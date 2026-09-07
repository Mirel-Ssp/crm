package com.crm.quote.dto;

import lombok.Data;

/**
 * 报价单列表查询条件（CRM-R1）
 */
@Data
public class QuoteQuery {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** DRAFT/SUBMITTED/APPROVED/REJECTED/CONVERTED/VOID */
    private String status;
    private Long customerId;
    private Long opportunityId;
    /** 报价单号/标题模糊 */
    private String keyword;
}
