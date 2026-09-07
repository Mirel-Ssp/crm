package com.crm.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Caffeine 进程内缓存实现（dev/默认 Profile，方案 A）
 * - 全局过期策略 maximumSize=10000, expireAfterWrite=30m（与 application-dev.yml 对应）
 * - put 的 ttl 参数本地实现忽略，仅为 Redis(prod) 实现兼容保留
 */
@Component
@Profile("!prod")
public class CaffeineCacheService implements CacheService {

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) cache.getIfPresent(key);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        cache.put(key, value);
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }
}
