package com.crm.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 报价单创建/编辑请求（CRM-R1）
 */
@Data
public class QuoteSaveRequest {

    @NotBlank(message = "报价标题不能为空")
    private String title;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 可选：关联商机 */
    private Long opportunityId;

    /** 折扣率（0.01~1，默认 1 不打折） */
    @DecimalMax(value = "1", message = "折扣率不能大于1")
    @DecimalMin(value = "0.01", message = "折扣率不能小于0.01")
    private BigDecimal discountRate = BigDecimal.ONE;

    /** 税率（0~1） */
    @DecimalMax(value = "1", message = "税率不能大于1")
    @DecimalMin(value = "0", message = "税率不能小于0")
    private BigDecimal taxRate = BigDecimal.ZERO;

    private LocalDate validUntil;

    private String remark;

    @NotEmpty(message = "报价明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        /** 可选：关联交易标的（带出名称默认价） */
        private Long itemId;
        @NotBlank(message = "明细名称不能为空")
        private String name;
        private String spec;
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0.0001", message = "数量必须大于0")
        private BigDecimal quantity;
        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0", message = "单价不能为负")
        private BigDecimal price;
    }
}
