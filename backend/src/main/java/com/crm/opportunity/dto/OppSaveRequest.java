package com.crm.opportunity.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机新增/编辑请求体（CRM-O1：关联客户、金额+币种、预计成交日、阶段必填校验）
 */
@Data
public class OppSaveRequest {

    @NotNull(message = "关联客户不能为空")
    private Long customerId;

    @NotBlank(message = "商机名称不能为空")
    @Size(max = 128, message = "商机名称最长 128 字")
    private String name;

    /** 开放阶段 1~5（默认 1）；终态由成交/丢单接口写入 */
    private Integer stage;

    @NotNull(message = "预计金额不能为空")
    @DecimalMin(value = "0", message = "预计金额不能为负")
    private BigDecimal amount;

    @Pattern(regexp = "[A-Z]{3}", message = "币种须为 3 位大写字母（如 CNY/USD）")
    private String currency;

    private LocalDate expectedDate;

    @Size(max = 500, message = "备注最长 500 字")
    private String remark;
}
