package com.crm.lead.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 线索分配请求
 */
@Data
public class LeadAssignRequest {

    @NotNull(message = "目标成员不能为空")
    private Long userId;
}
