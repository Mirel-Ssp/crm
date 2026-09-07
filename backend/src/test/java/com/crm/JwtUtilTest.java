package com.crm;

import com.crm.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 单元测试（纯逻辑，无 Spring 上下文）
 */
class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "unit-test-secret-key-with-at-least-32-bytes!!", 120, 7);

    @Test
    @DisplayName("生成并解析 access 令牌：uid/username/type 正确")
    void accessRoundTrip() {
        String token = jwtUtil.generateAccess(42L, "admin");
        Claims claims = jwtUtil.parse(token);
        assertThat(jwtUtil.getUid(claims)).isEqualTo(42L);
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("type", String.class)).isEqualTo(JwtUtil.TYPE_ACCESS);
        assertThat(jwtUtil.isValid(token, JwtUtil.TYPE_ACCESS)).isTrue();
    }

    @Test
    @DisplayName("refresh 令牌不能当作 access 使用（类型隔离）")
    void typeIsolation() {
        String refresh = jwtUtil.generateRefresh(1L, "u1");
        assertThat(jwtUtil.isValid(refresh, JwtUtil.TYPE_ACCESS)).isFalse();
        assertThat(jwtUtil.isValid(refresh, JwtUtil.TYPE_REFRESH)).isTrue();
    }

    @Test
    @DisplayName("篡改令牌解析失败")
    void tamperedTokenRejected() {
        String token = jwtUtil.generateAccess(1L, "u1");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThat(jwtUtil.isValid(tampered, JwtUtil.TYPE_ACCESS)).isFalse();
        assertThatThrownBy(() -> jwtUtil.parse(tampered)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("密钥不足 32 字节应抛异常（防弱密钥）")
    void weakSecretRejected() {
        assertThatThrownBy(() -> new JwtUtil("short-secret", 1, 1))
                .isInstanceOf(Exception.class);
    }
}
