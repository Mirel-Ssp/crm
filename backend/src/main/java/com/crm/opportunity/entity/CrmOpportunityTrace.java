package com.crm.opportunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商机阶段流转留痕（CRM-O2：推进/回退均留痕）
 */
@Data
@TableName("crm_opportunity_trace")
public class CrmOpportunityTrace implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long oppId;
    private Integer fromStage;
    private Integer toStage;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
