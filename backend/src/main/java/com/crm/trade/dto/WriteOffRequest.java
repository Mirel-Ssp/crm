package com.crm.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** 核销请求（RM-4：汇款 ↔ 订单 多对多） */
@Data
public class WriteOffRequest {

    @NotNull(message = "订单不能为空")
    private Long orderId;

    @NotNull(message = "核销金额不能为空")
    @Positive(message = "核销金额必须大于 0")
    private BigDecimal amount;
}
