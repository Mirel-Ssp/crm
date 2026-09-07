package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易规则（TB-3：审批阈值/单笔限额，键值型配置）
 */
@Data
@TableName("trade_rule")
public class TradeRule implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String ruleKey;
    private BigDecimal ruleValue;
    private String remark;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
