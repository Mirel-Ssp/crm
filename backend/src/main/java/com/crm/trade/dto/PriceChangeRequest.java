package com.crm.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** 调价请求（TB-2：变更留痕） */
@Data
public class PriceChangeRequest {

    @NotNull(message = "新价格不能为空")
    @Positive(message = "新价格必须大于 0")
    private BigDecimal newPrice;
}
