package com.crm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.cache.CacheService;
import com.crm.common.security.UserState;
import com.crm.system.entity.SysRole;
import com.crm.system.entity.SysRolePermission;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysOrgMapper;
import com.crm.system.mapper.SysRoleMapper;
import com.crm.system.mapper.SysRolePermissionMapper;
import com.crm.system.mapper.SysPermissionMapper;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户态服务（SYS-DV-02 完整版核心）
 * - load：缓存命中直返；未命中查库组装（用户状态/角色+范围/权限点/TEAM 子树用户）后回填
 * - evict：管理端写操作（用户/角色/组织变更）必须调用，保证权限即时生效
 * 非活跃用户返回 null → 过滤器按未认证处理（停用即 401）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStateService {

    public static final String KEY_PREFIX = "user:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final CacheService cacheService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysOrgMapper orgMapper;

    /** 装载用户态；用户不存在/已删除返回 null（调用方按 401 处理） */
    public UserState load(Long uid) {
        String key = KEY_PREFIX + uid;
        UserState cached = cacheService.get(key, UserState.class);
        if (cached != null) {
            return cached;
        }
        UserState st = loadFromDb(uid);
        if (st != null) {
            cacheService.put(key, st, TTL);
        }
        return st;
    }

    /** 逐出单用户缓存（用户信息/角色分配变更后调用） */
    public void evict(Long uid) {
        cacheService.evict(KEY_PREFIX + uid);
    }

    private UserState loadFromDb(Long uid) {
        SysUser user = userMapper.selectById(uid);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            return null;
        }

        // 角色（含 data_scope）
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .inSql(SysRole::getId,
                        "SELECT role_id FROM sys_user_role WHERE user_id = " + uid)
                .eq(SysRole::getDeleted, 0));

        // 权限点并集
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        Set<String> perms = new HashSet<>();
        if (!roleIds.isEmpty()) {
            List<Long> permIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                            .in(SysRolePermission::getRoleId, roleIds))
                    .stream().map(SysRolePermission::getPermissionId).distinct().toList();
            if (!permIds.isEmpty()) {
                permissionMapper.selectBatchIds(permIds).forEach(p -> perms.add(p.getCode()));
            }
        }

        // 数据范围取最宽
        String dataScope = "SELF";
        for (SysRole r : roles) {
            if ("ALL".equals(r.getDataScope())) { dataScope = "ALL"; break; }
            if ("TEAM".equals(r.getDataScope())) { dataScope = "TEAM"; }
        }

        UserState st = new UserState();
        st.setUid(uid);
        st.setStatus(user.getStatus());
        st.setOrgId(user.getOrgId());
        st.setRoles(roles.stream().map(SysRole::getCode).toList());
        st.setPermissions(perms);
        st.setDataScope(dataScope);

        // TEAM：本组织（含下级）全部成员 id（含停用——历史数据仍需可见）
        if ("TEAM".equals(dataScope)) {
            List<Long> orgIds = orgMapper.selectSubtreeOrgIds(user.getOrgId());
            st.setVisibleUserIds(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getOrgId, orgIds))
                    .stream().map(SysUser::getId).toList());
        } else if ("SELF".equals(dataScope)) {
            st.setVisibleUserIds(List.of(uid));
        }
        return st;
    }
}
