package com.crm.stat.controller;

import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.audit.AuditLog;
import com.crm.common.security.CurrentUser;
import com.crm.stat.service.BusinessStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 多维统计接口（ST-1~ST-5）
 * 权限：stat:report 查询（汇总/排名/明细/导出）；stat:manage 维护（全量重建）
 */
@Tag(name = "多维统计 STAT")
@RestController
@RequestMapping("/api/stat")
@RequiredArgsConstructor
public class MultiStatController {

    private final BusinessStatService statService;

    @Operation(summary = "业务总量统计 + 同环比（读聚合表）", description = "需求 ST-1")
    @GetMapping("/summary")
    @PreAuthorize("@ss.hasPerm('stat:report')")
    public Result<Map<String, Object>> summary(@CurrentUser Long uid,
                                               @RequestParam String dim,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return Result.ok(statService.summary(uid, dim, from, to));
    }

    @Operation(summary = "业务员业绩排名", description = "需求 ST-4")
    @GetMapping("/rank")
    @PreAuthorize("@ss.hasPerm('stat:report')")
    public Result<List<Map<String, Object>>> ownerRank(@CurrentUser Long uid,
                                                       @RequestParam String dim,
                                                       @RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to) {
        return Result.ok(statService.ownerRank(uid, dim, from, to));
    }

    @Operation(summary = "标的分析（交易量/额排行）", description = "需求 ST-5")
    @GetMapping("/items")
    @PreAuthorize("@ss.hasPerm('stat:report')")
    public Result<List<Map<String, Object>>> itemRank(@CurrentUser Long uid,
                                                      @RequestParam String dim,
                                                      @RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to) {
        return Result.ok(statService.itemRank(uid, dim, from, to));
    }

    @Operation(summary = "汇款交易明细分页（多条件筛选）", description = "需求 ST-3")
    @GetMapping("/detail")
    @PreAuthorize("@ss.hasPerm('stat:report')")
    public Result<PageResult<Map<String, Object>>> detail(@CurrentUser Long uid,
                                                          @RequestParam(required = false) Integer pageNum,
                                                          @RequestParam(required = false) Integer pageSize,
                                                          @RequestParam(required = false) Long customerId,
                                                          @RequestParam(required = false) Long itemId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String direction,
                                                          @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to) {
        return Result.ok(statService.detail(uid, pageNum, pageSize, customerId, itemId, status, direction, from, to));
    }

    @Operation(summary = "交易明细 CSV 导出（UTF-8 BOM）", description = "需求 ST-3")
    @GetMapping("/detail/export")
    @PreAuthorize("@ss.hasPerm('stat:report')")
    @AuditLog(action = "stat:detail-export")
    public ResponseEntity<byte[]> exportDetail(@CurrentUser Long uid,
                                               @RequestParam(required = false) Long customerId,
                                               @RequestParam(required = false) Long itemId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String direction,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        byte[] body = statService.exportDetailCsv(uid, customerId, itemId, status, direction, from, to);
        String filename = URLEncoder.encode("trade-detail-" + LocalDate.now() + ".csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @Operation(summary = "全维度聚合重建（每日 02:30 自动执行，亦可手动触发）", description = "需求 ST-1 预计算；仅经理/管理员可触发")
    @PostMapping("/rebuild")
    @PreAuthorize("@ss.hasPerm('stat:manage')")
    @AuditLog(action = "stat:rebuild")
    public Result<Integer> rebuild(@RequestParam(required = false) String dim) {
        return Result.ok(dim == null ? statService.rebuildAll() : statService.rebuild(dim));
    }
}
