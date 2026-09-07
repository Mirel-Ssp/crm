package com.crm.trade.dto;

import lombok.Data;

/**
 * 汇款查询（RM-3：三端数据范围过滤由服务端注入）
 */
@Data
public class RemitQuery {
    /** 汇款单号模糊 */
    private String keyword;
    /** 状态；空=全部 */
    private String status;
    private Long customerId;
    private Integer pageNum;
    private Integer pageSize;
}
