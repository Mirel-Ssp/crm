package com.crm.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.trade.entity.NotifyMessage;
import com.crm.trade.mapper.NotifyMessageMapper;
import com.crm.trade.ws.NotifyWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内通知（RTP-DV-01 轮询兜底的事件源：订单/汇款状态变更三端同步）
 * V5 升级（RTP-DV-02/03）：落库后经 WebSocket 增量推送在线用户（<1s），
 * WS 推送失败或用户离线时由前端 5s 轮询兜底，双通道互为冗余
 */
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotifyMessageMapper notifyMapper;
    private final NotifyWebSocketHandler wsHandler;

    /** 写入单条通知（收件人为空则忽略），落库后 WS 实时推送在线用户 */
    @Transactional
    public void publish(Long userId, String type, String title, String content, String relType, Long relId) {
        if (userId == null) {
            return;
        }
        NotifyMessage m = new NotifyMessage();
        m.setUserId(userId);
        m.setType(type);
        m.setTitle(title == null ? "" : title.substring(0, Math.min(title.length(), 128)));
        m.setContent(content == null ? "" : content.substring(0, Math.min(content.length(), 500)));
        m.setRelType(relType);
        m.setRelId(relId);
        // 实体未继承 BaseEntity，createdAt 无自动填充（DB 默认值不回写内存），显式赋值保证 WS 推送时间一致
        m.setCreatedAt(LocalDateTime.now());
        notifyMapper.insert(m);
        // RTP-DV-02：实时推送（离线用户由前端 5s 轮询兜底覆盖）
        try {
            wsHandler.push(userId, m.getId(), m.getType(), m.getTitle(), m.getContent(),
                    m.getRelType(), m.getRelId(), m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
        } catch (Exception ignored) {
            // 推送失败不影响落库主流程
        }
    }

    /** 通知全部经理（排除操作人本人） */
    public void publishToManagers(Long actorId, String title, String content, String relType, Long relId) {
        for (Long mid : notifyMapper.selectUserIdsByRoleCode("MANAGER")) {
            if (!mid.equals(actorId)) {
                publish(mid, "ORDER_EVENT", title, content, relType, relId);
            }
        }
    }

    /** 我的动态流（按时间倒序分页） */
    public PageResult<Map<String, Object>> feed(Long uid, Integer pageNum, Integer pageSize) {
        Page<NotifyMessage> page = new Page<>(pageNum == null ? 1 : pageNum, Math.min(pageSize == null ? 20 : pageSize, 100));
        Page<NotifyMessage> result = notifyMapper.selectPage(page, new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, uid)
                .orderByDesc(NotifyMessage::getCreatedAt));
        List<Map<String, Object>> list = new ArrayList<>();
        for (NotifyMessage m : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("type", m.getType());
            row.put("title", m.getTitle());
            row.put("content", m.getContent());
            row.put("relType", m.getRelType());
            row.put("relId", m.getRelId());
            row.put("read", m.getReadAt() != null);
            row.put("createdAt", m.getCreatedAt() == null ? "" : m.getCreatedAt().toString());
            list.add(row);
        }
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 未读数（前端轮询徽标） */
    public long unreadCount(Long uid) {
        return notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, uid)
                .isNull(NotifyMessage::getReadAt));
    }

    /** 标记已读（仅本人消息） */
    public void markRead(Long uid, Long id) {
        notifyMapper.update(null, new LambdaUpdateWrapper<NotifyMessage>()
                .eq(NotifyMessage::getId, id)
                .eq(NotifyMessage::getUserId, uid)
                .isNull(NotifyMessage::getReadAt)
                .set(NotifyMessage::getReadAt, LocalDateTime.now()));
    }

    /** 全部已读 */
    public void markAllRead(Long uid) {
        notifyMapper.update(null, new LambdaUpdateWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, uid)
                .isNull(NotifyMessage::getReadAt)
                .set(NotifyMessage::getReadAt, LocalDateTime.now()));
    }
}
