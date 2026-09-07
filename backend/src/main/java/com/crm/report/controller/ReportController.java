package com.crm.report.controller;

import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 报表接口（RPT-DV-01/02/03）：客户/线索/商机三报表 + CSV 导出（UTF-8 BOM）
 */
@Tag(name = "报表 RPT")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "客户报表（等级/生命周期分布）", description = "需求 RPT-DV-01")
    @GetMapping("/customer")
    @PreAuthorize("@ss.hasPerm('report:view')")
    public Result<Map<String, Object>> customer(@CurrentUser Long uid,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        return Result.ok(reportService.customerReport(uid, from, to));
    }

    @Operation(summary = "线索报表（状态分布/转化率）", description = "需求 RPT-DV-01")
    @GetMapping("/lead")
    @PreAuthorize("@ss.hasPerm('report:view')")
    public Result<Map<String, Object>> lead(@CurrentUser Long uid,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to) {
        return Result.ok(reportService.leadReport(uid, from, to));
    }

    @Operation(summary = "商机报表（漏斗/丢单原因）", description = "需求 RPT-DV-01")
    @GetMapping("/opportunity")
    @PreAuthorize("@ss.hasPerm('report:view')")
    public Result<Map<String, Object>> opportunity(@CurrentUser Long uid,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to) {
        return Result.ok(reportService.opportunityReport(uid, from, to));
    }

    @Operation(summary = "跟进及时率监控", description = "需求 CRM-F3：活跃客户超 N 天未跟进占比 + 超时清单 + 超期待办")
    @GetMapping("/followup-timeliness")
    @PreAuthorize("@ss.hasPerm('report:view')")
    public Result<Map<String, Object>> followupTimeliness(@CurrentUser Long uid,
                                                          @RequestParam(required = false) Integer days) {
        return Result.ok(reportService.followupTimeliness(uid, days));
    }

    @Operation(summary = "CSV 导出（type=customer/lead/opportunity/followup-timeliness）", description = "需求 RPT-DV-02")
    @GetMapping("/{type}/export")
    @PreAuthorize("@ss.hasPerm('report:view')")
    public ResponseEntity<byte[]> export(@CurrentUser Long uid, @PathVariable String type,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        String filename = type + "-report-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(reportService.exportCsv(uid, type, from, to));
    }
}
