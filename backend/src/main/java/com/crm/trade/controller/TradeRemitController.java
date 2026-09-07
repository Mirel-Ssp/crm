package com.crm.trade.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.trade.dto.RemitQuery;
import com.crm.trade.dto.RemitSaveRequest;
import com.crm.trade.dto.WriteOffRequest;
import com.crm.trade.service.TradeRemitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 汇款与核销接口（RM-1~4）
 * 权限：805 trade:remit:list 查询；806 trade:remit:create 登记；
 * 301 remit:confirm 到账确认/驳回；302 remit:writeoff 核销
 */
@Tag(name = "汇款核销 REM-RM")
@RestController
@RequestMapping("/api/trade/remit")
@RequiredArgsConstructor
public class TradeRemitController {

    private final TradeRemitService tradeRemitService;

    @Operation(summary = "汇款分页列表（三端数据范围）", description = "需求 RM-3")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('trade:remit:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, RemitQuery query) {
        return Result.ok(tradeRemitService.page(uid, query));
    }

    @Operation(summary = "汇款详情（含核销明细）", description = "需求 RM-3")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('trade:remit:list')")
    public Result<Map<String, Object>> detail(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(tradeRemitService.detail(uid, id));
    }

    @Operation(summary = "汇款登记（待经理到账确认）", description = "需求 RM-1")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('trade:remit:create')")
    @AuditLog(action = "trade:remit:register", targetType = "TRADE_REMIT")
    public Result<Long> register(@CurrentUser Long uid, @Valid @RequestBody RemitSaveRequest req) {
        return Result.ok(tradeRemitService.register(uid, req));
    }

    @Operation(summary = "到账确认", description = "需求 RM-2")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("@ss.hasPerm('remit:confirm')")
    @AuditLog(action = "trade:remit:confirm", targetType = "TRADE_REMIT", targetId = "#id")
    public Result<Void> confirm(@CurrentUser Long uid, @PathVariable Long id) {
        tradeRemitService.confirm(uid, id);
        return Result.ok();
    }

    @Operation(summary = "到账驳回（原因必填）", description = "需求 RM-2")
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPerm('remit:confirm')")
    @AuditLog(action = "trade:remit:reject", targetType = "TRADE_REMIT", targetId = "#id")
    public Result<Void> reject(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody RejectBody body) {
        tradeRemitService.reject(uid, id, body.getReason());
        return Result.ok();
    }

    @Operation(summary = "核销（多对多，原子推进，双向防超核）", description = "需求 RM-4")
    @PostMapping("/{id}/write-off")
    @PreAuthorize("@ss.hasPerm('remit:writeoff')")
    @AuditLog(action = "trade:remit:writeoff", targetType = "TRADE_REMIT", targetId = "#id")
    public Result<Void> writeOff(@CurrentUser Long uid, @PathVariable Long id,
                                 @Valid @RequestBody List<WriteOffRequest> items) {
        tradeRemitService.writeOff(uid, id, items);
        return Result.ok();
    }

    /** 驳回请求体 */
    @Data
    static class RejectBody {
        @NotBlank(message = "驳回原因不能为空")
        private String reason;
    }
}
