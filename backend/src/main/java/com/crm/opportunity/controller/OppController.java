package com.crm.opportunity.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.opportunity.dto.OppQuery;
import com.crm.opportunity.dto.OppSaveRequest;
import com.crm.opportunity.service.OppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
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
 * 商机接口（CRM-O1~O5；漏斗/丢单统计供 RPT 复用）
 */
@Tag(name = "商机管理 CRM-O")
@RestController
@RequestMapping("/api/opps")
@RequiredArgsConstructor
public class OppController {

    private final OppService oppService;

    @Operation(summary = "分页列表（漏斗下钻明细）", description = "需求 CRM-O3")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('opp:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, OppQuery query) {
        return Result.ok(oppService.page(uid, query));
    }

    @Operation(summary = "商机漏斗（数量/金额/赢率/加权预测）", description = "需求 CRM-O3/O5")
    @GetMapping("/funnel")
    @PreAuthorize("@ss.hasPerm('opp:list')")
    public Result<List<Map<String, Object>>> funnel(@CurrentUser Long uid,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        return Result.ok(oppService.funnel(uid, from, to));
    }

    @Operation(summary = "丢单原因统计", description = "需求 CRM-O4")
    @GetMapping("/loss-stats")
    @PreAuthorize("@ss.hasPerm('opp:list')")
    public Result<List<Map<String, Object>>> lossStats(@CurrentUser Long uid,
                                                       @RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to) {
        return Result.ok(oppService.lossStats(uid, from, to));
    }

    @Operation(summary = "商机新增", description = "需求 CRM-O1")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('opp:create')")
    @AuditLog(action = "opp:create", targetType = "OPPORTUNITY")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody OppSaveRequest req) {
        return Result.ok(oppService.create(uid, req));
    }

    @Operation(summary = "商机编辑", description = "需求 CRM-O1")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('opp:create')")
    @AuditLog(action = "opp:update", targetType = "OPPORTUNITY", targetId = "#id")
    public Result<Void> update(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody OppSaveRequest req) {
        oppService.update(uid, id, req);
        return Result.ok();
    }

    @Operation(summary = "商机删除（仅 OPEN）", description = "需求 CRM-O1")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('opp:delete')")
    @AuditLog(action = "opp:delete", targetType = "OPPORTUNITY", targetId = "#id")
    public Result<Void> delete(@CurrentUser Long uid, @PathVariable Long id) {
        oppService.delete(uid, id);
        return Result.ok();
    }

    @Operation(summary = "阶段流转（推进/回退留痕）", description = "需求 CRM-O2")
    @PostMapping("/{id}/stage")
    @PreAuthorize("@ss.hasPerm('opp:stage')")
    @AuditLog(action = "opp:stage", targetType = "OPPORTUNITY", targetId = "#id")
    public Result<Void> stage(@CurrentUser Long uid, @PathVariable Long id, @Valid @RequestBody StageRequest req) {
        oppService.stage(uid, id, req.getToStage(), req.getReason());
        return Result.ok();
    }

    @Operation(summary = "标记成交（客户生命周期联动 WON）", description = "需求 CRM-O4")
    @PostMapping("/{id}/win")
    @PreAuthorize("@ss.hasPerm('opp:win')")
    @AuditLog(action = "opp:win", targetType = "OPPORTUNITY", targetId = "#id")
    public Result<Void> win(@CurrentUser Long uid, @PathVariable Long id, @RequestBody(required = false) CloseRequest req) {
        oppService.win(uid, id, req == null ? null : req.getReason());
        return Result.ok();
    }

    @Operation(summary = "标记丢单（原因必填）", description = "需求 CRM-O4")
    @PostMapping("/{id}/lose")
    @PreAuthorize("@ss.hasPerm('opp:lose')")
    @AuditLog(action = "opp:lose", targetType = "OPPORTUNITY", targetId = "#id")
    public Result<Void> lose(@CurrentUser Long uid, @PathVariable Long id, @Valid @RequestBody CloseRequest req) {
        oppService.lose(uid, id, req.getReason());
        return Result.ok();
    }

    /** 阶段流转请求体 */
    @Data
    static class StageRequest {
        @NotNull(message = "目标阶段不能为空")
        private Integer toStage;
        private String reason;
    }

    /** 成交/丢单请求体（丢单原因必填校验在 OppService.lose） */
    @Data
    static class CloseRequest {
        private String reason;
    }
}
