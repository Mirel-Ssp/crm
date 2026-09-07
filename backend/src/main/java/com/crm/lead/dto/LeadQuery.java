package com.crm.lead.dto;

import lombok.Data;

/**
 * 线索列表查询参数（CRM-L1）
 */
@Data
public class LeadQuery {

    /** public=公共池 / mine=我的线索（默认） */
    private String pool = "mine";
    private String status;
    private String keyword;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
