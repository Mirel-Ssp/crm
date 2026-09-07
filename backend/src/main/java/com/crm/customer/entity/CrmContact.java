package com.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import com.crm.common.web.sensitive.Sensitive;
import com.crm.common.web.sensitive.SensitiveType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户联系人（CRM-C1）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contact")
public class CrmContact extends BaseEntity {

    private Long customerId;
    private String name;
    private String position;
    /** SYS-DV-03：输出一律脱敏 */
    @Sensitive(SensitiveType.PHONE)
    private String phone;
    @Sensitive(SensitiveType.EMAIL)
    private String email;
    private String wechat;
    /** 1=主联系人 */
    private Integer isPrimary;
    private String remark;
}
