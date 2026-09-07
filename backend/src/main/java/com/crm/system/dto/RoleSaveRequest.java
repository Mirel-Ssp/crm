package com.crm.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 角色新增/编辑请求
 */
@Data
public class RoleSaveRequest {

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}", message = "角色编码须为大写字母/数字/下划线")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    private String name;

    /** SELF / TEAM / ALL */
    @Pattern(regexp = "SELF|TEAM|ALL", message = "数据范围取值非法")
    private String dataScope = "SELF";

    private String remark;
}
