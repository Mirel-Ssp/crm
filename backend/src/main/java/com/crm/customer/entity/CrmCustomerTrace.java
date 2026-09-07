package com.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户字段变更留痕（CRM-C4：生命周期/等级流转历史）
 */
@Data
@TableName("crm_customer_trace")
public class CrmCustomerTrace implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long customerId;
    /** lifecycle_status / level */
    private String field;
    private String fromValue;
    private String toValue;
    private Long operatorId;
    private LocalDateTime createdAt;
}
