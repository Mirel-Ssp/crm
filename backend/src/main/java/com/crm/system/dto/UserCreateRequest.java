package com.crm.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 成员创建请求（SYS-DV-01）
 */
@Data
public class UserCreateRequest {

    @NotNull(message = "所属组织不能为空")
    private Long orgId;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 初始密码 ≥8 位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码至少 8 位")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String email;
    private String phone;

    /** 初始角色（可空） */
    private List<Long> roleIds;
}
