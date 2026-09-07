package com.crm.stat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.stat.entity.BusinessStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessStatMapper extends BaseMapper<BusinessStat> {

    /**
     * 订单原始聚合行（rebuild 数据源）：unit ∈ day/month/quarter/year（服务端校验）
     * 两类行族 UNION：owner 行（item_id=0）+ item 行（owner_id=0）+ ALL 全量汇总行
     * buckets=null 聚合全历史；否则仅聚合命中周期桶（批次6 增量重建用）
     */
    @Select("""
            <script>
            SELECT date_trunc(#{unit}, created_at)::date AS "statDate", owner_id AS "ownerId", 0 AS "itemId",
                   COUNT(*) AS "orderCount", SUM(total_amount) AS "orderAmount",
                   COUNT(DISTINCT customer_id) AS "customerCount"
            FROM trade_order
            WHERE deleted = 0 AND status NOT IN ('CANCELLED', 'PARTIAL_CANCELLED')
              AND created_at &gt;= #{from}
              <if test="buckets != null">AND date_trunc(#{unit}, created_at)::date IN
                <foreach item="b" collection="buckets" open="(" separator="," close=")">#{b}</foreach>
              </if>
            GROUP BY 1, 2, 3
            UNION ALL
            SELECT date_trunc(#{unit}, created_at)::date AS "statDate", 0 AS "ownerId", item_id AS "itemId",
                   COUNT(*) AS "orderCount", SUM(total_amount) AS "orderAmount",
                   COUNT(DISTINCT customer_id) AS "customerCount"
            FROM trade_order
            WHERE deleted = 0 AND status NOT IN ('CANCELLED', 'PARTIAL_CANCELLED')
              AND created_at &gt;= #{from}
              <if test="buckets != null">AND date_trunc(#{unit}, created_at)::date IN
                <foreach item="b" collection="buckets" open="(" separator="," close=")">#{b}</foreach>
              </if>
            GROUP BY 1, 2, 3
            UNION ALL
            SELECT date_trunc(#{unit}, created_at)::date AS "statDate", 0 AS "ownerId", 0 AS "itemId",
                   COUNT(*) AS "orderCount", SUM(total_amount) AS "orderAmount",
                   COUNT(DISTINCT customer_id) AS "customerCount"
            FROM trade_order
            WHERE deleted = 0 AND status NOT IN ('CANCELLED', 'PARTIAL_CANCELLED')
              AND created_at &gt;= #{from}
              <if test="buckets != null">AND date_trunc(#{unit}, created_at)::date IN
                <foreach item="b" collection="buckets" open="(" separator="," close=")">#{b}</foreach>
              </if>
            GROUP BY 1
            </script>
            """)
    List<Map<String, Object>> rawOrderRows(@Param("unit") String unit, @Param("from") LocalDateTime from,
                                           @Param("buckets") List<LocalDate> buckets);

    /** 已确认到账原始聚合（按 owner 维度；item 维度不可直接归属，仅入 owner/ALL 行）；buckets 过滤口径同上 */
    @Select("""
            <script>
            SELECT date_trunc(#{unit}, confirmed_at)::date AS "statDate", owner_id AS "ownerId",
                   SUM(amount) AS "remitAmount"
            FROM trade_remittance
            WHERE deleted = 0 AND status IN ('CONFIRMED', 'PARTIALLY_WRITTEN_OFF', 'WRITTEN_OFF')
              AND confirmed_at &gt;= #{from}
              <if test="buckets != null">AND date_trunc(#{unit}, confirmed_at)::date IN
                <foreach item="b" collection="buckets" open="(" separator="," close=")">#{b}</foreach>
              </if>
            GROUP BY 1, 2
            </script>
            """)
    List<Map<String, Object>> rawRemitRows(@Param("unit") String unit, @Param("from") LocalDateTime from,
                                           @Param("buckets") List<LocalDate> buckets);

    /** 增量重建：since 之后有变动（创建/更新，含取消）的订单所属周期桶 */
    @Select("""
            SELECT DISTINCT date_trunc(#{unit}, created_at)::date AS "bucket"
            FROM trade_order
            WHERE deleted = 0 AND (created_at >= #{since} OR updated_at >= #{since})
            """)
    List<Map<String, Object>> affectedOrderBuckets(@Param("unit") String unit, @Param("since") LocalDateTime since);

    /** 增量重建：since 之后有变动的到账记录所属周期桶（confirmed_at 归桶，状态回退经 updated_at 捕获） */
    @Select("""
            SELECT DISTINCT date_trunc(#{unit}, confirmed_at)::date AS "bucket"
            FROM trade_remittance
            WHERE deleted = 0 AND (confirmed_at >= #{since} OR updated_at >= #{since})
            """)
    List<Map<String, Object>> affectedRemitBuckets(@Param("unit") String unit, @Param("since") LocalDateTime since);

    /** 交易明细（ST-3：订单 + 客户/标的/核销额联表，多条件筛选） */
    @Select("""
            <script>
            SELECT o.id, o.order_no AS "orderNo", o.customer_id AS "customerId", c.name AS "customerName",
                   o.item_id AS "itemId", i.name AS "itemName", o.direction, o.quantity, o.price,
                   o.amount, o.fee_amount AS "feeAmount", o.total_amount AS "totalAmount", o.status,
                   COALESCE(s.settled, 0) AS "settledAmount", o.owner_id AS "ownerId",
                   u.real_name AS "ownerName", o.created_at AS "createdAt"
            FROM trade_order o
            JOIN crm_customer c ON c.id = o.customer_id
            JOIN trade_item i ON i.id = o.item_id
            JOIN sys_user u ON u.id = o.owner_id
            LEFT JOIN (SELECT order_id, SUM(amount) AS settled FROM trade_order_settlement
                       WHERE deleted = 0 GROUP BY order_id) s ON s.order_id = o.id
            WHERE o.deleted = 0
            <if test="customerId != null"> AND o.customer_id = #{customerId}</if>
            <if test="itemId != null"> AND o.item_id = #{itemId}</if>
            <if test="status != null and status != ''"> AND o.status = #{status}</if>
            <if test="direction != null and direction != ''"> AND o.direction = #{direction}</if>
            <if test="from != null"> AND o.created_at &gt;= #{from}</if>
            <if test="to != null"> AND o.created_at &lt; #{to}</if>
            <if test="ownerIds != null">
              AND o.owner_id IN
              <foreach item="i" collection="ownerIds" open="(" separator="," close=")">#{i}</foreach>
            </if>
            ORDER BY o.created_at DESC
            </script>
            """)
    IPage<Map<String, Object>> selectDetail(Page<Map<String, Object>> page,
                                            @Param("customerId") Long customerId,
                                            @Param("itemId") Long itemId,
                                            @Param("status") String status,
                                            @Param("direction") String direction,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("ownerIds") List<Long> ownerIds);
}
