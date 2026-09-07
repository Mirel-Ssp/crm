package com.crm.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 标的建档/编辑请求（TB-1；调价走独立接口留痕） */
@Data
public class ItemSaveRequest {

    @NotBlank(message = "标的代码不能为空")
    private String code;

    @NotBlank(message = "标的名称不能为空")
    private String name;

    @NotBlank(message = "标的类型不能为空")
    private String category;

    private String market;

    @NotNull(message = "参考价格不能为空")
    @Positive(message = "参考价格必须大于 0")
    private BigDecimal referencePrice;

    private String riskLevel;

    private BigDecimal feeRate;

    private BigDecimal minQuantity;

    private String remark;

    private Map<String, Object> attributes;
}
