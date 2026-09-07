package com.crm.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志（SYS-DV-04）：@AuditLog AOP 旁路写入，不阻断业务
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    /** 如 lead:claim / access:denied */
    private String action;
    private String targetType;
    private Long targetId;
    /** JSONB，写入时传 JSON 字符串（数据源需 stringtype=unspecified） */
    private String detail;
    private String ip;
    /** DB 默认 CURRENT_TIMESTAMP，插入时留空 */
    private LocalDateTime createdAt;
}
