package com.crm.quote.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报价单（CRM-R1）
 * 状态机：DRAFT → SUBMITTED → APPROVED / REJECTED（可改后重提）
 * APPROVED → CONVERTED（转订单）；DRAFT/SUBMITTED/REJECTED → VOID（作废）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_quote")
public class SalesQuote extends BaseEntity {

    private String quoteNo;
    private String title;
    private Long opportunityId;
    private Long customerId;
    /** 归属业务员（数据范围过滤键） */
    private Long ownerId;
    private String status;
    /** 折扣率（1=不打折，0.9=九折） */
    private BigDecimal discountRate;
    /** 税率 */
    private BigDecimal taxRate;
    /** 明细合计（服务端计算） */
    private BigDecimal totalAmount;
    /** total × discount_rate */
    private BigDecimal discountAmount;
    /** discount_amount × tax_rate */
    private BigDecimal taxAmount;
    /** discount_amount + tax_amount */
    private BigDecimal finalAmount;
    private LocalDate validUntil;
    private LocalDateTime approvedAt;
    private String rejectedReason;
    private String remark;
    /** 乐观锁 */
    @Version
    private Long version;
}
