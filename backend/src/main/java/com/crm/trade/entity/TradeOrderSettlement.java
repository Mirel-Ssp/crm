package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销明细（RM-4：一笔汇款核销多张订单；防超核校验在服务端）
 */
@Data
@TableName("trade_order_settlement")
public class TradeOrderSettlement implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long remittanceId;
    private Long orderId;
    private BigDecimal amount;
    private Long operatorId;
    private LocalDateTime createdAt;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
