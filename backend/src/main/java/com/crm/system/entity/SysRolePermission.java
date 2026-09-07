package com.crm.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-权限点关联（联合主键表）
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    @TableId
    private Long roleId;
    private Long permissionId;
}
