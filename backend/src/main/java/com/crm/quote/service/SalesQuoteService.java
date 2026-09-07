package com.crm.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.service.CustomerService;
import com.crm.opportunity.entity.CrmOpportunity;
import com.crm.opportunity.mapper.CrmOpportunityMapper;
import com.crm.quote.dto.QuoteQuery;
import com.crm.quote.dto.QuoteSaveRequest;
import com.crm.quote.entity.SalesQuote;
import com.crm.quote.entity.SalesQuoteItem;
import com.crm.quote.mapper.SalesQuoteItemMapper;
import com.crm.quote.mapper.SalesQuoteMapper;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.entity.SysUser;
import com.crm.system.service.ScopeService;
import com.crm.trade.dto.OrderCreateRequest;
import com.crm.trade.service.NotifyService;
import com.crm.trade.service.TradeOrderService;
import com.crm.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 报价单服务（CRM-R1）
 * 状态机：DRAFT → SUBMITTED → APPROVED / REJECTED（驳回可改后重提）
 *        APPROVED → CONVERTED（按明细逐行转交易订单）
 *        DRAFT/SUBMITTED/REJECTED → VOID（作废）
 * 金额口径：total=Σ(明细)；discount=total×折扣率；tax=discount×税率；final=discount+tax
 * BPMN（CRM-F4）：submit 启动 quoteApproval 流程（候选组 MANAGER/ADMIN），
 *        approve/reject 完成网关决策回写；作废时取消活跃流程实例
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesQuoteService {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CONVERTED = "CONVERTED";
    public static final String VOID = "VOID";

    private static final int MONEY_SCALE = 4;
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SalesQuoteMapper quoteMapper;
    private final SalesQuoteItemMapper itemMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CustomerService customerService;
    private final SysUserMapper sysUserMapper;
    private final ScopeService scopeService;
    private final TradeOrderService tradeOrderService;
    private final NotifyService notifyService;
    private final WorkflowService workflowService;

    // ---------------- 查询 ----------------

    public PageResult<Map<String, Object>> page(Long uid, QuoteQuery q) {
        Page<SalesQuote> page = new Page<>(q.getPageNum() == null ? 1 : q.getPageNum(),
                Math.min(q.getPageSize() == null ? 10 : q.getPageSize(), 100));
        LambdaQueryWrapper<SalesQuote> w = new LambdaQueryWrapper<SalesQuote>()
                .eq(StringUtils.hasText(q.getStatus()), SalesQuote::getStatus, q.getStatus())
                .eq(q.getCustomerId() != null, SalesQuote::getCustomerId, q.getCustomerId())
                .eq(q.getOpportunityId() != null, SalesQuote::getOpportunityId, q.getOpportunityId())
                .and(StringUtils.hasText(q.getKeyword()), x -> x
                        .like(SalesQuote::getQuoteNo, q.getKeyword())
                        .or().like(SalesQuote::getTitle, q.getKeyword()))
                .orderByDesc(SalesQuote::getCreatedAt);
        applyScope(w, uid);
        Page<SalesQuote> result = quoteMapper.selectPage(page, w);

        List<Map<String, Object>> list = new ArrayList<>();
        for (SalesQuote s : result.getRecords()) {
            list.add(toRow(s, false));
        }
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public Map<String, Object> detail(Long uid, Long id) {
        return toRow(requireVisible(uid, id), true);
    }

    // ---------------- 创建/编辑 ----------------

    @Transactional
    public Long create(Long uid, QuoteSaveRequest req) {
        customerService.requireVisible(uid, req.getCustomerId());
        if (req.getOpportunityId() != null) {
            requireOpportunity(uid, req.getOpportunityId());
        }
        Amounts a = compute(req);

        SalesQuote q = new SalesQuote();
        q.setQuoteNo("Q" + LocalDateTime.now().format(NO_FMT)
                + String.format("%03d", RANDOM.nextInt(1000)));
        q.setTitle(req.getTitle());
        q.setOpportunityId(req.getOpportunityId());
        q.setCustomerId(req.getCustomerId());
        q.setOwnerId(uid);
        q.setStatus(DRAFT);
        q.setDiscountRate(a.discountRate);
        q.setTaxRate(a.taxRate);
        q.setTotalAmount(a.total);
        q.setDiscountAmount(a.discount);
        q.setTaxAmount(a.tax);
        q.setFinalAmount(a.finalAmount);
        q.setValidUntil(req.getValidUntil());
        q.setRemark(req.getRemark());
        quoteMapper.insert(q);
        insertItems(q.getId(), req.getItems());
        return q.getId();
    }

    /** 仅 DRAFT / REJECTED（驳回修改后）可编辑；编辑重算金额并全量替换明细 */
    @Transactional
    public void update(Long uid, Long id, QuoteSaveRequest req) {
        SalesQuote q = requireVisible(uid, id);
        if (!DRAFT.equals(q.getStatus()) && !REJECTED.equals(q.getStatus())) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "仅草稿或已驳回报价单可编辑");
        }
        customerService.requireVisible(uid, req.getCustomerId());
        if (req.getOpportunityId() != null) {
            requireOpportunity(uid, req.getOpportunityId());
        }
        Amounts a = compute(req);

        q.setTitle(req.getTitle());
        q.setOpportunityId(req.getOpportunityId());
        q.setCustomerId(req.getCustomerId());
        q.setDiscountRate(a.discountRate);
        q.setTaxRate(a.taxRate);
        q.setTotalAmount(a.total);
        q.setDiscountAmount(a.discount);
        q.setTaxAmount(a.tax);
        q.setFinalAmount(a.finalAmount);
        q.setValidUntil(req.getValidUntil());
        q.setRemark(req.getRemark());
        quoteMapper.updateById(q);
        itemMapper.delete(new LambdaQueryWrapper<SalesQuoteItem>().eq(SalesQuoteItem::getQuoteId, id));
        insertItems(id, req.getItems());
    }

    // ---------------- 状态流转 ----------------

    /** 提交审批：DRAFT/REJECTED → SUBMITTED，启动 BPMN 流程并通知审批人 */
    @Transactional
    public void submit(Long uid, Long id) {
        SalesQuote q = requireVisible(uid, id);
        requireStatus(q, DRAFT, REJECTED);
        q.setStatus(SUBMITTED);
        requireUpdated(quoteMapper.updateById(q));
        workflowService.startProcess("quoteApproval", businessKey(q.getId()), Map.of(
                "quoteId", q.getId(),
                "quoteNo", q.getQuoteNo(),
                "title", q.getTitle(),
                "submittedBy", uid));
        notifyService.publishToManagers(uid, "新报价单待审批",
                "报价单 " + q.getQuoteNo() + "（" + q.getTitle() + "）已提交审批", "QUOTE", q.getId());
    }

    /** 审批通过：SUBMITTED → APPROVED（同步完成 BPMN 审批任务） */
    @Transactional
    public void approve(Long uid, Long id, String reason) {
        SalesQuote q = requireVisible(uid, id);
        requireStatus(q, SUBMITTED);
        completeWfTask(q, true, uid, reason);
        q.setStatus(APPROVED);
        q.setApprovedAt(LocalDateTime.now());
        q.setRejectedReason(null);
        requireUpdated(quoteMapper.updateById(q));
        notifyService.publish(q.getOwnerId(), "ORDER_EVENT", "报价单审批通过",
                "报价单 " + q.getQuoteNo() + " 已审批通过"
                        + (StringUtils.hasText(reason) ? "：" + reason : ""), "QUOTE", q.getId());
    }

    /** 审批驳回：SUBMITTED → REJECTED（修改后可重新提交） */
    @Transactional
    public void reject(Long uid, Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "驳回原因不能为空");
        }
        SalesQuote q = requireVisible(uid, id);
        requireStatus(q, SUBMITTED);
        completeWfTask(q, false, uid, reason);
        q.setStatus(REJECTED);
        q.setRejectedReason(reason);
        requireUpdated(quoteMapper.updateById(q));
        notifyService.publish(q.getOwnerId(), "ORDER_EVENT", "报价单被驳回",
                "报价单 " + q.getQuoteNo() + " 被驳回：" + reason, "QUOTE", q.getId());
    }

    /** 作废：DRAFT/SUBMITTED/REJECTED → VOID（取消 BPMN 活跃实例避免僵尸待办） */
    @Transactional
    public void voidQuote(Long uid, Long id, String reason) {
        SalesQuote q = requireVisible(uid, id);
        requireStatus(q, DRAFT, SUBMITTED, REJECTED);
        if (SUBMITTED.equals(q.getStatus())) {
            workflowService.cancelProcess(businessKey(q.getId()), "报价单作废");
        }
        q.setStatus(VOID);
        q.setRemark(StringUtils.hasText(reason) ? "作废：" + reason : "作废");
        requireUpdated(quoteMapper.updateById(q));
    }

    /**
     * 转订单：APPROVED → CONVERTED
     * 按明细逐行生成交易订单（仅含交易标的的行；全部为自由行时提示先关联标的）
     */
    @Transactional
    public List<Long> convert(Long uid, Long id, String direction) {
        SalesQuote q = requireVisible(uid, id);
        requireStatus(q, APPROVED);
        if (!"BUY".equals(direction) && !"SELL".equals(direction)) {
            direction = "BUY";
        }
        List<SalesQuoteItem> items = itemMapper.selectList(new LambdaQueryWrapper<SalesQuoteItem>()
                .eq(SalesQuoteItem::getQuoteId, id).orderByAsc(SalesQuoteItem::getSort));
        List<Long> orderIds = new ArrayList<>();
        for (SalesQuoteItem it : items) {
            if (it.getItemId() == null) {
                continue;
            }
            OrderCreateRequest req = new OrderCreateRequest();
            req.setCustomerId(q.getCustomerId());
            req.setItemId(it.getItemId());
            req.setDirection(direction);
            req.setQuantity(it.getQuantity());
            req.setPrice(it.getPrice());
            orderIds.add(tradeOrderService.create(uid, req));
        }
        if (orderIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "报价明细均未关联交易标的，无法转订单（请先在明细中选择标的）");
        }
        q.setStatus(CONVERTED);
        quoteMapper.updateById(q);
        return orderIds;
    }

    // ---------------- 金额计算（纯函数） ----------------

    record Amounts(BigDecimal discountRate, BigDecimal taxRate,
                   BigDecimal total, BigDecimal discount, BigDecimal tax, BigDecimal finalAmount) {
    }

    private Amounts compute(QuoteSaveRequest req) {
        BigDecimal discountRate = req.getDiscountRate() == null ? BigDecimal.ONE : req.getDiscountRate();
        BigDecimal taxRate = req.getTaxRate() == null ? BigDecimal.ZERO : req.getTaxRate();
        BigDecimal total = BigDecimal.ZERO;
        for (QuoteSaveRequest.Item it : req.getItems()) {
            total = total.add(it.getQuantity().multiply(it.getPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        BigDecimal discount = total.multiply(discountRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = discount.multiply(taxRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Amounts(discountRate, taxRate, total, discount, tax, discount.add(tax));
    }

    private void insertItems(Long quoteId, List<QuoteSaveRequest.Item> items) {
        int sort = 1;
        for (QuoteSaveRequest.Item it : items) {
            SalesQuoteItem e = new SalesQuoteItem();
            e.setQuoteId(quoteId);
            e.setItemId(it.getItemId());
            e.setName(it.getName());
            e.setSpec(it.getSpec());
            e.setQuantity(it.getQuantity());
            e.setPrice(it.getPrice());
            e.setAmount(it.getQuantity().multiply(it.getPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            e.setSort(sort++);
            itemMapper.insert(e);
        }
    }

    // ---------------- internal ----------------

    private String businessKey(Long quoteId) {
        return "QUOTE:" + quoteId;
    }

    /** 完成 BPMN 审批任务（网关变量 approved）；引擎部署前的历史单据无活跃任务则跳过（兼容迁移） */
    private void completeWfTask(SalesQuote q, boolean approved, Long uid, String reason) {
        var task = workflowService.findActiveTask(businessKey(q.getId()));
        if (task == null) {
            log.info("quote {} has no active BPMN task (pre-engine data), skip wf complete", q.getId());
            return;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("approved", approved);
        vars.put("operator", uid);
        if (StringUtils.hasText(reason)) {
            vars.put(approved ? "approveReason" : "rejectReason", reason);
        }
        workflowService.completeTaskById(task.getId(), vars);
    }

    private SalesQuote requireVisible(Long uid, Long id) {
        SalesQuote q = quoteMapper.selectById(id);
        if (q == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "报价单不存在");
        }
        if (!scopeService.canSee(uid, q.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return q;
    }

    private void requireOpportunity(Long uid, Long oppId) {
        CrmOpportunity o = opportunityMapper.selectById(oppId);
        if (o == null || !scopeService.canSee(uid, o.getOwnerId())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商机不存在或无权访问");
        }
    }

    private void requireStatus(SalesQuote q, String... allowed) {
        for (String s : allowed) {
            if (s.equals(q.getStatus())) {
                return;
            }
        }
        throw new BizException(ResultCode.CONFLICT.getCode(), "当前状态不允许该操作（" + q.getStatus() + "）");
    }

    private void requireUpdated(int rows) {
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT);
        }
    }

    private Map<String, Object> toRow(SalesQuote q, boolean withItems) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", q.getId());
        row.put("quoteNo", q.getQuoteNo());
        row.put("title", q.getTitle());
        row.put("opportunityId", q.getOpportunityId());
        row.put("customerId", q.getCustomerId());
        CrmCustomer c = customerMapper.selectById(q.getCustomerId());
        row.put("customerName", c == null ? "-" : c.getName());
        row.put("ownerId", q.getOwnerId());
        row.put("ownerName", userName(q.getOwnerId()));
        row.put("status", q.getStatus());
        row.put("discountRate", q.getDiscountRate());
        row.put("taxRate", q.getTaxRate());
        row.put("totalAmount", q.getTotalAmount());
        row.put("discountAmount", q.getDiscountAmount());
        row.put("taxAmount", q.getTaxAmount());
        row.put("finalAmount", q.getFinalAmount());
        row.put("validUntil", q.getValidUntil() == null ? "" : q.getValidUntil().toString());
        row.put("approvedAt", q.getApprovedAt() == null ? "" : q.getApprovedAt().toString());
        row.put("rejectedReason", q.getRejectedReason());
        row.put("remark", q.getRemark());
        row.put("createdAt", q.getCreatedAt() == null ? "" : q.getCreatedAt().toString());
        if (withItems) {
            row.put("items", itemMapper.selectList(new LambdaQueryWrapper<SalesQuoteItem>()
                    .eq(SalesQuoteItem::getQuoteId, q.getId()).orderByAsc(SalesQuoteItem::getSort)));
        }
        return row;
    }

    private void applyScope(LambdaQueryWrapper<SalesQuote> wrapper, Long uid) {
        List<Long> ownerIds = scopeService.visibleOwnerIds(uid);
        if (ownerIds != null) {
            wrapper.in(ownerIds.isEmpty(), SalesQuote::getOwnerId, -1L)
                    .in(!ownerIds.isEmpty(), SalesQuote::getOwnerId, ownerIds);
        }
    }

    private String userName(Long id) {
        if (id == null) {
            return "-";
        }
        SysUser u = sysUserMapper.selectById(id);
        return u == null ? "-" : u.getRealName();
    }
}
