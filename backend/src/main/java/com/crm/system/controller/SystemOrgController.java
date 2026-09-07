package com.crm.system.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.Result;
import com.crm.system.dto.OrgSaveRequest;
import com.crm.system.service.SysOrgService;
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
 * 组织管理接口（SYS-DV-01）
 */
@Tag(name = "系统管理-组织")
@RestController
@RequestMapping("/api/system/orgs")
@RequiredArgsConstructor
public class SystemOrgController {

    private final SysOrgService orgService;

    @Operation(summary = "组织树")
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPerm('system:org')")
    public Result<List<Map<String, Object>>> tree() {
        return Result.ok(orgService.tree());
    }

    @Operation(summary = "新增组织")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:org')")
    @AuditLog(action = "org:create", targetType = "ORG")
    public Result<Long> create(@Valid @RequestBody OrgSaveRequest req) {
        return Result.ok(orgService.create(req));
    }

    @Operation(summary = "编辑组织")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:org')")
    @AuditLog(action = "org:update", targetType = "ORG", targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OrgSaveRequest req) {
        orgService.update(id, req);
        return Result.ok(null);
    }

    @Operation(summary = "删除组织（须无子节点与成员）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:org')")
    @AuditLog(action = "org:delete", targetType = "ORG", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return Result.ok(null);
    }
}
