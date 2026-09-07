package com.crm.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织架构（CRM-M1，树形）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org")
public class SysOrg extends BaseEntity {

    /** 0=根节点 */
    private Long parentId;
    private String name;
    /** 团队负责人（TEAM 数据范围判定用） */
    private Long leaderId;
    private Integer sort;
}
