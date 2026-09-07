package com.crm.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 审批/驳回/取消请求（OD-4/5：驳回原因必填） */
@Data
public class OrderActionRequest {

    /** 驳回必填，通过/取消可选 */
    private String reason;
}
