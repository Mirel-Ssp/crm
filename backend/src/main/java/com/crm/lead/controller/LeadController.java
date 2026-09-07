package com.crm.lead.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.lead.dto.LeadAssignRequest;
import com.crm.lead.dto.LeadQuery;
import com.crm.lead.dto.LeadSaveRequest;
import com.crm.lead.service.LeadService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 线索接口（CRM-L 最小闭环）
 */
@Tag(name = "线索管理 CRM-L")
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @Operation(summary = "线索分页（pool=public|mine）")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('lead:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, LeadQuery query) {
        return Result.ok(leadService.page(uid, query));
    }

    @Operation(summary = "线索详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('lead:list')")
    public Result<Map<String, Object>> detail(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(leadService.detail(uid, id));
    }

    @Operation(summary = "公共池建档（手机号防重）")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('lead:create')")
    @AuditLog(action = "lead:create", targetType = "LEAD")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody LeadSaveRequest req) {
        return Result.ok(leadService.create(uid, req));
    }

    @Operation(summary = "编辑线索")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('lead:create')")
    @AuditLog(action = "lead:update", targetType = "LEAD", targetId = "#id")
    public Result<Void> update(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody LeadSaveRequest req) {
        leadService.update(uid, id, req);
        return Result.ok(null);
    }

    @Operation(summary = "删除线索（已转客户禁删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('lead:create')")
    @AuditLog(action = "lead:delete", targetType = "LEAD", targetId = "#id")
    public Result<Void> delete(@CurrentUser Long uid, @PathVariable Long id) {
        leadService.delete(uid, id);
        return Result.ok(null);
    }

    @Operation(summary = "可分配成员（数据范围内启用成员）")
    @GetMapping("/assignable")
    @PreAuthorize("@ss.hasPerm('lead:assign')")
    public Result<List<Map<String, Object>>> assignable(@CurrentUser Long uid) {
        return Result.ok(leadService.assignableUsers(uid));
    }

    @Operation(summary = "领取公共池线索")
    @PostMapping("/{id}/claim")
    @PreAuthorize("@ss.hasPerm('lead:claim')")
    @AuditLog(action = "lead:claim", targetType = "LEAD", targetId = "#id")
    public Result<Void> claim(@CurrentUser Long uid, @PathVariable Long id) {
        leadService.claim(uid, id);
        return Result.ok(null);
    }

    @Operation(summary = "分配线索给成员")
    @PostMapping("/{id}/assign")
    @PreAuthorize("@ss.hasPerm('lead:assign')")
    @AuditLog(action = "lead:assign", targetType = "LEAD", targetId = "#id")
    public Result<Void> assign(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody LeadAssignRequest req) {
        leadService.assign(uid, id, req.getUserId());
        return Result.ok(null);
    }

    @Operation(summary = "转客户（事务内建档）")
    @PostMapping("/{id}/convert")
    @PreAuthorize("@ss.hasPerm('lead:convert')")
    @AuditLog(action = "lead:convert", targetType = "LEAD", targetId = "#id")
    public Result<Long> convert(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(leadService.convert(uid, id));
    }

    @Operation(summary = "作废线索")
    @PostMapping("/{id}/invalidate")
    @PreAuthorize("@ss.hasPerm('lead:invalidate')")
    @AuditLog(action = "lead:invalidate", targetType = "LEAD", targetId = "#id")
    public Result<Void> invalidate(@CurrentUser Long uid, @PathVariable Long id) {
        leadService.invalidate(uid, id);
        return Result.ok(null);
    }
}
