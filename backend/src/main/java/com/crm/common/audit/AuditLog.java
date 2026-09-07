package com.crm.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解（SYS-DV-04，SYS-DS-01「越权与关键写操作全留痕」）
 * 标注在 Controller 写方法上，AOP 成功返回后旁路写 sys_audit_log
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 动作编码，如 lead:claim / user:create */
    String action();

    /** 操作对象类型，如 LEAD / USER / ORG */
    String targetType() default "";

    /** 目标 id 的 SpEL（如 "#id"），求值失败记 null */
    String targetId() default "";
}
