package com.crm.va.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户价值评分（VA-1/VA-2，V10 建表；每客户每日一条，历史即趋势数据）
 * 表无 deleted 列，独立实体不继承 BaseEntity
 */
@Data
@TableName("customer_score")
public class CustomerScore {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long customerId;
    /** 总分 0~100 */
    private BigDecimal score;
    /** HIGH_VALUE/POTENTIAL/TO_ACTIVATE/AT_RISK */
    private String tier;
    private BigDecimal dimFreq;
    private BigDecimal dimAmount;
    private BigDecimal dimActive;
    private BigDecimal dimRemittance;
    private BigDecimal dimFollowup;
    /** 1=手动调整过，自动重算跳过 */
    private Integer manual;
    private LocalDate calcDate;
}
