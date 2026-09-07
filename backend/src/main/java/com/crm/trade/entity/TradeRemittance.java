package com.crm.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇款记录（RM-1~4：登记 → 到账确认 → 核销）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_remittance")
public class TradeRemittance extends BaseEntity {

    /** 银行流水/汇款单号（唯一） */
    private String remitNo;
    private Long customerId;
    /** 登记业务员（数据范围过滤键） */
    private Long ownerId;
    private BigDecimal amount;
    private String currency;
    /** 汇款时间 */
    private LocalDateTime remittedAt;
    /** 凭证文件 key（ObjectStorageService） */
    private String voucherKey;
    /** PENDING_CONFIRM / CONFIRMED / PARTIALLY_WRITTEN_OFF / WRITTEN_OFF / REJECTED */
    private String status;
    private BigDecimal writtenOffAmount;
    /** 到账确认人 */
    private Long confirmerId;
    private LocalDateTime confirmedAt;
    private String rejectReason;
    private String remark;
}
