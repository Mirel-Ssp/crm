package com.crm.svc.dto;

import lombok.Data;

/**
 * 工单列表查询条件（CRM-S1）
 */
@Data
public class TicketQuery {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    /** OPEN/PROCESSING/RESOLVED/CLOSED */
    private String status;
    /** 字典 ticket_type */
    private String type;
    /** LOW/MEDIUM/HIGH/URGENT */
    private String priority;
    /** 工单号/标题模糊 */
    private String keyword;
}
