package com.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 跟进记录（CRM-F1/F2）：客户/线索/商机三类对象统一跟进流
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_followup")
public class CrmFollowup extends BaseEntity {

    /** CUSTOMER / LEAD / OPPORTUNITY */
    private String relType;
    private Long relId;
    private String content;
    /** PHONE / VISIT / WECHAT / EMAIL / OTHER */
    private String method;
    /** 下次跟进时间（工作台待办来源） */
    private LocalDateTime nextFollowupAt;
    /** DONE 已跟进 / TODO 待办 */
    private String status;
    private Long ownerId;
}
