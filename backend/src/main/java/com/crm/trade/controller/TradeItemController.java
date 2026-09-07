package com.crm.trade.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.trade.dto.ItemSaveRequest;
import com.crm.trade.dto.PriceChangeRequest;
import com.crm.trade.entity.TradeItem;
import com.crm.trade.entity.TradeItemPriceLog;
import com.crm.trade.entity.TradeRule;
import com.crm.trade.service.TradeItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易标的接口（TB-1 档案 / TB-2 调价留痕 / TB-3 规则配置）
 * 权限：801 trade:item:list 全员；802 trade:item:manage 仅 ADMIN
 */
@Tag(name = "交易标的 TRD-TB")
@RestController
@RequestMapping("/api/trade/items")
@RequiredArgsConstructor
public class TradeItemController {

    private final TradeItemService tradeItemService;

    @Operation(summary = "标的列表（keyword/category/status 筛选，供订单下拉）", description = "需求 TB-1")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('trade:item:list')")
    public Result<List<TradeItem>> list(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String status) {
        return Result.ok(tradeItemService.page(keyword, category, status));
    }

    @Operation(summary = "标的建档（默认 DRAFT 草稿）", description = "需求 TB-1")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('trade:item:manage')")
    @AuditLog(action = "trade:item:create", targetType = "TRADE_ITEM")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody ItemSaveRequest req) {
        return Result.ok(tradeItemService.create(uid, req));
    }

    @Operation(summary = "标的编辑（不含价格）", description = "需求 TB-1")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('trade:item:manage')")
    @AuditLog(action = "trade:item:update", targetType = "TRADE_ITEM", targetId = "#id")
    public Result<Void> update(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody ItemSaveRequest req) {
        tradeItemService.update(uid, id, req);
        return Result.ok();
    }

    @Operation(summary = "调价（旧价→新价留痕）", description = "需求 TB-2")
    @PostMapping("/{id}/price")
    @PreAuthorize("@ss.hasPerm('trade:item:manage')")
    @AuditLog(action = "trade:item:price", targetType = "TRADE_ITEM", targetId = "#id")
    public Result<Void> changePrice(@CurrentUser Long uid, @PathVariable Long id,
                                    @Valid @RequestBody PriceChangeRequest req) {
        tradeItemService.changePrice(uid, id, req);
        return Result.ok();
    }

    @Operation(summary = "调价留痕列表", description = "需求 TB-2")
    @GetMapping("/{id}/price-logs")
    @PreAuthorize("@ss.hasPerm('trade:item:list')")
    public Result<List<TradeItemPriceLog>> priceLogs(@PathVariable Long id) {
        return Result.ok(tradeItemService.priceLogs(id));
    }

    @Operation(summary = "标的上架（DRAFT/DELISTED → LISTED）", description = "需求 TB-1")
    @PostMapping("/{id}/list")
    @PreAuthorize("@ss.hasPerm('trade:item:manage')")
    @AuditLog(action = "trade:item:list-up", targetType = "TRADE_ITEM", targetId = "#id")
    public Result<Void> listItem(@CurrentUser Long uid, @PathVariable Long id) {
        tradeItemService.listItem(uid, id);
        return Result.ok();
    }

    @Operation(summary = "标的上架（LISTED → DELISTED）", description = "需求 TB-1")
    @PostMapping("/{id}/delist")
    @PreAuthorize("@ss.hasPerm('trade:item:manage')")
    @AuditLog(action = "trade:item:delist", targetType = "TRADE_ITEM", targetId = "#id")
    public Result<Void> delistItem(@CurrentUser Long uid, @PathVariable Long id) {
        tradeItemService.delistItem(uid, id);
        return Result.ok();
    }

    @Operation(summary = "交易规则列表（审批阈值/单笔限额）", description = "需求 TB-3")
    @GetMapping("/rules")
    @PreAuthorize("@ss.hasPerm('trade:item:list')")
    public Result<List<TradeRule>> rules() {
        return Result.ok(tradeItemService.rules());
    }

    @Operation(summary = "规则更新（仅 ADMIN）", description = "需求 TB-3")
    @PutMapping("/rules/{id}")
    @PreAuthorize("@ss.hasPerm('trade:item:manage')")
    @AuditLog(action = "trade:rule:update", targetType = "TRADE_RULE", targetId = "#id")
    public Result<Void> updateRule(@CurrentUser Long uid, @PathVariable Long id,
                                   @RequestBody RuleValueRequest req) {
        tradeItemService.updateRule(uid, id, req.getValue());
        return Result.ok();
    }

    /** 规则更新请求体 */
    @lombok.Data
    static class RuleValueRequest {
        @jakarta.validation.constraints.NotNull(message = "规则值不能为空")
        private BigDecimal value;
    }
}
