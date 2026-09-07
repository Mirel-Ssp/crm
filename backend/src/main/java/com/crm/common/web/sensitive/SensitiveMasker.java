package com.crm.common.web.sensitive;

/**
 * 脱敏掩码工具（SYS-DV-03）
 */
public final class SensitiveMasker {

    private SensitiveMasker() {
    }

    public static String mask(SensitiveType type, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
        };
    }

    /** 13812345678 → 138****5678；过短串整段掩码 */
    static String maskPhone(String v) {
        if (v.length() <= 7) {
            return "***";
        }
        return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
    }

    /** ab@x.com → a***@x.com；无 @ 的整串掩码 */
    static String maskEmail(String v) {
        int at = v.indexOf('@');
        if (at <= 0) {
            return v.charAt(0) + "***";
        }
        return v.charAt(0) + "***" + v.substring(at);
    }
}
