package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易订单（OD-1~5：六态状态机 + 乐观锁）
 * 状态机契约（V1 迁移注释）：
 * PENDING_CONFIRM → CONFIRMED → PARTIAL_DEALT → FULL_DEALT
 * PENDING_CONFIRM/CONFIRMED/PARTIAL_DEALT → CANCELLED（整单撤销）
 * PARTIAL_DEALT → PARTIAL_CANCELLED → CANCELLED
 * 终态：FULL_DEALT / CANCELLED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order")
public class TradeOrder extends BaseEntity {

    private String orderNo;
    private Long itemId;
    private Long customerId;
    /** 经手业务员（数据范围过滤键） */
    private Long ownerId;
    /** BUY / SELL */
    private String direction;
    private BigDecimal quantity;
    private BigDecimal price;
    /** 已成交数量（交割完成时置为 quantity） */
    private BigDecimal dealQuantity;
    /** quantity × price（服务端计算） */
    private BigDecimal amount;
    private BigDecimal feeRate;
    /** 服务端计算，前端只读 */
    private BigDecimal feeAmount;
    /** amount + fee_amount */
    private BigDecimal totalAmount;
    private String status;
    /** 乐观锁（并发防重） */
    @Version
    private Long version;

    /** 审批通过时间（OD-4） */
    private LocalDateTime confirmedAt;
    /** 取消/驳回时间（OD-5） */
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String remark;
}
