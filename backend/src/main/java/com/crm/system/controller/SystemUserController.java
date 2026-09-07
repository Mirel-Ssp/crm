package com.crm.system.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.system.dto.PasswordResetRequest;
import com.crm.system.dto.RoleIdsRequest;
import com.crm.system.dto.UserCreateRequest;
import com.crm.system.dto.UserQuery;
import com.crm.system.dto.UserUpdateRequest;
import com.crm.system.service.SysUserService;
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

import java.util.Map;

/**
 * 成员管理接口（SYS-DV-01）
 */
@Tag(name = "系统管理-成员")
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SystemUserController {

    private final SysUserService userService;

    @Operation(summary = "成员分页")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('system:user')")
    public Result<PageResult<Map<String, Object>>> page(UserQuery query) {
        return Result.ok(userService.page(query));
    }

    @Operation(summary = "创建成员（初始密码 ≥8 位）")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:user')")
    @AuditLog(action = "user:create", targetType = "USER")
    public Result<Long> create(@Valid @RequestBody UserCreateRequest req) {
        return Result.ok(userService.create(req));
    }

    @Operation(summary = "编辑成员（email/phone 留空=不变更）")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:user')")
    @AuditLog(action = "user:update", targetType = "USER", targetId = "#id")
    public Result<Void> update(@CurrentUser Long uid, @PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        userService.update(uid, id, req);
        return Result.ok(null);
    }

    @Operation(summary = "分配角色（全量覆盖）")
    @PutMapping("/{id}/roles")
    @PreAuthorize("@ss.hasPerm('system:user')")
    @AuditLog(action = "user:assignRole", targetType = "USER", targetId = "#id")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody RoleIdsRequest req) {
        userService.assignRoles(id, req.getRoleIds());
        return Result.ok(null);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    @PreAuthorize("@ss.hasPerm('system:user')")
    @AuditLog(action = "user:resetPassword", targetType = "USER", targetId = "#id")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest req) {
        userService.resetPassword(id, req.getPassword());
        return Result.ok(null);
    }

    @Operation(summary = "删除成员（内置管理员与本人不可删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:user')")
    @AuditLog(action = "user:delete", targetType = "USER", targetId = "#id")
    public Result<Void> delete(@CurrentUser Long uid, @PathVariable Long id) {
        userService.delete(uid, id);
        return Result.ok(null);
    }
}
