package com.crm;

import com.crm.common.web.sensitive.SensitiveMasker;
import com.crm.common.web.sensitive.SensitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 单元测试：敏感字段脱敏（SYS-DV-03）
 */
class SensitiveMaskerTest {

    @Test
    @DisplayName("手机号 13812345678 → 138****5678")
    void maskPhone() {
        assertEquals("138****5678", SensitiveMasker.mask(SensitiveType.PHONE, "13812345678"));
    }

    @Test
    @DisplayName("过短手机号整段掩码")
    void maskShortPhone() {
        assertEquals("***", SensitiveMasker.mask(SensitiveType.PHONE, "12345"));
    }

    @Test
    @DisplayName("邮箱 ab@x.com → a***@x.com（保留域名）")
    void maskEmail() {
        assertEquals("a***@x.com", SensitiveMasker.mask(SensitiveType.EMAIL, "ab@x.com"));
    }

    @Test
    @DisplayName("无 @ 的邮箱串掩码首字符")
    void maskEmailWithoutAt() {
        assertEquals("a***", SensitiveMasker.mask(SensitiveType.EMAIL, "admin"));
    }

    @Test
    @DisplayName("null 与空串原样返回")
    void maskNullOrBlank() {
        assertNull(SensitiveMasker.mask(SensitiveType.PHONE, null));
        assertEquals("", SensitiveMasker.mask(SensitiveType.EMAIL, ""));
    }
}
