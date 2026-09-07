package com.crm.common.security;

import com.crm.common.util.JwtUtil;
import com.crm.system.service.UserStateService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（INF-DV-03 + SYS-DV-02 完整版）
 * 有效 access 令牌 → UserStateService 装载用户态（缓存）→
 * SecurityContext 写入 principal=uid(Long)（CurrentUserArgumentResolver/MetaObjectHandler 依赖此约定）
 * authorities=权限点编码集（@PreAuthorize("@ss.hasPerm(...)") 判定来源）
 * 用户不存在/停用/装载异常 → 不设认证，由 EntryPoint 统一返回 401
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserStateService userStateService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            try {
                Claims claims = jwtUtil.parse(token);
                if (JwtUtil.TYPE_ACCESS.equals(claims.get("type", String.class))) {
                    Long uid = jwtUtil.getUid(claims);
                    UserState st = userStateService.load(uid);
                    if (st != null && "ACTIVE".equals(st.getStatus())) {
                        List<SimpleGrantedAuthority> authorities = st.getPermissions().stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(uid, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ignored) {
                // 令牌无效或用户态装载失败：不设认证，由 EntryPoint 输出 401
            }
        }
        chain.doFilter(request, response);
    }
}
