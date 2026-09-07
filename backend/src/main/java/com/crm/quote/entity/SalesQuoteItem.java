package com.crm.quote.entity;

import com.crm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报价单明细行（CRM-R1）：自由行（name/spec），可选关联交易标的（itemId 带出默认单价）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_quote_item")
public class SalesQuoteItem extends BaseEntity {

    private Long quoteId;
    private Long itemId;
    private String name;
    private String spec;
    private BigDecimal quantity;
    private BigDecimal price;
    /** quantity × price（服务端计算） */
    private BigDecimal amount;
    private Integer sort;
}
