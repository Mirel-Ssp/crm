package com.crm.trade.dto;

import lombok.Data;

/**
 * 订单查询（OD-2/3：三端数据范围过滤由服务端注入）
 */
@Data
public class OrderQuery {
    /** 订单号模糊 */
    private String keyword;
    /** 状态；空=全部 */
    private String status;
    private Long customerId;
    private Long itemId;
    /** BUY / SELL；空=全部 */
    private String direction;
    private Integer pageNum;
    private Integer pageSize;
}
