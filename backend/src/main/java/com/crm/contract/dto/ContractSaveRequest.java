package com.crm.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同创建/编辑请求（CRM-R3）
 */
@Data
public class ContractSaveRequest {

    @NotBlank(message = "合同标题不能为空")
    private String title;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 可选：关联商机 / 订单 / 报价单 */
    private Long opportunityId;
    private Long orderId;
    private Long quoteId;

    @NotNull(message = "合同金额不能为空")
    @DecimalMin(value = "0", message = "合同金额不能为负")
    private BigDecimal amount;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 到期预警提前天数（默认 30） */
    private Integer reminderDays;

    private String attachmentUrl;

    private String remark;
}
