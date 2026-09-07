package com.crm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.customer.entity.CrmFollowup;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface CrmFollowupMapper extends BaseMapper<CrmFollowup> {

    /**
     * 跟进及时率监控（CRM-F3）：活跃客户中最近一次跟进（无跟进回退建档时间）早于 N 天前的客户
     * 数据范围：ownerIds 为 null 不过滤（ALL）；为空列表查不到任何行（由服务层传入 -1 兜底）
     */
    @Select("""
            <script>
            SELECT c.id, c.name, c.owner_id AS "ownerId", u.real_name AS "ownerName",
                   c.level, c.lifecycle_status AS "lifecycleStatus",
                   MAX(f.created_at) AS "lastFollowupAt",
                   COUNT(f.id) AS "followupCount",
                   EXTRACT(DAY FROM now() - COALESCE(MAX(f.created_at), c.created_at)) AS "overdueDays"
            FROM crm_customer c
            LEFT JOIN crm_followup f ON f.rel_type = 'CUSTOMER' AND f.rel_id = c.id AND f.deleted = 0
            LEFT JOIN sys_user u ON u.id = c.owner_id
            WHERE c.deleted = 0 AND c.status = 'ACTIVE'
            <if test="ownerIds != null">
                AND c.owner_id IN
                <foreach collection="ownerIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
            </if>
            GROUP BY c.id, c.name, c.owner_id, u.real_name, c.level, c.lifecycle_status, c.created_at
            HAVING COALESCE(MAX(f.created_at), c.created_at) &lt; now() - make_interval(days =&gt; #{days})
            ORDER BY "overdueDays" DESC, c.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> selectOverdueCustomers(@Param("ownerIds") List<Long> ownerIds,
                                                      @Param("days") int days,
                                                      @Param("limit") int limit);

    /** 超时未跟进客户总数（与 selectOverdueCustomers 同口径，不做 LIMIT） */
    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT c.id
                FROM crm_customer c
                LEFT JOIN crm_followup f ON f.rel_type = 'CUSTOMER' AND f.rel_id = c.id AND f.deleted = 0
                WHERE c.deleted = 0 AND c.status = 'ACTIVE'
                <if test="ownerIds != null">
                    AND c.owner_id IN
                    <foreach collection="ownerIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
                </if>
                GROUP BY c.id, c.created_at
                HAVING COALESCE(MAX(f.created_at), c.created_at) &lt; now() - make_interval(days =&gt; #{days})
            ) t
            </script>
            """)
    long countOverdueCustomers(@Param("ownerIds") List<Long> ownerIds, @Param("days") int days);

    /** 超期待办：status=TODO 且下次跟进时间已过（工作台待办的预警口径） */
    @Select("""
            <script>
            SELECT f.id, f.rel_type AS "relType", f.rel_id AS "relId", f.content, f.method,
                   f.next_followup_at AS "nextFollowupAt", f.owner_id AS "ownerId",
                   u.real_name AS "ownerName",
                   EXTRACT(DAY FROM now() - f.next_followup_at) AS "overdueDays"
            FROM crm_followup f
            LEFT JOIN sys_user u ON u.id = f.owner_id
            WHERE f.deleted = 0 AND f.status = 'TODO' AND f.next_followup_at &lt; now()
            <if test="ownerIds != null">
                AND f.owner_id IN
                <foreach collection="ownerIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
            </if>
            ORDER BY f.next_followup_at ASC
            LIMIT 50
            </script>
            """)
    List<Map<String, Object>> selectOverdueTodos(@Param("ownerIds") List<Long> ownerIds);
}
