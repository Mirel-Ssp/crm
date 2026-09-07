package com.crm.common.web.sensitive;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段脱敏标注（SYS-DV-03）
 * 字段级标注 + Jackson BeanSerializerModifier 全局生效，实体零侵入调用。
 * 策略：所有输出一律脱敏；按权限点明文的方案预留后置（设计文档未定，先保守）。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    SensitiveType value();
}
