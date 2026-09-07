package com.crm.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.service.CustomerService;
import com.crm.system.service.ScopeService;
import com.crm.trade.entity.TradeOrder;
import com.crm.trade.entity.TradeOrderLog;
import com.crm.trade.entity.TradeOrderSettlement;
import com.crm.trade.entity.TradeRemittance;
import com.crm.trade.mapper.TradeOrderLogMapper;
import com.crm.trade.mapper.TradeOrderMapper;
import com.crm.trade.mapper.TradeOrderSettlementMapper;
import com.crm.trade.mapper.TradeRemittanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 交易工作台聚合（WSP-DV-01/02 服务端）
 * - /api/trade/workbench：按数据范围（ALL/TEAM/SELF 对应管理/经理/业务员视图）
 *   返回业绩卡片 + 订单状态分布 + 待审批订单 + 待确认汇款 + 最新动态流，前端按角色渲染
 * - /api/trade/workbench/customer/{id}：客户业务面板（订单/汇款/业务时间轴）
 */
@Service
@RequiredArgsConstructor
public class TradeWorkbenchService {

    private final TradeOrderMapper orderMapper;
    private final TradeOrderLogMapper orderLogMapper;
    private final TradeRemittanceMapper remitMapper;
    private final TradeOrderSettlementMapper settlementMapper;
    private final CustomerService customerService;
    private final CrmCustomerMapper customerMapper;
    private final ScopeService scopeService;
    private final NotifyService notifyService;

    /** 工作台聚合（一套数据，前端按 scope/权限渲染三端视图） */
    public Map<String, Object> workbench(Long uid) {
        List<Long> ownerIds = scopeService.visibleOwnerIds(uid);
        String scope = scopeService.scopeOf(uid).name();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", scope);

        // ---- 业绩卡片 ----
        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("myOrderCount", orderMapper.selectCount(
                new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOwnerId, uid)));
        cards.put("myOrderAmount", sumAmount(w -> w.eq(TradeOrder::getOwnerId, uid)));
        cards.put("myMonthAmount", sumAmount(w -> w.eq(TradeOrder::getOwnerId, uid)
                .ge(TradeOrder::getCreatedAt, monthStart())));
        cards.put("scopeOrderAmount", sumAmount(w -> inScope(w, ownerIds)));
        cards.put("pendingApproveCount", orderMapper.selectCount(
                withScope(ownerIds).eq(TradeOrder::getStatus, OrderStateMachine.PENDING_CONFIRM)));
        cards.put("pendingRemitCount", countRemits(ownerIds, "PENDING_CONFIRM"));
        cards.put("unreadCount", notifyService.unreadCount(uid));
        data.put("cards", cards);

        // ---- 订单状态分布（范围内） ----
        LambdaQueryWrapper<TradeOrder> statusWrapper = withScope(ownerIds)
                .select(TradeOrder::getStatus);
        Map<String, Integer> statusStats = new LinkedHashMap<>();
        for (TradeOrder o : orderMapper.selectList(statusWrapper)) {
            statusStats.merge(o.getStatus(), 1, Integer::sum);
        }
        data.put("statusStats", statusStats);

        // ---- 待审批订单（范围内 top10，经理审批入口/业务员查看自己的单） ----
        data.put("pendingApprovals", orderMapper.selectList(withScope(ownerIds)
                .eq(TradeOrder::getStatus, OrderStateMachine.PENDING_CONFIRM)
                .orderByDesc(TradeOrder::getCreatedAt)
                .last("LIMIT 10")));

        // ---- 待确认汇款（范围内 top10） ----
        LambdaQueryWrapper<TradeRemittance> pendingRemit = new LambdaQueryWrapper<TradeRemittance>()
                .eq(TradeRemittance::getStatus, "PENDING_CONFIRM")
                .orderByDesc(TradeRemittance::getCreatedAt)
                .last("LIMIT 10");
        inRemitScope(pendingRemit, ownerIds);
        data.put("pendingRemits", remitMapper.selectList(pendingRemit));

        // ---- 最新动态流（top10） ----
        data.put("recentFeed", notifyService.feed(uid, 1, 10).getList());
        return data;
    }

    /** 客户业务面板：订单 + 汇款 + 业务时间轴（订单/汇款事件按时间倒序 top50） */
    public Map<String, Object> customerPanel(Long uid, Long customerId) {
        customerService.requireVisible(uid, customerId);
        CrmCustomer c = customerMapper.selectById(customerId);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "客户不存在");
        }

        List<TradeOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getCustomerId, customerId)
                .orderByDesc(TradeOrder::getCreatedAt));
        List<TradeRemittance> remits = remitMapper.selectList(new LambdaQueryWrapper<TradeRemittance>()
                .eq(TradeRemittance::getCustomerId, customerId)
                .orderByDesc(TradeRemittance::getCreatedAt));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerName", c.getName());
        data.put("orders", orders);
        data.put("remittances", remits);

        // 时间轴：订单状态流转 + 汇款登记/确认 + 核销明细
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (TradeOrder o : orders) {
            for (TradeOrderLog l : orderLogMapper.selectList(new LambdaQueryWrapper<TradeOrderLog>()
                    .eq(TradeOrderLog::getOrderId, o.getId()))) {
                timeline.add(event(l.getCreatedAt(), "ORDER_LOG",
                        "订单 " + o.getOrderNo() + "：" + l.getFromStatus() + " → " + l.getToStatus()
                                + (l.getReason() == null || l.getReason().isEmpty() ? "" : "（" + l.getReason() + "）"),
                        "ORDER", o.getId()));
            }
            for (TradeOrderSettlement s : settlementMapper.selectList(new LambdaQueryWrapper<TradeOrderSettlement>()
                    .eq(TradeOrderSettlement::getOrderId, o.getId()))) {
                timeline.add(event(s.getCreatedAt(), "WRITE_OFF",
                        "订单 " + o.getOrderNo() + " 核销 " + s.getAmount(), "REMIT", s.getRemittanceId()));
            }
        }
        for (TradeRemittance r : remits) {
            timeline.add(event(r.getCreatedAt(), "REMIT_REGISTER",
                    "汇款登记 " + r.getRemitNo() + " 金额 " + r.getAmount(), "REMIT", r.getId()));
            if (r.getConfirmedAt() != null) {
                timeline.add(event(r.getConfirmedAt(), "REMIT_CONFIRM",
                        "汇款到账确认 " + r.getRemitNo(), "REMIT", r.getId()));
            }
        }
        timeline.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("time")).reversed());
        data.put("timeline", timeline.size() > 50 ? new ArrayList<>(timeline.subList(0, 50)) : timeline);
        return data;
    }

    // ---------------- internal ----------------

    /** 订单范围过滤：ALL 不加条件，TEAM/SELF in(ownerIds)（空列表兜底 -1） */
    private LambdaQueryWrapper<TradeOrder> withScope(List<Long> ownerIds) {
        LambdaQueryWrapper<TradeOrder> w = new LambdaQueryWrapper<>();
        inScope(w, ownerIds);
        return w;
    }

    private void inScope(LambdaQueryWrapper<TradeOrder> w, List<Long> ownerIds) {
        if (ownerIds != null) {
            w.in(ownerIds.isEmpty(), TradeOrder::getOwnerId, -1L)
                    .in(!ownerIds.isEmpty(), TradeOrder::getOwnerId, ownerIds);
        }
    }

    private void inRemitScope(LambdaQueryWrapper<TradeRemittance> w, List<Long> ownerIds) {
        if (ownerIds != null) {
            w.in(ownerIds.isEmpty(), TradeRemittance::getOwnerId, -1L)
                    .in(!ownerIds.isEmpty(), TradeRemittance::getOwnerId, ownerIds);
        }
    }

    private long countRemits(List<Long> ownerIds, String status) {
        LambdaQueryWrapper<TradeRemittance> w = new LambdaQueryWrapper<TradeRemittance>()
                .eq(TradeRemittance::getStatus, status);
        inRemitScope(w, ownerIds);
        return remitMapper.selectCount(w);
    }

    private BigDecimal sumAmount(Consumer<LambdaQueryWrapper<TradeOrder>> extra) {
        LambdaQueryWrapper<TradeOrder> w = new LambdaQueryWrapper<TradeOrder>()
                .select(TradeOrder::getTotalAmount);
        extra.accept(w);
        BigDecimal sum = BigDecimal.ZERO;
        for (TradeOrder o : orderMapper.selectList(w)) {
            if (o.getTotalAmount() != null) {
                sum = sum.add(o.getTotalAmount());
            }
        }
        return sum;
    }

    private LocalDateTime monthStart() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    private Map<String, Object> event(LocalDateTime time, String type, String content, String relType, Long relId) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("time", time == null ? "" : time.toString());
        e.put("type", type);
        e.put("content", content);
        e.put("relType", relType);
        e.put("relId", relId);
        return e;
    }
}
