package com.crm.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项新增/编辑请求
 */
@Data
public class DictSaveRequest {

    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    @NotBlank(message = "字典编码不能为空")
    private String code;

    @NotBlank(message = "字典值不能为空")
    private String value;

    private Integer sort = 0;
}
