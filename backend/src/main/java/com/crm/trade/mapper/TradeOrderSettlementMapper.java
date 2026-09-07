package com.crm.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.trade.entity.TradeOrderSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface TradeOrderSettlementMapper extends BaseMapper<TradeOrderSettlement> {

    /** 订单累计核销金额（防超核口径） */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM trade_order_settlement WHERE order_id = #{orderId} AND deleted = 0")
    BigDecimal sumPaidByOrderId(@Param("orderId") Long orderId);
}
