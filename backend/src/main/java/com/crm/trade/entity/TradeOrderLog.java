package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志（状态机可追溯）
 */
@Data
@TableName("trade_order_log")
public class TradeOrderLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
