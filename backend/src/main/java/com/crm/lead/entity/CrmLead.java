package com.crm.lead.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import com.crm.common.web.sensitive.Sensitive;
import com.crm.common.web.sensitive.SensitiveType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 线索（CRM-L）：owner 为空 = 公共池
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead")
public class CrmLead extends BaseEntity {

    private String companyName;
    private String contactName;
    /** SYS-DV-03：输出一律脱敏 */
    @Sensitive(SensitiveType.PHONE)
    private String contactPhone;
    private String source;
    /** NULL=公共池 */
    private Long ownerId;
    /** PENDING / CLAIMED / ASSIGNED / CONVERTED / INVALID */
    private String status;
    /** 转客户后回写 */
    private Long convertedCustomerId;
    private LocalDateTime convertedAt;
    private String remark;
}
