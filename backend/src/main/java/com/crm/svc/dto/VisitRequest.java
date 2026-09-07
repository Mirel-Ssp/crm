package com.crm.svc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单回访请求（CRM-S2：回访任务 + 满意度评分）
 */
@Data
public class VisitRequest {
    @NotBlank(message = "回访内容不能为空")
    private String content;
    /** 满意度 1~5 */
    @NotNull(message = "满意度评分不能为空")
    @Min(value = 1, message = "满意度最低 1 分")
    @Max(value = 5, message = "满意度最高 5 分")
    private Integer satisfaction;
    /** 下次跟进时间（可选） */
    private LocalDateTime nextFollowupAt;
}
