package com.crm.system.service;

import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.common.security.UserState;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色与数据范围服务（SYS-DV-02 完整版，基于 UserStateService 缓存）
 * - ALL  → 不过滤（visibleOwnerIds 返回 null）
 * - TEAM → 本组织（含下级部门）成员 id 集合
 * - SELF → 仅本人
 * 业务查询统一走 visibleOwnerIds + wrapper.in（ALL 时 null 表示不加条件）
 */
@Service
@RequiredArgsConstructor
public class ScopeService {

    public enum Scope { ALL, TEAM, SELF }

    private final UserStateService userStateService;
    private final SysUserMapper sysUserMapper;

    /** 取当前用户最大数据范围（走缓存） */
    public Scope scopeOf(Long uid) {
        UserState st = userStateService.load(uid);
        if (st == null) {
            return Scope.SELF;
        }
        return Scope.valueOf(st.getDataScope());
    }

    /**
     * 数据范围过滤键：返回 null 表示 ALL 不过滤；
     * 返回列表用 wrapper.in(ownerId, ids) 过滤（空列表时调用方须自行兜底 eq(-1)）。
     */
    public List<Long> visibleOwnerIds(Long uid) {
        UserState st = userStateService.load(uid);
        if (st == null) {
            return List.of(uid);
        }
        return switch (st.getDataScope()) {
            case "ALL" -> null;
            case "TEAM" -> st.getVisibleUserIds() == null ? List.of(uid) : st.getVisibleUserIds();
            default -> List.of(uid);
        };
    }

    /** 校验目标用户是否在当前用户数据范围内（ALL 恒通过） */
    public boolean canSee(Long uid, Long targetOwnerId) {
        List<Long> ids = visibleOwnerIds(uid);
        return ids == null || ids.contains(targetOwnerId);
    }

    /** 校验用户存在且可用（创建业务数据时归属人合法性） */
    public SysUser requireActiveUser(Long uid) {
        SysUser user = sysUserMapper.selectById(uid);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }
}
