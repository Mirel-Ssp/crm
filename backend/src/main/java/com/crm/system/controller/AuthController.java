package com.crm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.Result;
import com.crm.common.api.ResultCode;
import com.crm.common.audit.AuditLog;
import com.crm.common.exception.BizException;
import com.crm.common.security.CurrentUser;
import com.crm.common.security.UserState;
import com.crm.common.util.JwtUtil;
import com.crm.system.dto.LoginRequest;
import com.crm.system.dto.LoginResponse;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.UserStateService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证接口（INF-DV-03）
 * login：用户名+密码 → 双令牌；refresh：续签；me：当前会话信息
 */
@Tag(name = "认证 AUTH")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserStateService userStateService;

    @Operation(summary = "登录（发双令牌）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已锁定或停用，请联系管理员");
        }
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));
        return Result.ok(buildTokens(user));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody RefreshRequest body) {
        if (body.refreshToken == null || body.refreshToken.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少 refreshToken");
        }
        Claims claims;
        try {
            claims = jwtUtil.parse(body.refreshToken);
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }
        if (!JwtUtil.TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw new BizException(ResultCode.UNAUTHORIZED, "令牌类型错误");
        }
        SysUser user = sysUserMapper.selectById(jwtUtil.getUid(claims));
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不可用");
        }
        return Result.ok(buildTokens(user));
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<LoginResponse.UserInfo> me(@CurrentUser Long uid) {
        SysUser user = sysUserMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return Result.ok(buildUserInfo(user));
    }

    @Operation(summary = "自助改密（验旧密 + 复位首登强改标记）", description = "V2 遗留问题4")
    @PostMapping("/password")
    @AuditLog(action = "auth:change-password", targetType = "USER", targetId = "#uid")
    public Result<Void> changePassword(@CurrentUser Long uid, @Valid @RequestBody ChangePasswordRequest req) {
        SysUser user = sysUserMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "原密码错误");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "新密码不能与原密码相同");
        }
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, uid)
                .set(SysUser::getPassword, passwordEncoder.encode(req.getNewPassword()))
                .set(SysUser::getMustChangePassword, 0));
        return Result.ok();
    }

    private LoginResponse.UserInfo buildUserInfo(SysUser user) {
        UserState st = userStateService.load(user.getId());
        List<String> perms = st == null ? List.of() : List.copyOf(st.getPermissions());
        String scope = st == null ? "SELF" : st.getDataScope();
        return new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRealName(),
                user.getOrgId(), perms, scope,
                user.getMustChangePassword() == null ? 0 : user.getMustChangePassword());
    }

    private LoginResponse buildTokens(SysUser user) {
        return new LoginResponse(
                jwtUtil.generateAccess(user.getId(), user.getUsername()),
                jwtUtil.generateRefresh(user.getId(), user.getUsername()),
                120,
                buildUserInfo(user));
    }

    /** 刷新请求体 */
    @Data
    static class RefreshRequest {
        private String refreshToken;
    }

    /** 自助改密请求体 */
    @Data
    static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, message = "新密码至少 8 位")
        private String newPassword;
    }
}
