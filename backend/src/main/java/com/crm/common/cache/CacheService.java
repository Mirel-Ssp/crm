package com.crm.common.cache;

import java.time.Duration;

/**
 * 缓存服务接口（INF-DS-02 §5.1 定形，批次2 T2 落地）
 * dev=Caffeine 进程内实现；prod 由 Redis 实现替换（业务代码零改动）。
 * key 约定：user:{uid} 用户态（角色/权限/数据范围）；权限变更时必须 evict。
 */
public interface CacheService {

    /** 取缓存值，未命中返回 null */
    <T> T get(String key, Class<T> type);

    /** 写缓存（Caffeine 实现使用全局 expireAfterWrite，ttl 参数在 prod Redis 实现生效） */
    void put(String key, Object value, Duration ttl);

    /** 逐出单个 key */
    void evict(String key);

    /** 清空全部（测试隔离与运维用） */
    void clear();
}
