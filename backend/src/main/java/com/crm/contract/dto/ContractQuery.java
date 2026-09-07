package com.crm.contract.dto;

import lombok.Data;

/**
 * 合同列表查询条件（CRM-R3）
 */
@Data
public class ContractQuery {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** UNSIGNED/SIGNED/EXECUTING/EXPIRED/TERMINATED */
    private String signStatus;
    private Long customerId;
    /** true=只看临到期（end_date - 今天 <= reminder_days） */
    private Boolean expiring;
    /** 合同号/标题模糊 */
    private String keyword;
}
