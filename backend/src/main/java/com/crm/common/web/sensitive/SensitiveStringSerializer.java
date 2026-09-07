package com.crm.common.web.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * 敏感字符串序列化器：输出前掩码
 */
public class SensitiveStringSerializer extends StdSerializer<Object> {

    private final SensitiveType type;

    protected SensitiveStringSerializer(SensitiveType type) {
        super(Object.class);
        this.type = type;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(SensitiveMasker.mask(type, value.toString()));
        }
    }
}
