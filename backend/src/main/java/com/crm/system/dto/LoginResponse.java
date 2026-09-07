package com.crm.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 登录/刷新令牌响应
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresInMinutes;
    private UserInfo userInfo;

    /**
     * 用户信息（SYS-DV-02 扩展：permissions/dataScope 供前端菜单权限过滤与口径提示）
     */
    @Data
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private Long orgId;
        /** 权限点编码集 */
        private List<String> permissions;
        /** 最宽数据范围 SELF/TEAM/ALL */
        private String dataScope;
        /** 1=需强制改密（首登/被重置）；前端据此弹改密弹窗 */
        private Integer mustChangePassword;
    }
}
