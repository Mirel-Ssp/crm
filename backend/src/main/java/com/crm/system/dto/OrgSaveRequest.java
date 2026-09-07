package com.crm.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织新增/编辑请求
 */
@Data
public class OrgSaveRequest {

    /** 0=根节点下 */
    private Long parentId = 0L;

    @NotBlank(message = "组织名称不能为空")
    private String name;

    private Long leaderId;

    private Integer sort = 0;
}
