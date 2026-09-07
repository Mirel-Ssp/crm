package com.crm.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同（CRM-R3）
 * 签署状态机：UNSIGNED → SIGNED → EXECUTING → EXPIRED（到期由调度器推进）
 *            UNSIGNED/SIGNED/EXECUTING → TERMINATED（提前终止）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_contract")
public class SalesContract extends BaseEntity {

    private String contractNo;
    private String title;
    private Long customerId;
    private Long opportunityId;
    private Long orderId;
    private Long quoteId;
    /** 归属业务员（数据范围过滤键） */
    private Long ownerId;
    private BigDecimal amount;
    private String signStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 到期预警提前天数 */
    private Integer reminderDays;
    /** 附件地址（预留 MinIO 落地） */
    private String attachmentUrl;
    private LocalDateTime signedAt;
    private LocalDateTime terminatedAt;
    private String terminateReason;
    private String remark;
    /** 乐观锁 */
    @Version
    private Long version;
}
