package com.crm.opportunity.dto;

import lombok.Data;

/**
 * 商机查询（CRM-O3：支持按阶段筛选下钻 + 数据范围）
 */
@Data
public class OppQuery {
    private String keyword;
    private Long customerId;
    /** 1~7；空=全部 */
    private Integer stage;
    /** OPEN / WON / LOST；空=全部 */
    private String status;
    private Integer pageNum;
    private Integer pageSize;
}
