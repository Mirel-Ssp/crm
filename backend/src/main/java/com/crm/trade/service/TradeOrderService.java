package com.crm.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.service.CustomerService;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import com.crm.trade.dto.OrderCreateRequest;
import com.crm.trade.dto.OrderQuery;
import com.crm.trade.entity.TradeItem;
import com.crm.trade.entity.TradeOrder;
import com.crm.trade.entity.TradeOrderLog;
import com.crm.trade.entity.TradeOrderSettlement;
import com.crm.trade.entity.TradeRemittance;
import com.crm.trade.mapper.TradeItemMapper;
import com.crm.trade.mapper.TradeOrderLogMapper;
import com.crm.trade.mapper.TradeOrderMapper;
import com.crm.trade.mapper.TradeOrderSettlementMapper;
import com.crm.trade.mapper.TradeRemittanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 交易订单服务（OD-1~5）
 * - OD-1 创建：服务端计价（NUMERIC(18,4) HALF_UP），超单笔限额拒绝，
 *   总额超 APPROVAL_THRESHOLD 进人工审批，否则自动确认（均留痕）
 * - OD-4/5 状态机流转 + trade_order_log 留痕 + 乐观锁防并发
 * - OD-2/3 列表/详情按 ScopeService 三端过滤
 */
@Service
@RequiredArgsConstructor
public class TradeOrderService {

    private static final int MONEY_SCALE = 4;
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TradeOrderMapper orderMapper;
    private final TradeOrderLogMapper orderLogMapper;
    private final TradeOrderSettlementMapper settlementMapper;
    private final TradeRemittanceMapper remittanceMapper;
    private final TradeItemMapper itemMapper;
    private final TradeItemService tradeItemService;
    private final CustomerService customerService;
    private final CrmCustomerMapper customerMapper;
    private final SysUserMapper sysUserMapper;
    private final ScopeService scopeService;
    private final NotifyService notifyService;

    // ---------------- 计价纯函数（供单测） ----------------

    /** 金额 = round(数量 × 单价, 4)，与 V9 CHECK 对齐 */
    public static BigDecimal calcAmount(BigDecimal quantity, BigDecimal price) {
        return quantity.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 手续费 = round(金额 × 费率, 4) */
    public static BigDecimal calcFee(BigDecimal amount, BigDecimal feeRate) {
        return amount.multiply(feeRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 阈值分流：总额 > 阈值 → 人工审批（PENDING_CONFIRM），否则自动确认 */
    public static boolean needApproval(BigDecimal totalAmount, BigDecimal threshold) {
        return threshold != null && totalAmount.compareTo(threshold) > 0;
    }

    // ---------------- OD-2/3 查询 ----------------

    /** 分页列表（三端数据范围 + 名称回填） */
    public PageResult<Map<String, Object>> page(Long uid, OrderQuery query) {
        Page<TradeOrder> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(),
                Math.min(query.getPageSize() == null ? 20 : query.getPageSize(), 200));

        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<TradeOrder>()
                .like(StringUtils.hasText(query.getKeyword()), TradeOrder::getOrderNo, query.getKeyword())
                .eq(StringUtils.hasText(query.getStatus()), TradeOrder::getStatus, query.getStatus())
                .eq(query.getCustomerId() != null, TradeOrder::getCustomerId, query.getCustomerId())
                .eq(query.getItemId() != null, TradeOrder::getItemId, query.getItemId())
                .eq(StringUtils.hasText(query.getDirection()), TradeOrder::getDirection, query.getDirection())
                .orderByDesc(TradeOrder::getCreatedAt);
        applyScope(wrapper, uid);

        List<TradeOrder> rows = orderMapper.selectPage(page, wrapper).getRecords();

        Map<Long, String> customerNames = resolveNames(rows.stream()
                .map(TradeOrder::getCustomerId).distinct().toList(), id -> {
            CrmCustomer c = customerMapper.selectById(id);
            return c == null ? "-" : c.getName();
        });
        Map<Long, String> itemNames = resolveNames(rows.stream()
                .map(TradeOrder::getItemId).distinct().toList(), id -> {
            TradeItem i = itemMapper.selectById(id);
            return i == null ? "-" : i.getName();
        });
        Map<Long, String> ownerNames = resolveNames(rows.stream()
                .map(TradeOrder::getOwnerId).distinct().toList(), this::userName);

        List<Map<String, Object>> list = new ArrayList<>();
        for (TradeOrder o : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", o.getId());
            row.put("orderNo", o.getOrderNo());
            row.put("itemId", o.getItemId());
            row.put("itemName", itemNames.get(o.getItemId()));
            row.put("customerId", o.getCustomerId());
            row.put("customerName", customerNames.get(o.getCustomerId()));
            row.put("ownerId", o.getOwnerId());
            row.put("ownerName", ownerNames.get(o.getOwnerId()));
            row.put("direction", o.getDirection());
            row.put("quantity", o.getQuantity());
            row.put("price", o.getPrice());
            row.put("dealQuantity", o.getDealQuantity());
            row.put("amount", o.getAmount());
            row.put("feeRate", o.getFeeRate());
            row.put("feeAmount", o.getFeeAmount());
            row.put("totalAmount", o.getTotalAmount());
            row.put("status", o.getStatus());
            row.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString());
            list.add(row);
        }
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 详情（含状态时间轴 + 关联核销明细） */
    public Map<String, Object> detail(Long uid, Long id) {
        TradeOrder o = requireVisible(uid, id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", o.getId());
        data.put("orderNo", o.getOrderNo());
        data.put("itemId", o.getItemId());
        TradeItem item = itemMapper.selectById(o.getItemId());
        data.put("itemName", item == null ? "-" : item.getName());
        data.put("customerId", o.getCustomerId());
        CrmCustomer c = customerMapper.selectById(o.getCustomerId());
        data.put("customerName", c == null ? "-" : c.getName());
        data.put("ownerId", o.getOwnerId());
        data.put("ownerName", userName(o.getOwnerId()));
        data.put("direction", o.getDirection());
        data.put("quantity", o.getQuantity());
        data.put("price", o.getPrice());
        data.put("dealQuantity", o.getDealQuantity());
        data.put("amount", o.getAmount());
        data.put("feeRate", o.getFeeRate());
        data.put("feeAmount", o.getFeeAmount());
        data.put("totalAmount", o.getTotalAmount());
        data.put("status", o.getStatus());
        data.put("paidAmount", paidAmount(id));
        data.put("confirmedAt", o.getConfirmedAt() == null ? "" : o.getConfirmedAt().toString());
        data.put("cancelledAt", o.getCancelledAt() == null ? "" : o.getCancelledAt().toString());
        data.put("cancelReason", o.getCancelReason());
        data.put("remark", o.getRemark());
        data.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString());

        data.put("logs", orderLogMapper.selectList(new LambdaQueryWrapper<TradeOrderLog>()
                .eq(TradeOrderLog::getOrderId, id)
                .orderByAsc(TradeOrderLog::getCreatedAt)));

        List<Map<String, Object>> settlements = new ArrayList<>();
        for (TradeOrderSettlement s : settlementMapper.selectList(new LambdaQueryWrapper<TradeOrderSettlement>()
                .eq(TradeOrderSettlement::getOrderId, id)
                .orderByDesc(TradeOrderSettlement::getCreatedAt))) {
            Map<String, Object> row = new LinkedHashMap<>();
            TradeRemittance r = remittanceMapper.selectById(s.getRemittanceId());
            row.put("id", s.getId());
            row.put("remittanceId", s.getRemittanceId());
            row.put("remitNo", r == null ? "-" : r.getRemitNo());
            row.put("amount", s.getAmount());
            row.put("operatorName", userName(s.getOperatorId()));
            row.put("createdAt", s.getCreatedAt() == null ? "" : s.getCreatedAt().toString());
            settlements.add(row);
        }
        data.put("settlements", settlements);
        return data;
    }

    // ---------------- OD-1 创建 ----------------

    @Transactional
    public Long create(Long uid, OrderCreateRequest req) {
        customerService.requireVisible(uid, req.getCustomerId());
        TradeItem item = tradeItemService.requireListed(req.getItemId());
        if (!"BUY".equals(req.getDirection()) && !"SELL".equals(req.getDirection())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "交易方向仅支持 BUY/SELL");
        }
        if (item.getMinQuantity() != null && req.getQuantity().compareTo(item.getMinQuantity()) < 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "数量低于标的最小交易量");
        }

        BigDecimal price = req.getPrice() != null ? req.getPrice() : item.getReferencePrice();
        BigDecimal amount = calcAmount(req.getQuantity(), price);
        BigDecimal feeRate = item.getFeeRate() == null ? BigDecimal.ZERO : item.getFeeRate();
        BigDecimal feeAmount = calcFee(amount, feeRate);
        BigDecimal totalAmount = amount.add(feeAmount);

        BigDecimal maxAmount = tradeItemService.ruleValue("MAX_ORDER_AMOUNT");
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            throw new BizException(ResultCode.ORDER_AMOUNT_OVER_LIMIT);
        }

        BigDecimal threshold = tradeItemService.ruleValue("APPROVAL_THRESHOLD");
        boolean manualApprove = needApproval(totalAmount, threshold);

        TradeOrder o = new TradeOrder();
        o.setOrderNo("TO" + LocalDateTime.now().format(ORDER_NO_FMT)
                + String.format("%03d", RANDOM.nextInt(1000)));
        o.setItemId(req.getItemId());
        o.setCustomerId(req.getCustomerId());
        o.setOwnerId(uid);
        o.setDirection(req.getDirection());
        o.setQuantity(req.getQuantity());
        o.setPrice(price);
        o.setDealQuantity(BigDecimal.ZERO);
        o.setAmount(amount);
        o.setFeeRate(feeRate);
        o.setFeeAmount(feeAmount);
        o.setTotalAmount(totalAmount);
        o.setStatus(manualApprove ? OrderStateMachine.PENDING_CONFIRM : OrderStateMachine.CONFIRMED);
        if (OrderStateMachine.CONFIRMED.equals(o.getStatus())) {
            o.setConfirmedAt(LocalDateTime.now());
        }
        orderMapper.insert(o);
        orderLogMapper.insert(log(o.getId(), null, o.getStatus(), uid,
                manualApprove ? "创建订单，等待审批" : "创建订单，总额低于审批阈值自动确认"));

        if (manualApprove) {
            notifyService.publishToManagers(uid, "新订单待审批",
                    "订单 " + o.getOrderNo() + " 总额 " + totalAmount + " 已提交审批", "ORDER", o.getId());
        }
        return o.getId();
    }

    // ---------------- OD-4 审批 ----------------

    @Transactional
    public void approve(Long uid, Long id, String reason) {
        TradeOrder o = requireVisible(uid, id);
        transit(o, OrderStateMachine.CONFIRMED, uid, StringUtils.hasText(reason) ? reason : "审批通过");
        o.setConfirmedAt(LocalDateTime.now());
        int rows = orderMapper.updateById(o);
        requireUpdated(rows);
        notifyService.publish(o.getOwnerId(), "ORDER_EVENT", "订单审批通过",
                "订单 " + o.getOrderNo() + " 已审批通过", "ORDER", o.getId());
    }

    @Transactional
    public void reject(Long uid, Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "驳回原因不能为空");
        }
        TradeOrder o = requireVisible(uid, id);
        transit(o, OrderStateMachine.CANCELLED, uid, "驳回：" + reason);
        o.setCancelledAt(LocalDateTime.now());
        o.setCancelReason(reason);
        int rows = orderMapper.updateById(o);
        requireUpdated(rows);
        notifyService.publish(o.getOwnerId(), "ORDER_EVENT", "订单被驳回",
                "订单 " + o.getOrderNo() + " 被驳回：" + reason, "ORDER", o.getId());
    }

    // ---------------- OD-5 取消 ----------------

    /**
     * 取消：业务员限本人 PENDING_CONFIRM；
     * 经理/管理员可取消数据范围内未完成订单（PENDING_CONFIRM/CONFIRMED/PARTIAL_DEALT）
     */
    @Transactional
    public void cancel(Long uid, Long id, String reason) {
        TradeOrder o = requireVisible(uid, id);
        boolean self = uid.equals(o.getOwnerId());
        if (self && !OrderStateMachine.PENDING_CONFIRM.equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATE_ILLEGAL.getCode(), "仅待审批订单可由本人取消");
        }
        String reasonText = StringUtils.hasText(reason) ? reason : (self ? "业务员撤销" : "经理撤销");
        transit(o, OrderStateMachine.CANCELLED, uid, reasonText);
        o.setCancelledAt(LocalDateTime.now());
        o.setCancelReason(reason);
        int rows = orderMapper.updateById(o);
        requireUpdated(rows);
        if (!self) {
            notifyService.publish(o.getOwnerId(), "ORDER_EVENT", "订单被取消",
                    "订单 " + o.getOrderNo() + " 被取消：" + reason, "ORDER", o.getId());
        }
    }

    // ---------------- REM 联动（P5 由 RemitService 在锁订单事务内调用） ----------------

    /** 累计已核销金额 */
    public BigDecimal paidAmount(Long orderId) {
        List<TradeOrderSettlement> list = settlementMapper.selectList(
                new LambdaQueryWrapper<TradeOrderSettlement>()
                        .eq(TradeOrderSettlement::getOrderId, orderId));
        return list.stream().map(TradeOrderSettlement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 核销原子推进：累计核销 ≥ 总额 → FULL_DEALT（dealQuantity=quantity），
     * 否则 → PARTIAL_DEALT（dealQuantity 按比例）；状态机校验 + 留痕。
     * 调用方须已 SELECT FOR UPDATE 锁定订单并持有乐观锁版本。
     */
    public void advanceOnSettlement(TradeOrder locked, BigDecimal paidAfter, Long operatorId) {
        String to = paidAfter.compareTo(locked.getTotalAmount()) >= 0
                ? OrderStateMachine.FULL_DEALT : OrderStateMachine.PARTIAL_DEALT;
        if (to.equals(locked.getStatus())) {
            return;
        }
        String from = locked.getStatus();
        OrderStateMachine.requireTransit(from, to);
        if (OrderStateMachine.FULL_DEALT.equals(to)) {
            locked.setDealQuantity(locked.getQuantity());
        } else {
            locked.setDealQuantity(locked.getQuantity().multiply(paidAfter)
                    .divide(locked.getTotalAmount(), MONEY_SCALE, RoundingMode.HALF_UP));
        }
        locked.setStatus(to);
        int rows = orderMapper.updateById(locked);
        requireUpdated(rows);
        orderLogMapper.insert(log(locked.getId(), from, to, operatorId,
                "核销推进，累计已核销 " + paidAfter));
    }

    // ---------------- internal ----------------

    private void transit(TradeOrder o, String to, Long uid, String reason) {
        String from = o.getStatus();
        OrderStateMachine.requireTransit(from, to);
        o.setStatus(to);
        orderLogMapper.insert(log(o.getId(), from, to, uid, reason));
    }

    private void requireUpdated(int rows) {
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT);
        }
    }

    private TradeOrder requireVisible(Long uid, Long id) {
        TradeOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (!scopeService.canSee(uid, o.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return o;
    }

    private TradeOrderLog log(Long orderId, String from, String to, Long operatorId, String reason) {
        TradeOrderLog l = new TradeOrderLog();
        l.setOrderId(orderId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setOperatorId(operatorId);
        l.setReason(reason == null ? "" : reason.substring(0, Math.min(reason.length(), 255)));
        return l;
    }

    private void applyScope(LambdaQueryWrapper<TradeOrder> wrapper, Long uid) {
        List<Long> ownerIds = scopeService.visibleOwnerIds(uid);
        if (ownerIds != null) {
            wrapper.in(ownerIds.isEmpty(), TradeOrder::getOwnerId, -1L)
                    .in(!ownerIds.isEmpty(), TradeOrder::getOwnerId, ownerIds);
        }
    }

    private String userName(Long id) {
        if (id == null) {
            return "-";
        }
        SysUser u = sysUserMapper.selectById(id);
        return u == null ? "-" : u.getRealName();
    }

    private Map<Long, String> resolveNames(List<Long> ids, Function<Long, String> loader) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Long id : ids) {
            map.put(id, loader.apply(id));
        }
        return map;
    }
}
