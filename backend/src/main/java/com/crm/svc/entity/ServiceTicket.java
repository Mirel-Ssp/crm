package com.crm.svc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 服务工单（CRM-S1/S2，V1 预建表）
 * 状态机契约：OPEN(待处理) → PROCESSING(处理中) → RESOLVED(已解决) → CLOSED(已关闭)
 * 允许 OPEN 直接 RESOLVED（快速办结）；CLOSED 为终态
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_ticket")
public class ServiceTicket extends BaseEntity {

    /** 工单号 ST+时间戳+随机 */
    private String no;
    private Long customerId;
    private Long contactId;
    /** 字典 ticket_type：CONSULT/COMPLAINT/AFTER_SALE/OTHER */
    private String type;
    /** LOW/MEDIUM/HIGH/URGENT（V1 CHECK 约束） */
    private String priority;
    private String title;
    private String content;
    /** 处理人（数据范围过滤键；NULL=待指派） */
    private Long assigneeId;
    private String status;
    /** SLA 到期时间 */
    private LocalDateTime slaDueAt;
    private LocalDateTime resolvedAt;
    /** CRM-S2 满意度 1~5（回访后填写） */
    private Integer satisfaction;
    private String remark;
}
