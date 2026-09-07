package com.crm.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取（SYS-DV-04 审计用）：优先 X-Forwarded-For 首段，其次 remoteAddr
 */
public final class IpUtil {

    private IpUtil() {
    }

    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
