package com.crm.common.web.sensitive;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * 扫描带 @Sensitive 的 String 属性并替换为掩码序列化器（SYS-DV-03）
 * 经 SimpleModule 注册，Spring Boot 自动并入主 ObjectMapper（不影响 JavaTimeModule 等）
 */
public class SensitiveSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            Sensitive ann = writer.getAnnotation(Sensitive.class);
            if (ann != null && String.class.equals(writer.getType().getRawClass())) {
                writer.assignSerializer(new SensitiveStringSerializer(ann.value()));
            }
        }
        return beanProperties;
    }
}
