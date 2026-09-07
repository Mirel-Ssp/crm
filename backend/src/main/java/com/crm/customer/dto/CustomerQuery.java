package com.crm.customer.dto;

import lombok.Data;

/**
 * 客户列表查询参数（CRM-C1）
 */
@Data
public class CustomerQuery {

    /** 名称模糊搜索（trgm 索引加速） */
    private String keyword;
    private String level;
    private String status;
    /** 生命周期（CRM-C4）：POTENTIAL/FOLLOWING/WON/LOST/DORMANT */
    private String lifecycle;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
