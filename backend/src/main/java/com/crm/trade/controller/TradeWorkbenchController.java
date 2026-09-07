package com.crm.trade.controller;

import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.trade.service.TradeWorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 交易工作台接口（WSP-DV-01/02 服务端）
 * 权限：808 trade:workbench
 */
@Tag(name = "交易工作台 WSP")
@RestController
@RequestMapping("/api/trade/workbench")
@RequiredArgsConstructor
public class TradeWorkbenchController {

    private final TradeWorkbenchService tradeWorkbenchService;

    @Operation(summary = "工作台聚合（业绩卡片/状态分布/待审批/待确认/动态流）", description = "需求 WSP-DV-01")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('trade:workbench')")
    public Result<Map<String, Object>> workbench(@CurrentUser Long uid) {
        return Result.ok(tradeWorkbenchService.workbench(uid));
    }

    @Operation(summary = "客户业务面板（订单/汇款/业务时间轴）", description = "需求 WSP-DV-02")
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("@ss.hasPerm('trade:workbench')")
    public Result<Map<String, Object>> customerPanel(@CurrentUser Long uid, @PathVariable Long customerId) {
        return Result.ok(tradeWorkbenchService.customerPanel(uid, customerId));
    }
}
