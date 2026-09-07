package com.crm.quote.controller;

import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.audit.AuditLog;
import com.crm.common.security.CurrentUser;
import com.crm.quote.dto.QuoteQuery;
import com.crm.quote.dto.QuoteSaveRequest;
import com.crm.quote.service.SalesQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报价单接口（CRM-R1）
 * 权限：1001 quote:list 查询；1002 quote:manage 创建/编辑/提交/作废/转订单；1003 quote:approve 审批
 */
@Tag(name = "报价单 QUOTE")
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class SalesQuoteController {

    private final SalesQuoteService quoteService;

    @Operation(summary = "报价单分页列表", description = "需求 CRM-R1")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('quote:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, QuoteQuery query) {
        return Result.ok(quoteService.page(uid, query));
    }

    @Operation(summary = "报价单详情（含明细行）", description = "需求 CRM-R1")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('quote:list')")
    public Result<Map<String, Object>> detail(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(quoteService.detail(uid, id));
    }

    @Operation(summary = "创建报价单（DRAFT，金额服务端计算）", description = "需求 CRM-R1")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('quote:manage')")
    @AuditLog(action = "quote:create", targetType = "QUOTE")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody QuoteSaveRequest req) {
        return Result.ok(quoteService.create(uid, req));
    }

    @Operation(summary = "编辑报价单（仅 DRAFT/REJECTED）", description = "需求 CRM-R1")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('quote:manage')")
    @AuditLog(action = "quote:update", targetType = "QUOTE", targetId = "#id")
    public Result<Void> update(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody QuoteSaveRequest req) {
        quoteService.update(uid, id, req);
        return Result.ok();
    }

    @Operation(summary = "提交审批（DRAFT/REJECTED→SUBMITTED）", description = "需求 CRM-R1")
    @PostMapping("/{id}/submit")
    @PreAuthorize("@ss.hasPerm('quote:manage')")
    @AuditLog(action = "quote:submit", targetType = "QUOTE", targetId = "#id")
    public Result<Void> submit(@CurrentUser Long uid, @PathVariable Long id) {
        quoteService.submit(uid, id);
        return Result.ok();
    }

    @Operation(summary = "审批通过（SUBMITTED→APPROVED）", description = "需求 CRM-R1")
    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPerm('quote:approve')")
    @AuditLog(action = "quote:approve", targetType = "QUOTE", targetId = "#id")
    public Result<Void> approve(@CurrentUser Long uid, @PathVariable Long id,
                                @RequestBody(required = false) Map<String, String> body) {
        quoteService.approve(uid, id, body == null ? null : body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "审批驳回（SUBMITTED→REJECTED，需填原因）", description = "需求 CRM-R1")
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPerm('quote:approve')")
    @AuditLog(action = "quote:reject", targetType = "QUOTE", targetId = "#id")
    public Result<Void> reject(@CurrentUser Long uid, @PathVariable Long id,
                               @RequestBody Map<String, String> body) {
        quoteService.reject(uid, id, body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "作废（DRAFT/SUBMITTED/REJECTED→VOID）", description = "需求 CRM-R1")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('quote:manage')")
    @AuditLog(action = "quote:void", targetType = "QUOTE", targetId = "#id")
    public Result<Void> voidQuote(@CurrentUser Long uid, @PathVariable Long id,
                                  @RequestParam(required = false) String reason) {
        quoteService.voidQuote(uid, id, reason);
        return Result.ok();
    }

    @Operation(summary = "转订单（APPROVED→CONVERTED，按明细逐行生成交易订单）", description = "需求 CRM-R1")
    @PostMapping("/{id}/convert")
    @PreAuthorize("@ss.hasPerm('quote:manage')")
    @AuditLog(action = "quote:convert", targetType = "QUOTE", targetId = "#id")
    public Result<List<Long>> convert(@CurrentUser Long uid, @PathVariable Long id,
                                      @RequestParam(required = false, defaultValue = "BUY") String direction) {
        return Result.ok(quoteService.convert(uid, id, direction));
    }
}
