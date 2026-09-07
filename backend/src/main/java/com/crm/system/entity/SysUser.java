package com.crm.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.entity.BaseEntity;
import com.crm.common.web.sensitive.Sensitive;
import com.crm.common.web.sensitive.SensitiveType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户（CRM-M1）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private Long orgId;
    private String username;
    /** bcrypt 哈希，任何接口不得外泄 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;
    private String realName;
    /** SYS-DV-03：输出一律脱敏（编辑接口 blank=不变更，防掩码回写） */
    @Sensitive(SensitiveType.EMAIL)
    private String email;
    @Sensitive(SensitiveType.PHONE)
    private String phone;
    /** ACTIVE / LOCKED / DISABLED */
    private String status;
    private LocalDateTime lastLoginAt;
    /** 1=首登/被重置后强制改密（V2 遗留问题4） */
    private Integer mustChangePassword;
}
