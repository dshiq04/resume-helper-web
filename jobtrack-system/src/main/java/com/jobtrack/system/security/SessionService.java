package com.jobtrack.system.security;

import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.RedisSettings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 在线会话存储。
 *
 * <p>登录会话统一使用 Redisson 保存：</p>
 * <ul>
 *   <li>{@code session:<sid>} 保存会话 JSON；</li>
 *   <li>{@code user:<userId>:sessions} 保存账号的全部会话，便于修改密码后强制下线；</li>
 *   <li>{@code tenant:<tenantId>:online} 用于统计工作区在线人数。</li>
 * </ul>
 *
 * <p>Redis 是可选项：配置项 redis.enabled=false 时使用进程内 Map；
 * 打开后会话与在线人数写入 Redisson。如果 Redis 临时不可用，会自动退回内存存储，
 * 保证登录功能不被基础设施拖垮。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisSettings redisSettings;
    private final ObjectProvider<RedissonClient> redissonProvider;

    private static final long REDIS_RETRY_BASE_MS = 5_000;
    private static final long REDIS_RETRY_MAX_MS = 60_000;

    private final Map<String, MemorySession> memory = new ConcurrentHashMap<>();
    /** 本机撤销墓碑：Redis 不可用时注销的会话不能因为 Redis 恢复而复活。 */
    private final Map<String, Instant> revokedSessions = new ConcurrentHashMap<>();
    /** 账号级撤销墓碑：改密/注销期间 Redis 不可用时，恢复后拒绝该账号的旧会话。 */
    private final Map<Long, Instant> revokedUsers = new ConcurrentHashMap<>();
    private static final Duration REVOKE_TTL = Duration.ofDays(7);
    private final AtomicLong redisFailures = new AtomicLong();
    /** Redisson 是否可用；运行期不再永久关闭，只做冷却重试。 */
    private volatile RedissonClient redisson;
    private volatile boolean redisEnabled;
    private volatile long redisRetryAt;

    @PostConstruct
    void init() {
        if (redisSettings.isEnabled()) {
            try {
                redisson = redissonProvider.getIfAvailable();
            } catch (Exception exception) {
                log.warn("Redisson 初始化失败，登录会话使用进程内存储: {}", exception.getMessage());
                redisson = null;
            }
        }
        redisEnabled = redisson != null;
        log.info("在线状态存储: {}", redisEnabled ? "Redisson" : "进程内");
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
        pruneRevoked();
        if (isRevoked(sid)) {
            // Redis 冷却期间已注销的会话：恢复后必须先把 Redis 中的旧会话删掉，
            // 否则会话会“复活”。
            deleteRedisSessionQuietly(sid);
            memory.remove(sid);
            return null;
        }
        if (useRedis()) {
            try {
                SessionInfo info = readRedis(sid);
                recordRedisSuccess();
                if (info != null && userRevoked(info)) {
                    deleteRedisSessionQuietly(sid);
                    memory.remove(sid);
                    return null;
                }
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
        if (session.expiresAt.isBefore(Instant.now()) || isRevoked(sid)
                || userRevoked(session.info)) {
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
        // 内存副本必须先清理，并记录撤销墓碑：Redis 异常期间也不能让会话复活。
        memory.remove(sid);
        revokedSessions.put(sid, Instant.now().plus(REVOKE_TTL));
        if (useRedis()) {
            try {
                SessionInfo info = readRedis(sid);
                redisson.getBucket(sessionKey(sid), StringCodec.INSTANCE).delete();
                if (info != null) {
                    String userKey = userKey(info.getUserId());
                    RSet<String> sessions = redisson.getSet(userKey, StringCodec.INSTANCE);
                    sessions.remove(sid);
                    if (sessions.size() == 0) {
                        sessions.delete();
                        redisson.getScoredSortedSet(tenantKey(info.getTenantId()), StringCodec.INSTANCE)
                                .remove(String.valueOf(info.getUserId()));
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
        // 账号级撤销墓碑。Redis 冷却期间恢复后，旧会话即使仍在 Redis 中也会被拒绝，
        // 而在墓碑之后创建的新会话（createdAt 更晚）仍可正常使用。
        revokedUsers.put(userId, Instant.now());
        // 同时记录本机已知的会话 ID，覆盖单会话撤销路径。
        memory.forEach((sid, session) -> {
            if (userId.equals(session.info.getUserId())) {
                revokedSessions.put(sid, Instant.now().plus(REVOKE_TTL));
            }
        });
        if (useRedis()) {
            try {
                RSet<String> sessions = redisson.getSet(userKey(userId), StringCodec.INSTANCE);
                Set<String> sids = sessions.readAll();
                for (String sid : sids) {
                    revokedSessions.put(sid, Instant.now().plus(REVOKE_TTL));
                    redisson.getBucket(sessionKey(sid), StringCodec.INSTANCE).delete();
                }
                sessions.delete();
                if (tenantId != null) {
                    redisson.getScoredSortedSet(tenantKey(tenantId), StringCodec.INSTANCE)
                            .remove(String.valueOf(userId));
                }
                recordRedisSuccess();
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        memory.entrySet().removeIf(entry -> userId.equals(entry.getValue().info.getUserId()));
    }

    private boolean isRevoked(String sid) {
        Instant until = revokedSessions.get(sid);
        if (until == null) {
            return false;
        }
        if (until.isBefore(Instant.now())) {
            revokedSessions.remove(sid);
            return false;
        }
        return true;
    }

    private boolean userRevoked(SessionInfo info) {
        if (info == null || info.getUserId() == null) {
            return false;
        }
        Instant revokedAt = revokedUsers.get(info.getUserId());
        if (revokedAt == null) {
            return false;
        }
        // 撤销墓碑之后创建的新会话（重新登录）允许通过；旧会话一律拒绝。
        if (info.getCreatedAt() != null && info.getCreatedAt().isAfter(revokedAt)) {
            return false;
        }
        return true;
    }

    private void pruneRevoked() {
        Instant now = Instant.now();
        revokedSessions.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        revokedUsers.entrySet().removeIf(entry -> entry.getValue().plus(REVOKE_TTL).isBefore(now));
    }

    private void deleteRedisSessionQuietly(String sid) {
        if (!useRedis()) {
            return;
        }
        try {
            redisson.getBucket(sessionKey(sid), StringCodec.INSTANCE).delete();
        } catch (Exception exception) {
            recordRedisFailure(exception);
        }
    }

    /**
     * 列出所有在线会话，供管理员「在线用户」页使用。
     * 该方法不按租户过滤，管理员可以看到全部工作区的会话。
     */
    public List<OnlineSession> listSessions() {
        Map<String, OnlineSession> merged = new LinkedHashMap<>();
        boolean redisRead = false;
        if (useRedis()) {
            String prefix = redisSettings.getKeyPrefix() + "session:";
            try (Stream<String> keys = redisson.getKeys()
                    .getKeysStreamByPattern(prefix + "*", 500)) {
                keys.forEach(key -> {
                    String value = redisson.<String>getBucket(key, StringCodec.INSTANCE).get();
                    if (value == null || value.isBlank()) {
                        return;
                    }
                    try {
                        SessionInfo info = JsonUtils.fromJson(value, SessionInfo.class);
                        long ttl = redisson.getBucket(key, StringCodec.INSTANCE).remainTimeToLive();
                        String sid = key.substring(prefix.length());
                        merged.put(sid, new OnlineSession(sid, info, Math.max(0, ttl / 1000)));
                    } catch (IllegalArgumentException exception) {
                        redisson.getBucket(key, StringCodec.INSTANCE).delete();
                    }
                });
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
                RScoredSortedSet<String> online =
                        redisson.getScoredSortedSet(tenantKey(tenantId), StringCodec.INSTANCE);
                online.removeRangeByScore(0, true, System.currentTimeMillis(), true);
                recordRedisSuccess();
                return online.size();
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
        return redisEnabled && !useRedis();
    }

    private boolean useRedis() {
        return redisEnabled && redisson != null
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
        log.warn("Redisson 访问失败，{} ms 后重试；本次使用进程内存储: {}",
                delay, exception.getMessage());
    }

    private void recordRedisSuccess() {
        redisRetryAt = 0;
        if (redisFailures.getAndSet(0) > 0) {
            log.info("Redisson 已恢复，在线状态重新写入 Redis");
        }
    }

    private void writeRedis(String sid, SessionInfo info, Duration ttl) {
        redisson.getBucket(sessionKey(sid), StringCodec.INSTANCE)
                .set(JsonUtils.toJson(info), ttl);

        RSet<String> sessions = redisson.getSet(userKey(info.getUserId()), StringCodec.INSTANCE);
        sessions.add(sid);
        sessions.expire(ttl);

        RScoredSortedSet<String> online =
                redisson.getScoredSortedSet(tenantKey(info.getTenantId()), StringCodec.INSTANCE);
        online.add(System.currentTimeMillis() + ttl.toMillis(), String.valueOf(info.getUserId()));
        online.expire(ttl.multipliedBy(2));
    }

    private void touchRedis(String sid, SessionInfo info, Duration ttl) {
        redisson.getBucket(sessionKey(sid), StringCodec.INSTANCE).expire(ttl);

        RSet<String> sessions = redisson.getSet(userKey(info.getUserId()), StringCodec.INSTANCE);
        sessions.expire(ttl);

        RScoredSortedSet<String> online =
                redisson.getScoredSortedSet(tenantKey(info.getTenantId()), StringCodec.INSTANCE);
        online.add(System.currentTimeMillis() + ttl.toMillis(), String.valueOf(info.getUserId()));
        online.expire(ttl.multipliedBy(2));
    }

    private SessionInfo readRedis(String sid) {
        String value = redisson.<String>getBucket(sessionKey(sid), StringCodec.INSTANCE).get();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.fromJson(value, SessionInfo.class);
        } catch (IllegalArgumentException exception) {
            redisson.getBucket(sessionKey(sid), StringCodec.INSTANCE).delete();
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
