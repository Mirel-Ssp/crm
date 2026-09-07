package com.crm.common.security;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 用户态快照（SYS-DV-02）：JWT 过滤器随认证装载，缓存 key=user:{uid}
 * 权限/角色/组织变更时由管理服务调用 UserStateService.evict 即时生效
 */
@Data
public class UserState implements Serializable {

    private Long uid;
    /** ACTIVE / LOCKED / DISABLED（非 ACTIVE 一律按未认证处理） */
    private String status;
    private Long orgId;
    /** 角色编码集，如 [ADMIN] */
    private List<String> roles;
    /** 最宽数据范围：SELF / TEAM / ALL */
    private String dataScope;
    /** 功能权限点编码集（@ss.hasPerm 判定来源） */
    private Set<String> permissions;
    /** TEAM 范围可见用户 id（本组织含下级成员）；SELF=仅本人；ALL 不使用 */
    private List<Long> visibleUserIds;
}
