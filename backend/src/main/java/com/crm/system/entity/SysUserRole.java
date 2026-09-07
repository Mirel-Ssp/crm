package com.crm.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-角色关联（联合主键表，仅经定制 SQL/Wrapper 维护）
 */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    @TableId
    private Long userId;
    private Long roleId;
}
