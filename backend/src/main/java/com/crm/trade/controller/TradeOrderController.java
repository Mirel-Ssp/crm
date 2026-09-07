package com.crm.trade.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.trade.dto.OrderActionRequest;
import com.crm.trade.dto.OrderCreateRequest;
import com.crm.trade.dto.OrderQuery;
import com.crm.trade.service.TradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 交易订单接口（OD-1~5）
 * 权限：803 trade:order:list 查询；804 trade:order:create 创建；
 * 201 order:confirm 审批/驳回；202 order:cancel 取消
 */
@Tag(name = "交易订单 ORD-OD")
@RestController
@RequestMapping("/api/trade/orders")
@RequiredArgsConstructor
public class TradeOrderController {

    private final TradeOrderService tradeOrderService;

    @Operation(summary = "订单分页列表（三端数据范围）", description = "需求 OD-2")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('trade:order:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, OrderQuery query) {
        return Result.ok(tradeOrderService.page(uid, query));
    }

    @Operation(summary = "订单详情（状态时间轴 + 核销明细）", description = "需求 OD-3")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('trade:order:list')")
    public Result<Map<String, Object>> detail(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(tradeOrderService.detail(uid, id));
    }

    @Operation(summary = "订单创建（服务端计价，超阈值进审批）", description = "需求 OD-1")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('trade:order:create')")
    @AuditLog(action = "trade:order:create", targetType = "TRADE_ORDER")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody OrderCreateRequest req) {
        return Result.ok(tradeOrderService.create(uid, req));
    }

    @Operation(summary = "审批通过（PENDING_CONFIRM → CONFIRMED）", description = "需求 OD-4")
    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPerm('order:confirm')")
    @AuditLog(action = "trade:order:approve", targetType = "TRADE_ORDER", targetId = "#id")
    public Result<Void> approve(@CurrentUser Long uid, @PathVariable Long id,
                                @RequestBody(required = false) OrderActionRequest req) {
        tradeOrderService.approve(uid, id, req == null ? null : req.getReason());
        return Result.ok();
    }

    @Operation(summary = "审批驳回（原因必填，→ CANCELLED）", description = "需求 OD-4")
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPerm('order:confirm')")
    @AuditLog(action = "trade:order:reject", targetType = "TRADE_ORDER", targetId = "#id")
    public Result<Void> reject(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody OrderActionRequest req) {
        tradeOrderService.reject(uid, id, req.getReason());
        return Result.ok();
    }

    @Operation(summary = "取消订单（业务员限待审批；经理限数据范围内未完成）", description = "需求 OD-5")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPerm('order:cancel')")
    @AuditLog(action = "trade:order:cancel", targetType = "TRADE_ORDER", targetId = "#id")
    public Result<Void> cancel(@CurrentUser Long uid, @PathVariable Long id,
                               @RequestBody(required = false) OrderActionRequest req) {
        tradeOrderService.cancel(uid, id, req == null ? null : req.getReason());
        return Result.ok();
    }
}
