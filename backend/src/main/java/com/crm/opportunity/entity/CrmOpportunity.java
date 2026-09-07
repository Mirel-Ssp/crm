package com.crm.opportunity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机（CRM-O1~O4，V1 建表）
 * 阶段 1~5 开放漏斗；成交=stage 6 + status WON；丢单=stage 7 + status LOST
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity")
public class CrmOpportunity extends BaseEntity {

    private Long customerId;
    private String name;
    /** 1初步接触 2需求确认 3方案报价 4商务谈判 5成交准备；6/7 为终态冗余（字典 opportunity_stage） */
    private Integer stage;
    private BigDecimal amount;
    /** CNY / USD ...（CRM-O1 币种） */
    private String currency;
    private LocalDate expectedDate;
    /** 归属业务员（数据范围过滤键） */
    private Long ownerId;
    /** OPEN / WON / LOST */
    private String status;
    private String winReason;
    private String loseReason;
    private String remark;
}
