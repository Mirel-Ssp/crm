package com.crm.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.trade.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    /** 行锁读取订单（核销并发串行化，REM-DV-02） */
    @Select("SELECT * FROM trade_order WHERE id = #{id} FOR UPDATE")
    TradeOrder selectForUpdate(@Param("id") Long id);
}
