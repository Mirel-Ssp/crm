package com.crm.stat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易聚合预计算表（ST-1，V10 建表；PRD 实体 BusinessStat）
 * 三类行族：ALL(owner_id=0,item_id=0) / OWNER(按业务员) / ITEM(按标的)
 * stat_date 为周期代表日（日=当日/月=月初/季=季初/年=年初）
 */
@Data
@TableName("business_stat")
public class BusinessStat {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** DAY/MONTH/QUARTER/YEAR */
    private String statDim;
    private LocalDate statDate;
    private Long ownerId;
    private Long itemId;
    private Long orderCount;
    private BigDecimal orderAmount;
    /** 活跃客户数（有成交去重） */
    private Long customerCount;
    private BigDecimal remitAmount;
    /** remit_amount / order_amount */
    private BigDecimal arriveRate;
    private LocalDateTime createdAt;
}
