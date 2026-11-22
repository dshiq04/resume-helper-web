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

    private final Map<String, MemorySession> memory = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable;

    @PostConstruct
    void init() {
        redisAvailable = redisSettings.isEnabled() && redisTemplate != null;
        log.info("在线状态存储: {}", redisAvailable ? "Redis" : "进程内");
    }

    public String create(SessionInfo info, Duration ttl) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        save(sid, info, ttl);
        return sid;
    }

    public void save(String sid, SessionInfo info, Duration ttl) {
        if (useRedis()) {
            try {
                writeRedis(sid, info, ttl);
                return;
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
        memory.put(sid, new MemorySession(info, Instant.now().plus(ttl)));
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
                if (info != null) {
                    touchRedis(sid, info, ttl);
                }
                return info;
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
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
                return;
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
        memory.remove(sid);
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
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
        memory.entrySet().removeIf(entry -> userId.equals(entry.getValue().info.getUserId()));
    }

    /**
     * 列出所有在线会话，供管理员「在线用户」页使用。
     * 该方法不按租户过滤，管理员可以看到全部工作区的会话。
     */
    public List<OnlineSession> listSessions() {
        List<OnlineSession> result = new ArrayList<>();
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
                        result.add(new OnlineSession(key.substring(prefix.length()), info,
                                ttl == null ? 0 : Math.max(0, ttl)));
                    } catch (IllegalArgumentException exception) {
                        redisTemplate.delete(key);
                    }
                }
                return result;
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
        Instant now = Instant.now();
        memory.forEach((sid, session) -> {
            if (session.expiresAt.isAfter(now)) {
                result.add(new OnlineSession(sid, session.info,
                        Duration.between(now, session.expiresAt).toSeconds()));
            } else {
                memory.remove(sid);
            }
        });
        return result;
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
                return count == null ? 0 : count;
            } catch (Exception exception) {
                disableRedis(exception);
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

    private boolean useRedis() {
        return redisAvailable && redisTemplate != null;
    }

    private void disableRedis(Exception exception) {
        if (redisAvailable) {
            log.warn("Redis 不可用，在线状态临时切换到进程内存储: {}", exception.getMessage());
            redisAvailable = false;
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

        private MemorySession(SessionInfo info, Instant expiresAt) {
            this.info = info;
            this.expiresAt = expiresAt;
        }
    }
}
