package com.jobtrack.system.security;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.RedisSettings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在线会话存储。
 *
 * <p>Redis 是可选项：配置项 redis.enabled=false 时使用进程内 Map；
 * 打开后会话与在线人数写入 Redis。如果 Redis 临时不可用，会自动退回内存存储，
 * 保证登录功能不被基础设施拖垮。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisSettings redisSettings;
    private final StringRedisTemplate redisTemplate;

    private static final long REDIS_RETRY_BASE_MS = 5_000;
    private static final long REDIS_RETRY_MAX_MS = 60_000;

    private final Map<String, MemorySession> memory = new ConcurrentHashMap<>();
    private final AtomicLong redisFailures = new AtomicLong();
    /** Redis 是否在配置中启用；运行期不再永久关闭，只做冷却重试。 */
    private volatile boolean redisEnabled;
    private volatile long redisRetryAt;

    @PostConstruct
    void init() {
        redisEnabled = redisSettings.isEnabled() && redisTemplate != null;
        log.info("在线状态存储: {}", redisEnabled ? "Redis" : "进程内");
    }

    public String create(SessionInfo info, Duration ttl) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        save(sid, info, ttl);
        return sid;
    }

    public void save(String sid, SessionInfo info, Duration ttl) {
        // 始终保留一份进程内副本：Redis 短暂异常时，本实例已登录用户不会立刻掉线。
        MemorySession session = new MemorySession(info, Instant.now().plus(ttl), false);
        memory.put(sid, session);
        if (useRedis()) {
            try {
                writeRedis(sid, info, ttl);
                session.redisBacked = true;
                recordRedisSuccess();
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
    }

    /**
     * 读取会话并顺延有效期；不存在或已过期返回 null。
     */
    public SessionInfo resolve(String sid, Duration ttl) {
        if (sid == null || sid.isBlank()) {
            return null;
        }
        if (useRedis()) {
            try {
                SessionInfo info = readRedis(sid);
                recordRedisSuccess();
                if (info != null) {
                    touchRedis(sid, info, ttl);
                    memory.put(sid, new MemorySession(info, Instant.now().plus(ttl), true));
                    return info;
                }
                // Redis 是权威来源：已有会话被踢出/退出后不能在内存中复活。
                // 只有“故障期间只写入内存”的会话才回填 Redis。
                MemorySession local = memory.get(sid);
                if (local != null && local.expiresAt.isBefore(Instant.now())) {
                    memory.remove(sid);
                    local = null;
                }
                if (local != null && !local.redisBacked) {
                    try {
                        writeRedis(sid, local.info, ttl);
                        local.redisBacked = true;
                        local.expiresAt = Instant.now().plus(ttl);
                        return local.info;
                    } catch (Exception exception) {
                        recordRedisFailure(exception);
                    }
                } else if (local != null) {
                    memory.remove(sid);
                }
                return null;
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        return resolveMemory(sid, ttl);
    }

    private SessionInfo resolveMemory(String sid, Duration ttl) {
        MemorySession session = memory.get(sid);
        if (session == null) {
            return null;
        }
        if (session.expiresAt.isBefore(Instant.now())) {
            memory.remove(sid);
            return null;
        }
        session.expiresAt = Instant.now().plus(ttl);
        return session.info;
    }

    public void delete(String sid) {
        if (sid == null || sid.isBlank()) {
            return;
        }
        // 内存副本必须先清理，避免 Redis 异常时旧会话在内存中复活。
        memory.remove(sid);
        if (useRedis()) {
            try {
                SessionInfo info = readRedis(sid);
                redisTemplate.delete(sessionKey(sid));
                if (info != null) {
                    String userKey = userKey(info.getUserId());
                    redisTemplate.opsForSet().remove(userKey, sid);
                    Long size = redisTemplate.opsForSet().size(userKey);
                    if (size == null || size == 0) {
                        redisTemplate.delete(userKey);
                        redisTemplate.opsForZSet().remove(tenantKey(info.getTenantId()),
                                String.valueOf(info.getUserId()));
                    }
                }
                recordRedisSuccess();
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
    }

    /**
     * 修改密码后让该账号的所有会话失效。
     */
    public void deleteByUser(Long userId, Long tenantId) {
        if (userId == null) {
            return;
        }
        if (useRedis()) {
            try {
                String userKey = userKey(userId);
                Set<String> sessions = redisTemplate.opsForSet().members(userKey);
                if (sessions != null) {
                    for (String sid : sessions) {
                        redisTemplate.delete(sessionKey(sid));
                    }
                }
                redisTemplate.delete(userKey);
                if (tenantId != null) {
                    redisTemplate.opsForZSet().remove(tenantKey(tenantId), String.valueOf(userId));
                }
                recordRedisSuccess();
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        memory.entrySet().removeIf(entry -> userId.equals(entry.getValue().info.getUserId()));
    }

    /**
     * 列出所有在线会话，供管理员「在线用户」页使用。
     * 该方法不按租户过滤，管理员可以看到全部工作区的会话。
     */
    public List<OnlineSession> listSessions() {
        Map<String, OnlineSession> merged = new java.util.LinkedHashMap<>();
        boolean redisRead = false;
        if (useRedis()) {
            String prefix = redisSettings.getKeyPrefix() + "session:";
            try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions()
                    .match(prefix + "*").count(500).build())) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    String value = redisTemplate.opsForValue().get(key);
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    try {
                        SessionInfo info = JsonUtils.fromJson(value, SessionInfo.class);
                        Long ttl = redisTemplate.getExpire(key);
                        String sid = key.substring(prefix.length());
                        merged.put(sid, new OnlineSession(sid, info,
                                ttl == null ? 0 : Math.max(0, ttl)));
                    } catch (IllegalArgumentException exception) {
                        redisTemplate.delete(key);
                    }
                }
                recordRedisSuccess();
                redisRead = true;
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        Instant now = Instant.now();
        boolean redisAuthoritative = redisRead;
        memory.forEach((sid, session) -> {
            if (session.expiresAt.isAfter(now)) {
                // Redis 可读时只补充异常期间尚未写入 Redis 的会话，避免已踢出的会话重新出现。
                if (!redisAuthoritative || !session.redisBacked) {
                    merged.putIfAbsent(sid, new OnlineSession(sid, session.info,
                            Duration.between(now, session.expiresAt).toSeconds()));
                }
            } else {
                memory.remove(sid);
            }
        });
        return new ArrayList<>(merged.values());
    }

    public long countOnline(Long tenantId) {
        if (tenantId == null) {
            return 0;
        }
        if (useRedis()) {
            try {
                String key = tenantKey(tenantId);
                redisTemplate.opsForZSet().removeRangeByScore(key, 0, System.currentTimeMillis());
                Long count = redisTemplate.opsForZSet().zCard(key);
                recordRedisSuccess();
                return count == null ? 0 : count;
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        Instant now = Instant.now();
        return memory.values().stream()
                .filter(session -> session.expiresAt.isAfter(now))
                .filter(session -> tenantId.equals(session.info.getTenantId()))
                .map(session -> session.info.getUserId())
                .distinct()
                .count();
    }

    /**
     * 当前使用的存储类型，前端设置页展示。
     */
    public String mode() {
        return useRedis() ? "redis" : "memory";
    }

    /**
     * Redis 因异常进入冷却期时为 true。此时无法确认会话是否仍然有效，
     * 调用方应返回 503 而不是把用户误判为“未登录”。
     */
    public boolean degraded() {
        return redisEnabled && redisTemplate != null && !useRedis();
    }

    private boolean useRedis() {
        return redisEnabled && redisTemplate != null
                && System.currentTimeMillis() >= redisRetryAt;
    }

    /**
     * Redis 故障后只进入短暂冷却，带指数退避自动重试；不再永久关闭，
     * 也不丢弃已经写入 Redis 的会话。冷却期间读写回退到进程内副本。
     */
    private void recordRedisFailure(Exception exception) {
        long failures = redisFailures.incrementAndGet();
        long delay = Math.min(REDIS_RETRY_MAX_MS,
                REDIS_RETRY_BASE_MS << Math.min(failures - 1, 4));
        redisRetryAt = System.currentTimeMillis() + delay;
        log.warn("Redis 访问失败，{} ms 后重试；本次使用进程内存储: {}",
                delay, exception.getMessage());
    }

    private void recordRedisSuccess() {
        redisRetryAt = 0;
        if (redisFailures.getAndSet(0) > 0) {
            log.info("Redis 已恢复，在线状态重新写入 Redis");
        }
    }

    private void writeRedis(String sid, SessionInfo info, Duration ttl) {
        String value = JsonUtils.toJson(info);
        redisTemplate.opsForValue().set(sessionKey(sid), value, ttl);

        String userKey = userKey(info.getUserId());
        redisTemplate.opsForSet().add(userKey, sid);
        redisTemplate.expire(userKey, ttl);

        String tenantKey = tenantKey(info.getTenantId());
        redisTemplate.opsForZSet().add(tenantKey, String.valueOf(info.getUserId()),
                System.currentTimeMillis() + ttl.toMillis());
        redisTemplate.expire(tenantKey, ttl.multipliedBy(2));
    }

    private void touchRedis(String sid, SessionInfo info, Duration ttl) {
        redisTemplate.expire(sessionKey(sid), ttl);
        String userKey = userKey(info.getUserId());
        redisTemplate.expire(userKey, ttl);
        String tenantKey = tenantKey(info.getTenantId());
        redisTemplate.opsForZSet().add(tenantKey, String.valueOf(info.getUserId()),
                System.currentTimeMillis() + ttl.toMillis());
        redisTemplate.expire(tenantKey, ttl.multipliedBy(2));
    }

    private SessionInfo readRedis(String sid) {
        String value = redisTemplate.opsForValue().get(sessionKey(sid));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.fromJson(value, SessionInfo.class);
        } catch (IllegalArgumentException exception) {
            redisTemplate.delete(sessionKey(sid));
            return null;
        }
    }

    private String sessionKey(String sid) {
        return redisSettings.getKeyPrefix() + "session:" + sid;
    }

    private String userKey(Long userId) {
        return redisSettings.getKeyPrefix() + "user:" + userId + ":sessions";
    }

    private String tenantKey(Long tenantId) {
        return redisSettings.getKeyPrefix() + "tenant:" + tenantId + ":online";
    }

    /** 在线会话快照，剩余有效时间仅用于展示。 */
    public record OnlineSession(String sessionId, SessionInfo info, long expiresInSeconds) {
    }

    private static final class MemorySession {
        private final SessionInfo info;
        private Instant expiresAt;
        /** 是否已成功写入 Redis；用于区分故障期间仅存内存的会话。 */
        private boolean redisBacked;

        private MemorySession(SessionInfo info, Instant expiresAt, boolean redisBacked) {
            this.info = info;
            this.expiresAt = expiresAt;
            this.redisBacked = redisBacked;
        }
    }
}
