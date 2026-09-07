package com.crm.common.config;

import com.crm.common.web.sensitive.SensitiveSerializerModifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 定制（SYS-DV-03 脱敏 + B4-P9 日期兼容）
 * Module Bean 由 Spring Boot 自动注册进主 ObjectMapper（不破坏 JavaTimeModule 自动装配）
 * LocalDateTime 统一序列化为 'yyyy-MM-dd HH:mm:ss'（反序列化保留 ISO 兼容，前端 value-format 含 T 不受影响）
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Module sensitiveModule() {
        SimpleModule module = new SimpleModule("sensitive-mask");
        module.setSerializerModifier(new SensitiveSerializerModifier());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        return module;
    }

    /** LocalDateTime → 'yyyy-MM-dd HH:mm:ss'（批次3遗留：ISO 'T' 格式前端需逐处 replace） */
    static class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.format(DATE_TIME_FORMATTER));
        }
    }
}
