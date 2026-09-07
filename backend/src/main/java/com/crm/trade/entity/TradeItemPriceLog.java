package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 标的调价留痕（TB-2：价格变更可追溯）
 */
@Data
@TableName("trade_item_price_log")
public class TradeItemPriceLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long itemId;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private Long operatorId;
    private LocalDateTime createdAt;
}
