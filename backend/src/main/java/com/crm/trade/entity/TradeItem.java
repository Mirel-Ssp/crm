package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交易标的（TB-1：档案/价格/状态）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "trade_item", autoResultMap = true)
public class TradeItem extends BaseEntity {

    /** 标的代码（唯一） */
    private String code;
    private String name;
    /** 字典 item_category */
    private String category;
    /** 所属市场/交易所 */
    private String market;
    /** 参考价格（调价经 /price 留痕） */
    private BigDecimal referencePrice;
    /** LOW / MEDIUM / HIGH */
    private String riskLevel;
    /** DRAFT / LISTED / DELISTED（上架/下架） */
    private String status;
    /** 扩展属性（JSONB） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> attributes;
    /** 手续费率（小数，如 0.0015） */
    private BigDecimal feeRate;
    /** 最小交易量 */
    private BigDecimal minQuantity;
    private LocalDateTime listedAt;
    private LocalDateTime delistedAt;
    private String remark;
}
