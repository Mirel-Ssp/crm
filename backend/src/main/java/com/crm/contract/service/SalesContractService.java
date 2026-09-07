package com.crm.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.contract.dto.ContractQuery;
import com.crm.contract.dto.ContractSaveRequest;
import com.crm.contract.entity.SalesContract;
import com.crm.contract.mapper.SalesContractMapper;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.service.CustomerService;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import com.crm.trade.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同服务（CRM-R3）
 * 签署状态机：UNSIGNED → SIGNED → EXECUTING → EXPIRED（调度器每日推进到期）
 *            UNSIGNED/SIGNED/EXECUTING → TERMINATED
 * 到期预警：end_date - 今天 <= reminder_days 且签署/履行中（列表 expiring 过滤 + 通知）
 */
@Service
@RequiredArgsConstructor
public class SalesContractService {

    public static final String UNSIGNED = "UNSIGNED";
    public static final String SIGNED = "SIGNED";
    public static final String EXECUTING = "EXECUTING";
    public static final String EXPIRED = "EXPIRED";
    public static final String TERMINATED = "TERMINATED";

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SalesContractMapper contractMapper;
    private final CrmCustomerMapper customerMapper;
    private final CustomerService customerService;
    private final SysUserMapper sysUserMapper;
    private final ScopeService scopeService;
    private final NotifyService notifyService;

    // ---------------- 查询 ----------------

    public PageResult<Map<String, Object>> page(Long uid, ContractQuery q) {
        Page<SalesContract> page = new Page<>(q.getPageNum() == null ? 1 : q.getPageNum(),
                Math.min(q.getPageSize() == null ? 10 : q.getPageSize(), 100));
        LambdaQueryWrapper<SalesContract> w = new LambdaQueryWrapper<SalesContract>()
                .eq(StringUtils.hasText(q.getSignStatus()), SalesContract::getSignStatus, q.getSignStatus())
                .eq(q.getCustomerId() != null, SalesContract::getCustomerId, q.getCustomerId())
                .and(StringUtils.hasText(q.getKeyword()), x -> x
                        .like(SalesContract::getContractNo, q.getKeyword())
                        .or().like(SalesContract::getTitle, q.getKeyword()))
                .orderByDesc(SalesContract::getCreatedAt);
        applyScope(w, uid);
        if (Boolean.TRUE.equals(q.getExpiring())) {
            w.in(SalesContract::getSignStatus, SIGNED, EXECUTING)
                    .isNotNull(SalesContract::getEndDate)
                    .le(SalesContract::getEndDate, LocalDate.now().plusDays(30));
        }
        Page<SalesContract> result = contractMapper.selectPage(page, w);

        List<Map<String, Object>> list = new ArrayList<>();
        for (SalesContract c : result.getRecords()) {
            list.add(toRow(c));
        }
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public Map<String, Object> detail(Long uid, Long id) {
        return toRow(requireVisible(uid, id));
    }

    /** 到期预警清单（SIGNED/EXECUTING 且 end_date <= 今天+reminder_days），按到期日升序 */
    public List<Map<String, Object>> expiring(Long uid) {
        LambdaQueryWrapper<SalesContract> w = new LambdaQueryWrapper<SalesContract>()
                .in(SalesContract::getSignStatus, SIGNED, EXECUTING)
                .isNotNull(SalesContract::getEndDate)
                .le(SalesContract::getEndDate, LocalDate.now().plusDays(30))
                .orderByAsc(SalesContract::getEndDate);
        applyScope(w, uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SalesContract c : contractMapper.selectList(w)) {
            Map<String, Object> row = toRow(c);
            row.put("daysLeft", c.getEndDate() == null ? null
                    : ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate()));
            out.add(row);
        }
        return out;
    }

    // ---------------- 创建/编辑 ----------------

    @Transactional
    public Long create(Long uid, ContractSaveRequest req) {
        customerService.requireVisible(uid, req.getCustomerId());
        validateDates(req);

        SalesContract c = new SalesContract();
        c.setContractNo("C" + LocalDateTime.now().format(NO_FMT)
                + String.format("%03d", RANDOM.nextInt(1000)));
        c.setTitle(req.getTitle());
        c.setCustomerId(req.getCustomerId());
        c.setOpportunityId(req.getOpportunityId());
        c.setOrderId(req.getOrderId());
        c.setQuoteId(req.getQuoteId());
        c.setOwnerId(uid);
        c.setAmount(req.getAmount());
        c.setSignStatus(UNSIGNED);
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setReminderDays(req.getReminderDays() == null ? 30 : req.getReminderDays());
        c.setAttachmentUrl(req.getAttachmentUrl());
        c.setRemark(req.getRemark());
        contractMapper.insert(c);
        return c.getId();
    }

    /** 仅 UNSIGNED 可编辑 */
    @Transactional
    public void update(Long uid, Long id, ContractSaveRequest req) {
        SalesContract c = requireVisible(uid, id);
        if (!UNSIGNED.equals(c.getSignStatus())) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "仅未签署合同可编辑");
        }
        customerService.requireVisible(uid, req.getCustomerId());
        validateDates(req);

        c.setTitle(req.getTitle());
        c.setCustomerId(req.getCustomerId());
        c.setOpportunityId(req.getOpportunityId());
        c.setOrderId(req.getOrderId());
        c.setQuoteId(req.getQuoteId());
        c.setAmount(req.getAmount());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setReminderDays(req.getReminderDays() == null ? 30 : req.getReminderDays());
        c.setAttachmentUrl(req.getAttachmentUrl());
        c.setRemark(req.getRemark());
        contractMapper.updateById(c);
    }

    // ---------------- 签署流转 ----------------

    /** 签署完成：UNSIGNED → SIGNED（记录签署时间） */
    @Transactional
    public void sign(Long uid, Long id) {
        SalesContract c = requireVisible(uid, id);
        requireStatus(c, UNSIGNED);
        c.setSignStatus(SIGNED);
        c.setSignedAt(LocalDateTime.now());
        requireUpdated(contractMapper.updateById(c));
    }

    /** 开始履行：SIGNED → EXECUTING */
    @Transactional
    public void execute(Long uid, Long id) {
        SalesContract c = requireVisible(uid, id);
        requireStatus(c, SIGNED);
        c.setSignStatus(EXECUTING);
        requireUpdated(contractMapper.updateById(c));
    }

    /** 提前终止：UNSIGNED/SIGNED/EXECUTING → TERMINATED */
    @Transactional
    public void terminate(Long uid, Long id, String reason) {
        SalesContract c = requireVisible(uid, id);
        requireStatus(c, UNSIGNED, SIGNED, EXECUTING);
        c.setSignStatus(TERMINATED);
        c.setTerminatedAt(LocalDateTime.now());
        c.setTerminateReason(reason);
        requireUpdated(contractMapper.updateById(c));
    }

    /**
     * 到期推进（每日 03:00）：end_date < 今天 且 SIGNED/EXECUTING → EXPIRED，并通知归属人
     * 与 VA/STAT 调度错峰（02:00 / 02:30）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void expireScheduled() {
        List<SalesContract> due = contractMapper.selectList(new LambdaQueryWrapper<SalesContract>()
                .in(SalesContract::getSignStatus, SIGNED, EXECUTING)
                .isNotNull(SalesContract::getEndDate)
                .lt(SalesContract::getEndDate, LocalDate.now()));
        for (SalesContract c : due) {
            c.setSignStatus(EXPIRED);
            contractMapper.updateById(c);
            notifyService.publish(c.getOwnerId(), "ORDER_EVENT", "合同已到期",
                    "合同 " + c.getContractNo() + "（" + c.getTitle() + "）已过有效期，请安排续约",
                    "CONTRACT", c.getId());
        }
    }

    // ---------------- internal ----------------

    private void validateDates(ContractSaveRequest req) {
        if (req.getStartDate() != null && req.getEndDate() != null
                && req.getEndDate().isBefore(req.getStartDate())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "结束日期不能早于开始日期");
        }
    }

    private SalesContract requireVisible(Long uid, Long id) {
        SalesContract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "合同不存在");
        }
        if (!scopeService.canSee(uid, c.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return c;
    }

    private void requireStatus(SalesContract c, String... allowed) {
        for (String s : allowed) {
            if (s.equals(c.getSignStatus())) {
                return;
            }
        }
        throw new BizException(ResultCode.CONFLICT.getCode(), "当前签署状态不允许该操作（" + c.getSignStatus() + "）");
    }

    private void requireUpdated(int rows) {
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT);
        }
    }

    private Map<String, Object> toRow(SalesContract c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("contractNo", c.getContractNo());
        row.put("title", c.getTitle());
        row.put("customerId", c.getCustomerId());
        CrmCustomer cust = customerMapper.selectById(c.getCustomerId());
        row.put("customerName", cust == null ? "-" : cust.getName());
        row.put("opportunityId", c.getOpportunityId());
        row.put("orderId", c.getOrderId());
        row.put("quoteId", c.getQuoteId());
        row.put("ownerId", c.getOwnerId());
        row.put("ownerName", userName(c.getOwnerId()));
        row.put("amount", c.getAmount());
        row.put("signStatus", c.getSignStatus());
        row.put("startDate", c.getStartDate() == null ? "" : c.getStartDate().toString());
        row.put("endDate", c.getEndDate() == null ? "" : c.getEndDate().toString());
        row.put("reminderDays", c.getReminderDays());
        row.put("attachmentUrl", c.getAttachmentUrl());
        row.put("signedAt", c.getSignedAt() == null ? "" : c.getSignedAt().toString());
        row.put("terminatedAt", c.getTerminatedAt() == null ? "" : c.getTerminatedAt().toString());
        row.put("terminateReason", c.getTerminateReason());
        row.put("remark", c.getRemark());
        row.put("createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());
        if (c.getEndDate() != null && (SIGNED.equals(c.getSignStatus()) || EXECUTING.equals(c.getSignStatus()))) {
            row.put("daysLeft", ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate()));
        }
        return row;
    }

    private void applyScope(LambdaQueryWrapper<SalesContract> wrapper, Long uid) {
        List<Long> ownerIds = scopeService.visibleOwnerIds(uid);
        if (ownerIds != null) {
            wrapper.in(ownerIds.isEmpty(), SalesContract::getOwnerId, -1L)
                    .in(!ownerIds.isEmpty(), SalesContract::getOwnerId, ownerIds);
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
