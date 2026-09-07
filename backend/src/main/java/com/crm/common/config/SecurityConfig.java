package com.crm.common.config;

import com.crm.common.security.JwtAuthFilter;
import com.crm.common.util.IpUtil;
import com.crm.system.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 安全配置（INF-DV-03 收紧版 + SYS-DV-02 方法安全 + SYS-DV-04 越权审计）
 * - /api/auth/**、/api/health、OpenAPI、actuator 匿名放行
 * - 其余 /api/** 必须携带有效 access 令牌
 * - 401/403 统一输出 Result JSON（与 INF-DS-02 §1 一致）
 * - URL 级 403（accessDeniedHandler）与方法级 403（GlobalExceptionHandler）均记录审计
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // SYS-DV-02：启用 @PreAuthorize("@ss.hasPerm(...)") 方法级校验
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuditService auditService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                // RTP-DV-02：WS 握手由 NotifyWebSocketConfig 拦截器以 ?token= 强校验
                                // （浏览器 WS 无法携带 Authorization 头，JwtAuthFilter 不生效）
                                "/ws/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, 40100, "未登录或登录已过期"))
                        .accessDeniedHandler((req, res, e) -> {
                            // URL 级 403 审计（方法级 403 由 GlobalExceptionHandler 记录）
                            auditService.record(currentUid(req), "access:denied", "URI", null,
                                    "{\"method\":\"" + req.getMethod() + "\",\"uri\":\"" + req.getRequestURI() + "\"}",
                                    IpUtil.clientIp(req));
                            writeJson(res, HttpServletResponse.SC_FORBIDDEN, 40300, "无权限执行该操作");
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 从 SecurityContext 取 uid（URL 级 403 场景已认证，principal=uid Long） */
    private Long currentUid(jakarta.servlet.http.HttpServletRequest req) {
        var a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof Long uid ? uid : null;
    }

    /** bcrypt：需求 §5 安全「密码 bcrypt」 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeJson(HttpServletResponse res, int status, int code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getOutputStream().write(
                ("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8));
    }
}
