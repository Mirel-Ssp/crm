package com.crm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.system.dto.UserCreateRequest;
import com.crm.system.dto.UserQuery;
import com.crm.system.dto.UserUpdateRequest;
import com.crm.system.entity.SysOrg;
import com.crm.system.entity.SysRole;
import com.crm.system.entity.SysUser;
import com.crm.system.entity.SysUserRole;
import com.crm.system.mapper.SysOrgMapper;
import com.crm.system.mapper.SysRoleMapper;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成员管理（SYS-DV-01）
 * - 分页/创建（username 唯一，密码 ≥8 bcrypt）/编辑（email/phone 留空不变更）
 * - 角色分配（全量覆盖）/密码重置/软删除
 * - 约束：id=1 内置管理员禁停用禁删（60202）；禁删本人（60203）
 * - 所有写操作后 evict 对应用户态
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final long ADMIN_ID = 1L;

    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserStateService userStateService;

    public PageResult<Map<String, Object>> page(UserQuery query) {
        int pageSize = Math.min(query.getPageSize() == null ? 20 : query.getPageSize(), 200);
        Page<SysUser> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(), pageSize);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(SysUser::getUsername, query.getKeyword())
                        .or().like(SysUser::getRealName, query.getKeyword()))
                .eq(query.getOrgId() != null, SysUser::getOrgId, query.getOrgId())
                .eq(StringUtils.hasText(query.getStatus()), SysUser::getStatus, query.getStatus())
                .orderByAsc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(page, wrapper);

        // 组织名与角色编码回填
        Map<Long, String> orgNames = orgMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysOrg::getId, SysOrg::getName));

        List<Map<String, Object>> rows = result.getRecords().stream().map(u -> {
            List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, u.getId()))
                    .stream().map(SysUserRole::getRoleId).toList();
            List<String> roleNames = roleIds.isEmpty() ? List.of()
                    : roleMapper.selectBatchIds(roleIds).stream().map(SysRole::getName).toList();
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("realName", u.getRealName());
            row.put("orgId", u.getOrgId());
            row.put("orgName", orgNames.getOrDefault(u.getOrgId(), "-"));
            row.put("email", u.getEmail() == null ? "" : u.getEmail());
            row.put("phone", u.getPhone() == null ? "" : u.getPhone());
            row.put("status", u.getStatus());
            row.put("roleIds", roleIds);
            row.put("roleNames", roleNames);
            row.put("createdAt", u.getCreatedAt() == null ? "" : u.getCreatedAt().toString());
            return row;
        }).toList();
        return new PageResult<>(rows, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Transactional
    public Long create(UserCreateRequest req) {
        Long dup = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "用户名已存在");
        }
        if (orgMapper.selectById(req.getOrgId()) == null) {
            throw new BizException(ResultCode.ORG_NOT_FOUND);
        }
        SysUser user = new SysUser();
        user.setOrgId(req.getOrgId());
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setEmail(blankToNull(req.getEmail()));
        user.setPhone(blankToNull(req.getPhone()));
        user.setStatus("ACTIVE");
        user.setMustChangePassword(1);
        userMapper.insert(user);
        if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
            assignRolesInternal(user.getId(), req.getRoleIds());
        }
        userStateService.evict(user.getId());
        return user.getId();
    }

    @Transactional
    public void update(Long uid, Long id, UserUpdateRequest req) {
        SysUser user = requireUser(id);
        if (orgMapper.selectById(req.getOrgId()) == null) {
            throw new BizException(ResultCode.ORG_NOT_FOUND);
        }
        user.setOrgId(req.getOrgId());
        user.setRealName(req.getRealName());
        // 留空 = 不变更（防脱敏掩码回写覆盖原值）
        if (StringUtils.hasText(req.getEmail())) {
            user.setEmail(req.getEmail());
        }
        if (StringUtils.hasText(req.getPhone())) {
            user.setPhone(req.getPhone());
        }
        if (StringUtils.hasText(req.getStatus()) && !req.getStatus().equals(user.getStatus())) {
            protectAdmin(uid, id);
            user.setStatus(req.getStatus());
        }
        userMapper.updateById(user);
        userStateService.evict(id);
    }

    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        requireUser(id);
        if (roleIds != null && !roleIds.isEmpty()) {
            long count = roleMapper.selectBatchIds(roleIds).size();
            if (count != roleIds.size()) {
                throw new BizException(ResultCode.ROLE_NOT_FOUND);
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        if (roleIds != null) {
            assignRolesInternal(id, roleIds);
        }
        userStateService.evict(id);
    }

    @Transactional
    public void resetPassword(Long id, String rawPassword) {
        SysUser user = requireUser(id);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(1);
        userMapper.updateById(user);
        userStateService.evict(id);
    }

    @Transactional
    public void delete(Long uid, Long id) {
        requireUser(id);
        protectAdmin(uid, id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
        userStateService.evict(id);
    }

    private void assignRolesInternal(Long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    /** admin(1) 与本人保护：停用/删除一律拒绝 */
    private void protectAdmin(Long operatorUid, Long targetId) {
        if (targetId == ADMIN_ID) {
            throw new BizException(ResultCode.ADMIN_PROTECTED);
        }
        if (targetId.equals(operatorUid)) {
            throw new BizException(ResultCode.SELF_OPERATION_FORBIDDEN);
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
