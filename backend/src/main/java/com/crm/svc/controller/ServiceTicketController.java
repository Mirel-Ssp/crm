package com.crm.svc.controller;

import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.audit.AuditLog;
import com.crm.common.security.CurrentUser;
import com.crm.svc.dto.TicketQuery;
import com.crm.svc.dto.TicketSaveRequest;
import com.crm.svc.dto.VisitRequest;
import com.crm.svc.service.ServiceTicketService;
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

import java.util.List;
import java.util.Map;

/**
 * 服务工单接口（CRM-S1 流转 / CRM-S2 回访满意度）
 * 权限：901 svc:ticket:list 查询；902 svc:ticket:manage 创建与流转；903 svc:satisfaction 回访统计
 */
@Tag(name = "服务工单 SVC")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class ServiceTicketController {

    private final ServiceTicketService ticketService;

    @Operation(summary = "工单分页列表", description = "需求 CRM-S1")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('svc:ticket:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, TicketQuery query) {
        return Result.ok(ticketService.page(uid, query));
    }

    @Operation(summary = "工单详情", description = "需求 CRM-S1")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('svc:ticket:list')")
    public Result<Map<String, Object>> detail(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(ticketService.detail(uid, id));
    }

    @Operation(summary = "创建工单（OPEN）", description = "需求 CRM-S1")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('svc:ticket:manage')")
    @AuditLog(action = "ticket:create", targetType = "TICKET")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody TicketSaveRequest req) {
        return Result.ok(ticketService.create(uid, req));
    }

    @Operation(summary = "指派/改派（经理指派他人，业务员认领自己）", description = "需求 CRM-S1")
    @PostMapping("/{id}/assign")
    @PreAuthorize("@ss.hasPerm('svc:ticket:manage')")
    @AuditLog(action = "ticket:assign", targetType = "TICKET", targetId = "#id")
    public Result<Void> assign(@CurrentUser Long uid, @PathVariable Long id,
                               @RequestBody Map<String, Long> body) {
        ticketService.assign(uid, id, body.get("assigneeId"));
        return Result.ok();
    }

    @Operation(summary = "开始处理（OPEN→PROCESSING）", description = "需求 CRM-S1")
    @PostMapping("/{id}/process")
    @PreAuthorize("@ss.hasPerm('svc:ticket:manage')")
    public Result<Void> process(@CurrentUser Long uid, @PathVariable Long id) {
        ticketService.process(uid, id);
        return Result.ok();
    }

    @Operation(summary = "标记解决（OPEN/PROCESSING→RESOLVED）", description = "需求 CRM-S1")
    @PostMapping("/{id}/resolve")
    @PreAuthorize("@ss.hasPerm('svc:ticket:manage')")
    public Result<Void> resolve(@CurrentUser Long uid, @PathVariable Long id,
                                @RequestBody(required = false) Map<String, String> body) {
        ticketService.resolve(uid, id, body == null ? null : body.get("remark"));
        return Result.ok();
    }

    @Operation(summary = "关闭工单（RESOLVED→CLOSED，终态）", description = "需求 CRM-S1")
    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.hasPerm('svc:ticket:manage')")
    @AuditLog(action = "ticket:close", targetType = "TICKET", targetId = "#id")
    public Result<Void> close(@CurrentUser Long uid, @PathVariable Long id) {
        ticketService.close(uid, id);
        return Result.ok();
    }

    @Operation(summary = "SLA 超期预警（未关闭且已超期，按超期最久排序，最多 50 条）", description = "批次6 增强")
    @GetMapping("/sla/warning")
    @PreAuthorize("@ss.hasPerm('svc:ticket:list')")
    public Result<List<Map<String, Object>>> slaWarning(@CurrentUser Long uid) {
        return Result.ok(ticketService.slaWarning(uid));
    }

    @Operation(summary = "回访登记 + 满意度评分（1~5）", description = "需求 CRM-S2")
    @PostMapping("/{id}/visit")
    @PreAuthorize("@ss.hasPerm('svc:satisfaction')")
    public Result<Void> visit(@CurrentUser Long uid, @PathVariable Long id, @Valid @RequestBody VisitRequest req) {
        ticketService.visit(uid, id, req);
        return Result.ok();
    }

    @Operation(summary = "满意度统计（均分/分布/已解决总数）", description = "需求 CRM-S2")
    @GetMapping("/satisfaction/summary")
    @PreAuthorize("@ss.hasPerm('svc:satisfaction')")
    public Result<Map<String, Object>> satisfactionSummary(@CurrentUser Long uid) {
        return Result.ok(ticketService.satisfactionSummary(uid));
    }
}
