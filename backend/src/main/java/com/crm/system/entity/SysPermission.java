package com.crm.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限点（CRM-M2）：菜单/按钮/API 三型，对应 @PreAuthorize("@ss.hasPerm(code)")
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private Long parentId;
    /** 如 customer:list / system:user */
    private String code;
    private String name;
    /** MENU / BUTTON / API */
    private String type;
    private String path;
    private Integer sort;
}
