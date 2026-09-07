package com.crm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.system.dto.RoleSaveRequest;
import com.crm.system.entity.SysPermission;
import com.crm.system.entity.SysRole;
import com.crm.system.entity.SysRolePermission;
import com.crm.system.entity.SysUserRole;
import com.crm.system.mapper.SysPermissionMapper;
import com.crm.system.mapper.SysRoleMapper;
import com.crm.system.mapper.SysRolePermissionMapper;
import com.crm.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 角色与权限点管理（SYS-DV-01）
 * - 角色增改（data_scope 变更影响数据范围）/删除（有引用 60103）
 * - 权限点全量覆盖分配；变更后 evict 该角色全部用户的用户态（不重登录即生效）
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final UserStateService userStateService;

    /** 全量角色列表 */
    public List<Map<String, Object>> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId))
                .stream().map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "code", r.getCode(),
                        "name", r.getName(),
                        "dataScope", r.getDataScope(),
                        "remark", r.getRemark() == null ? "" : r.getRemark(),
                        "permissionIds", rolePermissionIds(r.getId())))
                .toList();
    }

    /** 全量权限点（供角色授权勾选树/列表） */
    public List<Map<String, Object>> permissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getSort))
                .stream().map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "code", p.getCode(),
                        "name", p.getName(),
                        "type", p.getType()))
                .toList();
    }

    @Transactional
    public Long create(RoleSaveRequest req) {
        Long dup = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, req.getCode()));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setCode(req.getCode());
        role.setName(req.getName());
        role.setDataScope(req.getDataScope());
        role.setRemark(req.getRemark());
        roleMapper.insert(role);
        return role.getId();
    }

    @Transactional
    public void update(Long id, RoleSaveRequest req) {
        SysRole role = requireRole(id);
        role.setName(req.getName());
        // data_scope 变更影响数据范围判定，必须即时 evict
        if (req.getDataScope() != null && !req.getDataScope().equals(role.getDataScope())) {
            role.setDataScope(req.getDataScope());
        }
        role.setRemark(req.getRemark());
        roleMapper.updateById(role);
        evictRoleUsers(id);
    }

    /** 权限点全量覆盖 */
    @Transactional
    public void assignPermissions(Long id, List<Long> permissionIds) {
        requireRole(id);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            long count = permissionMapper.selectBatchIds(permissionIds).size();
            if (count != permissionIds.size()) {
                throw new BizException(ResultCode.PERMISSION_NOT_FOUND);
            }
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(id);
                rp.setPermissionId(pid);
                rolePermissionMapper.insert(rp);
            }
        }
        evictRoleUsers(id);
    }

    @Transactional
    public void delete(Long id) {
        requireRole(id);
        Long refs = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (refs != null && refs > 0) {
            throw new BizException(ResultCode.ROLE_IN_USE);
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
        evictRoleUsers(id);
    }

    private List<Long> rolePermissionIds(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
    }

    private void evictRoleUsers(Long roleId) {
        userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId))
                .forEach(ur -> userStateService.evict(ur.getUserId()));
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.ROLE_NOT_FOUND);
        }
        return role;
    }
}
