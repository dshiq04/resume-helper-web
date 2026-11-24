package com.jobtrack.system.service.impl;

import com.jobtrack.system.service.CacheService;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.RedisSettings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 共享配置缓存。
 *
 * <p>启用 Redis 时，公共配置与账号偏好从 Redis 读取，多实例部署也能共享，
 * 减少数据库查询；未启用或 Redis 临时不可用时自动退回进程内缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private static final long REDIS_RETRY_BASE_MS = 5_000;
    private static final long REDIS_RETRY_MAX_MS = 60_000;

    private final RedisSettings redisSettings;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, MemoryEntry> memory = new ConcurrentHashMap<>();
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicLong redisFailures = new AtomicLong();
    private volatile boolean redisEnabled;
    private volatile long redisRetryAt;

    @PostConstruct
    void init() {
        redisEnabled = redisSettings.isEnabled() && redisTemplate != null;
        log.info("共享配置缓存: {}", redisEnabled ? "Redis" : "进程内");
    }

    public String getString(String key) {
        if (useRedis()) {
            try {
                String value = redisTemplate.opsForValue().get(redisKey(key));
                recordRedisSuccess();
                if (value != null) {
                    return value;
                }
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        MemoryEntry entry = memory.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            memory.remove(key);
            return null;
        }
        return entry.value;
    }

    public void putString(String key, String value, Duration ttl) {
        if (value == null) {
            return;
        }
        if (useRedis()) {
            try {
                redisTemplate.opsForValue().set(redisKey(key), value, ttl);
                recordRedisSuccess();
                return;
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        memory.put(key, new MemoryEntry(value, Instant.now().plus(ttl)));
    }

    public void evict(String key) {
        if (useRedis()) {
            try {
                redisTemplate.delete(redisKey(key));
                recordRedisSuccess();
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        memory.remove(key);
    }

    public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        String cached = getString(key);
        if (cached != null) {
            try {
                return JsonUtils.fromJson(cached, type);
            } catch (IllegalArgumentException exception) {
                evict(key);
            }
        }
        T value = loader.get();
        if (value != null) {
            putString(key, JsonUtils.toJson(value), ttl);
        }
        return value;
    }

    public String mode() {
        return useRedis() ? "redis" : "memory";
    }

    /**
     * 全局缓存纪元。切换数据库后递增，使所有业务缓存键立即失效。
     */
    public long epoch() {
        return epoch.get();
    }

    public void bumpEpoch() {
        epoch.incrementAndGet();
    }

    private boolean useRedis() {
        return redisEnabled && redisTemplate != null
                && System.currentTimeMillis() >= redisRetryAt;
    }

    /** 故障后冷却并自动重试，避免一次异常永久降级为进程内缓存。 */
    private void recordRedisFailure(Exception exception) {
        long failures = redisFailures.incrementAndGet();
        long delay = Math.min(REDIS_RETRY_MAX_MS,
                REDIS_RETRY_BASE_MS << Math.min(failures - 1, 4));
        redisRetryAt = System.currentTimeMillis() + delay;
        log.warn("Redis 访问失败，{} ms 后重试；共享配置本次使用进程内缓存: {}",
                delay, exception.getMessage());
    }

    private void recordRedisSuccess() {
        redisRetryAt = 0;
        if (redisFailures.getAndSet(0) > 0) {
            log.info("Redis 已恢复，共享配置重新写入 Redis");
        }
    }

    private String redisKey(String key) {
        return redisSettings.getKeyPrefix() + key;
    }

    private static final class MemoryEntry {
        private final String value;
        private final Instant expiresAt;

        private MemoryEntry(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
