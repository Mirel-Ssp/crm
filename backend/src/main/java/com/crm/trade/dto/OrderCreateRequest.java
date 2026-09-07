package com.crm.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** 订单创建请求（OD-1：金额/手续费服务端计算，前端价格可空默认参考价） */
@Data
public class OrderCreateRequest {

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotNull(message = "标的不能为空")
    private Long itemId;

    @NotBlank(message = "交易方向不能为空")
    private String direction;

    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于 0")
    private BigDecimal quantity;

    @Positive(message = "单价必须大于 0")
    private BigDecimal price;
}
