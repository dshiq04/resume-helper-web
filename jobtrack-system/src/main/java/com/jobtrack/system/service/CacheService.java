package com.jobtrack.system.service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 共享配置缓存。
 */
public interface CacheService {

    String getString(String key);

    void putString(String key, String value, Duration ttl);

    void evict(String key);

    <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader);

    String mode();

    long epoch();

    void bumpEpoch();
}
