package com.crm.system.dto;

import lombok.Data;

/**
 * 成员列表查询参数
 */
@Data
public class UserQuery {

    private String keyword;
    private Long orgId;
    private String status;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
