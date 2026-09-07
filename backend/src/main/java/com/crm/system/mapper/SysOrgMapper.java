package com.crm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.system.entity.SysOrg;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SysOrg Mapper：含组织子树递归 CTE（TEAM 数据范围 SYS-DS-01 定形方案）
 */
public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /**
     * 组织及其全部下级（含多级）org id 列表。
     * 调用方必须保证 orgId 不在其自身子树形成环（组织编辑已做防环校验）。
     */
    @Select("""
            WITH RECURSIVE subtree AS (
                SELECT id FROM sys_org WHERE id = #{orgId} AND deleted = 0
                UNION ALL
                SELECT o.id FROM sys_org o JOIN subtree s ON o.parent_id = s.id WHERE o.deleted = 0
            )
            SELECT id FROM subtree
            """)
    List<Long> selectSubtreeOrgIds(@Param("orgId") Long orgId);
}
