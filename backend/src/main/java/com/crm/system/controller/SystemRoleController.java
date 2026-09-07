package com.crm.system.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.Result;
import com.crm.system.dto.PermissionIdsRequest;
import com.crm.system.dto.RoleSaveRequest;
import com.crm.system.service.SysRoleService;
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
 * 角色与权限点管理接口（SYS-DV-01）
 */
@Tag(name = "系统管理-角色")
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class SystemRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "角色列表（含已分配权限点）")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('system:role')")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(roleService.list());
    }

    @Operation(summary = "全量权限点列表（授权勾选用）")
    @GetMapping("/permissions")
    @PreAuthorize("@ss.hasPerm('system:role')")
    public Result<List<Map<String, Object>>> permissions() {
        return Result.ok(roleService.permissions());
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:role')")
    @AuditLog(action = "role:create", targetType = "ROLE")
    public Result<Long> create(@Valid @RequestBody RoleSaveRequest req) {
        return Result.ok(roleService.create(req));
    }

    @Operation(summary = "编辑角色（data_scope 变更即时生效）")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:role')")
    @AuditLog(action = "role:update", targetType = "ROLE", targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest req) {
        roleService.update(id, req);
        return Result.ok(null);
    }

    @Operation(summary = "分配权限点（全量覆盖，即时生效）")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("@ss.hasPerm('system:role')")
    @AuditLog(action = "role:assignPerms", targetType = "ROLE", targetId = "#id")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody PermissionIdsRequest req) {
        roleService.assignPermissions(id, req.getPermissionIds());
        return Result.ok(null);
    }

    @Operation(summary = "删除角色（被引用不可删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:role')")
    @AuditLog(action = "role:delete", targetType = "ROLE", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok(null);
    }
}
