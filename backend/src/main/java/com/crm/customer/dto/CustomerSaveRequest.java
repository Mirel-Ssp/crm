package com.crm.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户新增/编辑请求体（CRM-C1/C2）
 */
@Data
public class CustomerSaveRequest {

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 128, message = "客户名称最长 128 字")
    private String name;

    @Pattern(regexp = "VIP|IMPORTANT|NORMAL", message = "客户等级不合法")
    private String level;

    private String industry;
    private String source;
    private String region;
    private String address;

    @Pattern(regexp = "ACTIVE|COOPERATING|INACTIVE", message = "客户状态不合法")
    private String status;

    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 自定义字段（CRM-C1，JSONB；键值对，值支持字符串/数字/布尔） */
    private java.util.Map<String, Object> customFields;
}
