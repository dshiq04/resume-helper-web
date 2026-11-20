package com.jobtrack.system.service;

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
public class CacheService {

    private final RedisSettings redisSettings;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, MemoryEntry> memory = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable;

    @PostConstruct
    void init() {
        redisAvailable = redisSettings.isEnabled() && redisTemplate != null;
        log.info("共享配置缓存: {}", redisAvailable ? "Redis" : "进程内");
    }

    public String getString(String key) {
        if (useRedis()) {
            try {
                String value = redisTemplate.opsForValue().get(redisKey(key));
                if (value != null) {
                    return value;
                }
            } catch (Exception exception) {
                disableRedis(exception);
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
                return;
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
        memory.put(key, new MemoryEntry(value, Instant.now().plus(ttl)));
    }

    public void evict(String key) {
        if (useRedis()) {
            try {
                redisTemplate.delete(redisKey(key));
            } catch (Exception exception) {
                disableRedis(exception);
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

    private boolean useRedis() {
        return redisAvailable && redisTemplate != null;
    }

    private void disableRedis(Exception exception) {
        if (redisAvailable) {
            log.warn("Redis 不可用，共享配置临时切换到进程内缓存: {}", exception.getMessage());
            redisAvailable = false;
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
