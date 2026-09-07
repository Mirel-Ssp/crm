package com.crm.lead.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 线索新增/编辑请求
 */
@Data
public class LeadSaveRequest {

    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    @NotBlank(message = "联系人姓名不能为空")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    /** 字典 lead_source */
    private String source;
    private String remark;
}
