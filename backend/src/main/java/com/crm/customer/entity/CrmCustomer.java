package com.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 客户（CRM-C1/C4）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "crm_customer", autoResultMap = true)
public class CrmCustomer extends BaseEntity {

    private String name;
    /** VIP / IMPORTANT / NORMAL（字典 customer_level） */
    private String level;
    private String industry;
    private String source;
    private String region;
    private String address;
    /** 归属业务员（数据范围过滤键） */
    private Long ownerId;
    /** ACTIVE / COOPERATING / INACTIVE */
    private String status;
    /** 生命周期（CRM-C4）：POTENTIAL/FOLLOWING/WON/LOST/DORMANT */
    private String lifecycleStatus;
    private String remark;
    /** 自定义字段（CRM-C1，JSONB） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> customFields;
}
