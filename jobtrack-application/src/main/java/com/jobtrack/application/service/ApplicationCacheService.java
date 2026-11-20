package com.jobtrack.application.service;

import com.jobtrack.common.context.TenantContext;
import com.jobtrack.system.config.RedisSettings;
import com.jobtrack.system.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 投递数据的读缓存。
 *
 * <p>列表、统计与年度选项默认先读 Redis；Redis 未启用或不可用时退回进程内缓存，
 * 缓存未命中才查询数据库。每次写操作都会递增“数据版本”，旧缓存自然失效，
 * 无需遍历删除 key。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationCacheService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final CacheService cacheService;
    private final RedisSettings redisSettings;
    private final StringRedisTemplate redisTemplate;
    private final Map<Long, AtomicLong> localVersions = new ConcurrentHashMap<>();

    public <T> T cached(String suffix, Class<T> type, Supplier<T> loader) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return loader.get();
        }
        String key = "tenant:" + tenantId + ":applications:v" + version(tenantId)
                + ":e" + cacheService.epoch() + ":" + suffix;
        return cacheService.get(key, type, TTL, loader);
    }

    /**
     * 投递数据发生变化时调用，使该账号的旧缓存失效。
     */
    public void invalidate() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }
        localVersions.computeIfAbsent(tenantId, key -> new AtomicLong()).incrementAndGet();
        if (useRedis()) {
            try {
                redisTemplate.opsForValue().increment(versionKey(tenantId));
            } catch (Exception exception) {
                log.warn("Redis 数据版本递增失败，使用本地版本: {}", exception.getMessage());
            }
        }
    }

    public long version(long tenantId) {
        if (useRedis()) {
            try {
                String key = versionKey(tenantId);
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    redisTemplate.opsForValue().setIfAbsent(key, "0");
                    return 0;
                }
                return Long.parseLong(value);
            } catch (Exception exception) {
                log.warn("Redis 数据版本读取失败，使用本地版本: {}", exception.getMessage());
            }
        }
        return localVersions.computeIfAbsent(tenantId, key -> new AtomicLong()).get();
    }

    private boolean useRedis() {
        return redisSettings.isEnabled() && "redis".equals(cacheService.mode());
    }

    private String versionKey(long tenantId) {
        return redisSettings.getKeyPrefix() + "tenant:" + tenantId + ":applications:version";
    }
}
