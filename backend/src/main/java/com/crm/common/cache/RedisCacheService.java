package com.crm.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Redis 分布式缓存实现（prod Profile，方案 B，批次6 接入）
 * - 值以 JSON（Jackson）序列化，get 按 type 反序列化恢复
 * - put 的 ttl 参数生效（与 Caffeine 全局 30m 策略区分）
 * - 反序列化失败视为未命中并清除脏数据（如缓存结构升级后的旧值）
 */
@Component
@Profile("prod")
public class RedisCacheService implements CacheService {

    /** CacheService 约定的业务 key 前缀（user:{uid}），clear 仅清理此前缀避免误伤同实例其他数据 */
    private static final String KEY_PREFIX = "user:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            evict(key);
            return null;
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cache serialize failed: " + key, e);
        }
    }

    @Override
    public void evict(String key) {
        redis.delete(key);
    }

    @Override
    public void clear() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
