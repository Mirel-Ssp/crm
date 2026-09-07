package com.crm.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 成员编辑请求：email/phone 留空 = 不变更（SYS-DV-03 防掩码回写污染）
 */
@Data
public class UserUpdateRequest {

    @NotNull(message = "所属组织不能为空")
    private Long orgId;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    /** 留空 = 不修改 */
    private String email;
    private String phone;

    /** ACTIVE / LOCKED / DISABLED */
    private String status;
}
