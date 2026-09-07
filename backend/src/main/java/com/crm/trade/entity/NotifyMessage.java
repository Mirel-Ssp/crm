package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知（RTP 轮询兜底：状态变更三端同步的事件源）
 */
@Data
@TableName("notify_message")
public class NotifyMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收人 */
    private Long userId;
    /** FOLLOWUP_DUE / ORDER_EVENT / REMIT_EVENT / SYSTEM */
    private String type;
    private String title;
    private String content;
    private String relType;
    private Long relId;
    /** NULL = 未读 */
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
