package com.crm.svc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单创建请求（CRM-S1：关联客户与联系人）
 */
@Data
public class TicketSaveRequest {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    /** 可选：关联联系人 */
    private Long contactId;
    @NotBlank(message = "工单类型不能为空")
    private String type;
    /** LOW/MEDIUM/HIGH/URGENT，默认 MEDIUM */
    private String priority;
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
    /** SLA 到期时间（可选） */
    private LocalDateTime slaDueAt;
    /** 创建时直接指派（可选，需有指派权限） */
    private Long assigneeId;
}
