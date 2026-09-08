package com.crm.common.config;

import com.crm.common.web.sensitive.SensitiveSerializerModifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Jackson 定制（SYS-DV-03 脱敏 + B4-P9 日期兼容 + 雪花ID精度保护）
 * Module Bean 由 Spring Boot 自动注册进主 ObjectMapper（不破坏 JavaTimeModule 自动装配）
 * LocalDateTime 统一序列化为 'yyyy-MM-dd HH:mm:ss'（反序列化保留 ISO 兼容，前端 value-format 含 T 不受影响）
 * Long 精度保护：雪花 ID（>2^53）序列化为字符串，避免前端 JavaScript Number 精度丢失
 *   （浏览器 Number.MAX_SAFE_INTEGER=9007199254740991，19 位雪花 ID 末尾被四舍五入，
 *    导致前端回传的 ID 与数据库不一致、出现“客户不存在”）；小数字（总数/分页/种子ID）仍为数字。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Module sensitiveModule() {
        SimpleModule module = new SimpleModule("sensitive-mask");
        module.setSerializerModifier(new SensitiveSerializerModifier());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
        // 雪花 ID 精度保护：Long（对象）与 long（基本类型）共用同一序列化器
        LongSafeSerializer longSerializer = new LongSafeSerializer();
        module.addSerializer(Long.class, longSerializer);
        module.addSerializer(Long.TYPE, longSerializer);
        return module;
    }

    /** LocalDateTime → 'yyyy-MM-dd HH:mm:ss'（批次3遗留：ISO 'T' 格式前端需逐处 replace） */
    static class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.format(DATE_TIME_FORMATTER));
        }
    }

    /**
     * LocalDateTime 反序列化：同时接受 'yyyy-MM-dd HH:mm:ss'（空格，与本系统序列化输出对称）
     * 和 ISO 'yyyy-MM-ddTHH:mm:ss'（前端 el-date-picker value-format 输出）两种格式，
     * 保证“接口输出的值回传可被接受”，避免客户端回显值导致解析失败。
     */
    static class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt)
                throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            text = text.trim();
            try {
                // 空格格式（本系统序列化输出）
                return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignore) {
                // 回退 ISO（含 'T'，可能带毫秒）
                return LocalDateTime.parse(text, ISO);
            }
        }
    }

    /**
     * Long 序列化器：超出 JS 安全整数范围（±(2^53-1)）的值写成字符串，其余写数字。
     * 前端对 ID 仅作字符串拼接（/customers/${id}）与相等比较，字符串不影响使用；
     * 字符串 ID 回传后端时 Jackson/Spring 可自动转回 Long。
     */
    static class LongSafeSerializer extends JsonSerializer<Long> {
        private static final long JS_MAX_SAFE = 9007199254740991L;  // 2^53 - 1
        private static final long JS_MIN_SAFE = -9007199254740991L;

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else if (value > JS_MAX_SAFE || value < JS_MIN_SAFE) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value);
            }
        }
    }
}
