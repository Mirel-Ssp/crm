package com.crm.va.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.va.entity.CustomerScore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface CustomerScoreMapper extends BaseMapper<CustomerScore> {

    /**
     * 全部在营客户的评分原始指标聚合（一次 SQL 产出五维原料）
     * orderCount90d：近 90 天有效订单数；totalAmount：累计有效订单额（去取消）；
     * lastOrderAt：最近成交时间；remitAmount：已确认到账额；followupCount90d/lastFollowupAt：近 90 天跟进
     */
    @Select("""
            SELECT c.id AS "customerId", c.owner_id AS "ownerId", c.name AS "customerName",
              COALESCE(o.order_count_90d, 0)  AS "orderCount90d",
              COALESCE(o.total_amount, 0)     AS "totalAmount",
              o.last_order_at                 AS "lastOrderAt",
              COALESCE(r.remit_amount, 0)     AS "remitAmount",
              COALESCE(f.followup_count, 0)   AS "followupCount90d",
              f.last_followup_at              AS "lastFollowupAt"
            FROM crm_customer c
            LEFT JOIN (
              SELECT customer_id,
                     COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE - INTERVAL '90 days') AS order_count_90d,
                     SUM(total_amount) AS total_amount,
                     MAX(created_at)   AS last_order_at
              FROM trade_order
              WHERE deleted = 0 AND status NOT IN ('CANCELLED', 'PARTIAL_CANCELLED')
              GROUP BY customer_id
            ) o ON o.customer_id = c.id
            LEFT JOIN (
              SELECT customer_id, SUM(amount) AS remit_amount
              FROM trade_remittance
              WHERE deleted = 0 AND status IN ('CONFIRMED', 'PARTIALLY_WRITTEN_OFF', 'WRITTEN_OFF')
              GROUP BY customer_id
            ) r ON r.customer_id = c.id
            LEFT JOIN (
              SELECT rel_id AS customer_id, COUNT(*) AS followup_count, MAX(created_at) AS last_followup_at
              FROM crm_followup
              WHERE deleted = 0 AND rel_type = 'CUSTOMER' AND created_at >= CURRENT_DATE - INTERVAL '90 days'
              GROUP BY rel_id
            ) f ON f.customer_id = c.id
            WHERE c.deleted = 0 AND c.status = 'ACTIVE'
            """)
    List<Map<String, Object>> selectCustomerAggregates();

    /** 全部客户最新评分的 manual 标记（一次 SQL，替代逐客户查询，SET-BASED 重算用） */
    @Select("""
            SELECT DISTINCT ON (customer_id) customer_id AS "customerId", manual
            FROM customer_score
            ORDER BY customer_id, calc_date DESC
            """)
    List<Map<String, Object>> selectLatestManualFlags();

    /**
     * 批量 upsert（SET-BASED 重算，依赖 V10 UNIQUE(customer_id, calc_date) 幂等）
     * created_by/updated_by 留 DB 默认 0；时间戳取 DB CURRENT_TIMESTAMP
     */
    @Insert("""
            <script>
            INSERT INTO customer_score (id, customer_id, calc_date, score, tier,
              dim_freq, dim_amount, dim_active, dim_remittance, dim_followup, manual,
              created_at, updated_at)
            VALUES
            <foreach item="s" collection="rows" separator=",">
              (#{s.id}, #{s.customerId}, #{s.calcDate}, #{s.score}, #{s.tier},
               #{s.dimFreq}, #{s.dimAmount}, #{s.dimActive}, #{s.dimRemittance}, #{s.dimFollowup},
               #{s.manual}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            </foreach>
            ON CONFLICT (customer_id, calc_date) DO UPDATE SET
              score = EXCLUDED.score, tier = EXCLUDED.tier,
              dim_freq = EXCLUDED.dim_freq, dim_amount = EXCLUDED.dim_amount,
              dim_active = EXCLUDED.dim_active, dim_remittance = EXCLUDED.dim_remittance,
              dim_followup = EXCLUDED.dim_followup, manual = EXCLUDED.manual,
              updated_at = CURRENT_TIMESTAMP
            </script>
            """)
    int upsertBatch(@Param("rows") List<CustomerScore> rows);

    /** 各客户最新评分分页列表（tier/keyword/ownerIds 过滤；ownerIds=null 表示 ALL） */
    @Select("""
            <script>
            SELECT s.id, s.customer_id AS "customerId", s.score, s.tier,
                   s.dim_freq AS "dimFreq", s.dim_amount AS "dimAmount", s.dim_active AS "dimActive",
                   s.dim_remittance AS "dimRemittance", s.dim_followup AS "dimFollowup",
                   s.manual, s.calc_date AS "calcDate",
                   c.name AS "customerName", c.owner_id AS "ownerId"
            FROM customer_score s
            JOIN (SELECT customer_id, MAX(calc_date) AS max_date FROM customer_score GROUP BY customer_id) l
              ON l.customer_id = s.customer_id AND l.max_date = s.calc_date
            JOIN crm_customer c ON c.id = s.customer_id
            WHERE c.deleted = 0
            <if test="tier != null and tier != ''"> AND s.tier = #{tier}</if>
            <if test="keyword != null and keyword != ''"> AND c.name LIKE CONCAT('%', #{keyword}, '%')</if>
            <if test="ownerIds != null">
              AND c.owner_id IN
              <foreach item="i" collection="ownerIds" open="(" separator="," close=")">#{i}</foreach>
            </if>
            ORDER BY s.score DESC
            </script>
            """)
    IPage<Map<String, Object>> selectLatestScores(Page<Map<String, Object>> page,
                                                  @Param("tier") String tier,
                                                  @Param("keyword") String keyword,
                                                  @Param("ownerIds") List<Long> ownerIds);

    /** 沉默客户（VA-5）：超 N 天无成交且无跟进的在营客户 */
    @Select("""
            <script>
            SELECT c.id AS "customerId", c.name AS "customerName", c.owner_id AS "ownerId",
                   o.last_order_at AS "lastOrderAt", f.last_followup_at AS "lastFollowupAt"
            FROM crm_customer c
            LEFT JOIN (
              SELECT customer_id, MAX(created_at) AS last_order_at
              FROM trade_order WHERE deleted = 0 AND status NOT IN ('CANCELLED', 'PARTIAL_CANCELLED')
              GROUP BY customer_id
            ) o ON o.customer_id = c.id
            LEFT JOIN (
              SELECT rel_id AS customer_id, MAX(created_at) AS last_followup_at
              FROM crm_followup WHERE deleted = 0 AND rel_type = 'CUSTOMER'
              GROUP BY rel_id
            ) f ON f.customer_id = c.id
            WHERE c.deleted = 0 AND c.status = 'ACTIVE'
              AND (o.last_order_at IS NULL OR o.last_order_at &lt; #{silentBefore})
              AND (f.last_followup_at IS NULL OR f.last_followup_at &lt; #{silentBefore})
            <if test="ownerIds != null">
              AND c.owner_id IN
              <foreach item="i" collection="ownerIds" open="(" separator="," close=")">#{i}</foreach>
            </if>
            ORDER BY o.last_order_at NULLS FIRST
            </script>
            """)
    List<Map<String, Object>> selectSilentCustomers(@Param("silentBefore") LocalDateTime silentBefore,
                                                    @Param("ownerIds") List<Long> ownerIds);

    /** 分层分布（VA-2：各客户最新评分按 tier 计数；ownerIds=null 表示 ALL） */
    @Select("""
            <script>
            SELECT s.tier AS tier, COUNT(*) AS cnt
            FROM customer_score s
            JOIN (SELECT customer_id, MAX(calc_date) AS max_date FROM customer_score GROUP BY customer_id) l
              ON l.customer_id = s.customer_id AND l.max_date = s.calc_date
            JOIN crm_customer c ON c.id = s.customer_id
            WHERE c.deleted = 0
            <if test="ownerIds != null">
              AND c.owner_id IN
              <foreach item="i" collection="ownerIds" open="(" separator="," close=")">#{i}</foreach>
            </if>
            GROUP BY s.tier
            </script>
            """)
    List<Map<String, Object>> selectTierDistribution(@Param("ownerIds") List<Long> ownerIds);
}
