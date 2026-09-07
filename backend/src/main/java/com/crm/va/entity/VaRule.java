package com.crm.va.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VA 权重/阈值配置（VA-1 权重可配 + VA-5 沉默阈值）
 */
@Data
@TableName("va_rule")
public class VaRule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String ruleKey;
    private BigDecimal ruleValue;
    private String remark;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
