package com.crm.trade.ws;

import com.crm.common.security.UserState;
import com.crm.common.util.JwtUtil;
import com.crm.system.service.UserStateService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 配置（RTP-DV-02/03）
 * - 端点 /ws/notify：浏览器 WS API 无法自定义 Authorization 头，
 *   统一以 ?token=access 令牌握手，拦截器完成 JWT 鉴权 + 用户态校验（与 JwtAuthFilter 口径一致）
 * - 鉴权失败返回 HTTP 401；Security 对 /ws/** 匿名放行由本拦截器把关
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class NotifyWebSocketConfig implements WebSocketConfigurer {

    private final NotifyWebSocketHandler handler;
    private final JwtUtil jwtUtil;
    private final UserStateService userStateService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notify")
                .setAllowedOriginPatterns("*") // 令牌已在握手时强校验，来源仅作兜底
                .addInterceptors(authInterceptor());
    }

    private HandshakeInterceptor authInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                String token = UriComponentsBuilder.fromUri(request.getURI())
                        .build().getQueryParams().getFirst("token");
                try {
                    if (token != null && !token.isBlank()) {
                        Claims claims = jwtUtil.parse(token);
                        if (JwtUtil.TYPE_ACCESS.equals(claims.get("type", String.class))) {
                            Long uid = jwtUtil.getUid(claims);
                            UserState st = userStateService.load(uid);
                            if (st != null && "ACTIVE".equals(st.getStatus())) {
                                attributes.put("uid", uid);
                                return true;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 令牌无效/过期/用户态装载失败：统一 401
                }
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {
                // 无需处理
            }
        };
    }
}
