package com.crm.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具（INF-DV-03）
 * 双令牌：access（短效，携带 type=access）+ refresh（长效，type=refresh）
 * HMAC-SHA256；密钥必须 ≥32 字节
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.access-token-ttl-minutes:120}") long accessTtlMinutes,
                   @Value("${app.jwt.refresh-token-ttl-days:7}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = accessTtlMinutes * 60_000;
        this.refreshTtlMs = refreshTtlDays * 24 * 60 * 60_000;
    }

    public String generateAccess(Long uid, String username) {
        return generate(uid, username, TYPE_ACCESS, accessTtlMs);
    }

    public String generateRefresh(Long uid, String username) {
        return generate(uid, username, TYPE_REFRESH, refreshTtlMs);
    }

    private String generate(Long uid, String username, String type, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", uid)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名/过期；失败抛 ExpiredJwtException / JwtException */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** 是否有效且为指定类型 */
    public boolean isValid(String token, String expectedType) {
        try {
            Claims claims = parse(token);
            return expectedType.equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUid(Claims claims) {
        return claims.get("uid", Long.class);
    }
}
