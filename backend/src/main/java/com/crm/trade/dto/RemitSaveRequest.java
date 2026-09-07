package com.crm.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 汇款登记请求（RM-1） */
@Data
public class RemitSaveRequest {

    @NotBlank(message = "汇款单号不能为空")
    private String remitNo;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotNull(message = "汇款金额不能为空")
    @Positive(message = "汇款金额必须大于 0")
    private BigDecimal amount;

    private String currency;

    @NotNull(message = "汇款时间不能为空")
    private LocalDateTime remittedAt;

    private String voucherKey;

    private String remark;
}
