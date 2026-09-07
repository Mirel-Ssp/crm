package com.crm.va.controller;

import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.audit.AuditLog;
import com.crm.common.security.CurrentUser;
import com.crm.va.service.CustomerScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;
import java.util.Map;

/**
 * 客户价值评分接口（VA-1~VA-5）
 * 权限：904 va:score:list 查询；905 va:score:manage 重算/调整/唤醒分配
 */
@Tag(name = "客户价值 VA")
@RestController
@RequestMapping("/api/value")
@RequiredArgsConstructor
public class CustomerValueController {

    private final CustomerScoreService scoreService;

    @Operation(summary = "客户最新评分分页（分层/关键词过滤）", description = "需求 VA-2")
    @GetMapping("/scores")
    @PreAuthorize("@ss.hasPerm('va:score:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid,
                                                        @RequestParam(required = false) Integer pageNum,
                                                        @RequestParam(required = false) Integer pageSize,
                                                        @RequestParam(required = false) String tier,
                                                        @RequestParam(required = false) String keyword) {
        return Result.ok(scoreService.page(uid, pageNum, pageSize, tier, keyword));
    }

    @Operation(summary = "分层分布（四层计数）", description = "需求 VA-2")
    @GetMapping("/distribution")
    @PreAuthorize("@ss.hasPerm('va:score:list')")
    public Result<List<Map<String, Object>>> distribution(@CurrentUser Long uid) {
        return Result.ok(scoreService.distribution(uid));
    }

    @Operation(summary = "客户评分趋势（近 30/90 天）", description = "需求 VA-4")
    @GetMapping("/scores/{customerId}/trend")
    @PreAuthorize("@ss.hasPerm('va:score:list')")
    public Result<List<Map<String, Object>>> trend(@PathVariable Long customerId,
                                                   @RequestParam(required = false) Integer days) {
        return Result.ok(scoreService.trend(customerId, days));
    }

    @Operation(summary = "沉默客户待唤醒列表", description = "需求 VA-5")
    @GetMapping("/silent")
    @PreAuthorize("@ss.hasPerm('va:score:list')")
    public Result<List<Map<String, Object>>> silentList(@CurrentUser Long uid) {
        return Result.ok(scoreService.silentList(uid));
    }

    @Operation(summary = "沉默客户唤醒分配（生成跟进待办）", description = "需求 VA-5")
    @PostMapping("/silent/{customerId}/assign")
    @PreAuthorize("@ss.hasPerm('va:score:manage')")
    @AuditLog(action = "value:wake-assign", targetType = "CUSTOMER", targetId = "#customerId")
    public Result<Void> wakeAssign(@CurrentUser Long uid, @PathVariable Long customerId,
                                   @RequestBody Map<String, Long> body) {
        scoreService.wakeAssign(uid, customerId, body.get("assigneeId"));
        return Result.ok();
    }

    @Operation(summary = "手动调整评分（重算跳过，留痕）", description = "需求 VA-2")
    @PostMapping("/manual")
    @PreAuthorize("@ss.hasPerm('va:score:manage')")
    @AuditLog(action = "value:score-manual", targetType = "CUSTOMER")
    public Result<Void> manualAdjust(@CurrentUser Long uid, @RequestBody Map<String, Object> body) {
        Long customerId = body.get("customerId") == null ? null : Long.valueOf(String.valueOf(body.get("customerId")));
        Double score = body.get("score") == null ? null : Double.valueOf(String.valueOf(body.get("score")));
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        scoreService.manualAdjust(uid, customerId, score, reason);
        return Result.ok();
    }

    @Operation(summary = "全量重算（每日 02:00 自动执行，亦可手动触发）", description = "需求 VA-1")
    @PostMapping("/recalc")
    @PreAuthorize("@ss.hasPerm('va:score:manage')")
    @AuditLog(action = "value:score-recalc")
    public Result<Integer> recalc(@CurrentUser Long uid) {
        return Result.ok(scoreService.recalcAll());
    }

    @Operation(summary = "五维权重/沉默阈值读取", description = "需求 VA-1")
    @GetMapping("/weights")
    @PreAuthorize("@ss.hasPerm('va:score:list')")
    public Result<Map<String, Object>> weights() {
        return Result.ok(scoreService.weights());
    }

    @Operation(summary = "五维权重调整（和必须为 1）", description = "需求 VA-1 权重可配")
    @PutMapping("/weights")
    @PreAuthorize("@ss.hasPerm('va:score:manage')")
    @AuditLog(action = "value:weight-update")
    public Result<Void> updateWeights(@CurrentUser Long uid, @RequestBody Map<String, Double> body) {
        scoreService.updateWeights(uid, body);
        return Result.ok();
    }
}
