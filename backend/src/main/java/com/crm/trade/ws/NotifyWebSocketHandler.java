package com.crm.trade.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知 WebSocket 处理器（RTP-DV-02）
 * - 握手成功后按 uid 注册会话（同一用户多端登录多会话并存）
 * - NotifyService.publish 落库后调用 push 增量推送，前端徽标秒级更新
 * - 推送失败仅关闭该会话（前端 5s 轮询兜底，RTP-DV-01 保留），不影响主业务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    /** uid → 该用户全部在线会话（多端登录） */
    private static final Map<Long, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long uid = (Long) session.getAttributes().get("uid");
        if (uid == null) {
            // 握手拦截器未放行（理论上不会出现）：直接关闭
            closeQuietly(session);
            return;
        }
        SESSIONS.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(session);
        send(session, Map.of("type", "CONNECTED"));
        log.debug("WS connected uid={} 在线会话={} 总连接={}", uid, SESSIONS.get(uid).size(), totalSessions());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = (Long) session.getAttributes().get("uid");
        if (uid == null) {
            return;
        }
        Set<WebSocketSession> set = SESSIONS.get(uid);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                SESSIONS.remove(uid, set);
            }
        }
        log.debug("WS closed uid={} status={} 总连接={}", uid, status, totalSessions());
    }

    /** 客户端上行消息仅作心跳/pong，忽略内容 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 当前为单向推送通道，收到消息仅代表连接存活
    }

    /**
     * 向指定用户全部在线会话推送一条通知（RTP-DV-02）
     * 消息体与 feed 行字段对齐，前端可直接增量渲染
     */
    public void push(Long uid, Long id, String type, String title, String content,
                     String relType, Long relId, String createdAt) {
        Set<WebSocketSession> set = SESSIONS.get(uid);
        if (set == null || set.isEmpty()) {
            return; // 用户不在线：轮询兜底覆盖
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "NOTIFY");
        payload.put("id", id);
        payload.put("msgType", type);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("relType", relType);
        payload.put("relId", relId);
        payload.put("read", false);
        payload.put("createdAt", createdAt);
        String json = toJson(payload);
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                send(s, json);
            }
        }
    }

    /** 当前在线用户数（运维观测） */
    public int onlineUsers() {
        return SESSIONS.size();
    }

    private int totalSessions() {
        return SESSIONS.values().stream().mapToInt(Set::size).sum();
    }

    private void send(WebSocketSession session, Object payload) {
        String json = payload instanceof String s ? s : toJson(payload);
        try {
            // 同一会话并发发送需串行化（TextMessage 发送非线程安全）
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.debug("WS 推送失败，关闭会话：{}", e.getMessage());
            closeQuietly(session);
            Long uid = (Long) session.getAttributes().get("uid");
            Set<WebSocketSession> set = uid == null ? null : SESSIONS.get(uid);
            if (set != null) {
                set.remove(session);
            }
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"type\":\"NOTIFY\"}"; // 序列化失败不影响连接
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // 忽略关闭异常
        }
    }
}
