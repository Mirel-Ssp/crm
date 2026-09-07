package com.crm.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 权限判定服务（SYS-DS-01 定形：@PreAuthorize("@ss.hasPerm('xxx')")）
 * 权限集已由 JwtAuthFilter 装载进 authorities，此处仅做内存判定
 */
@Component("ss")
public class PermissionService {

    /** 当前认证用户是否持有指定权限点 */
    public boolean hasPerm(String code) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals(code));
    }
}
