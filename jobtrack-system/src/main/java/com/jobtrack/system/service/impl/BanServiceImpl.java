package com.jobtrack.system.service.impl;

import com.jobtrack.system.config.RedisSettings;
import com.jobtrack.system.mapper.SysUserBanMapper;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.pojo.po.SysUserBan;
import com.jobtrack.system.service.BanService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 封禁集合：Redis ZSet + 进程内兜底，封禁详情来自 sys_user_ban 记录表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanServiceImpl implements BanService {

    private static final String BAN_KEY = "ban:users";
    private static final long PERMANENT_SCORE = Long.MAX_VALUE;
    private static final long REDIS_RETRY_BASE_MS = 5_000;
    private static final long REDIS_RETRY_MAX_MS = 60_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RedisSettings redisSettings;
    private final StringRedisTemplate redisTemplate;
    private final SysUserBanMapper banMapper;

    private final Map<Long, Long> memory = new ConcurrentHashMap<>();
    private final AtomicLong redisFailures = new AtomicLong();
    private volatile boolean redisEnabled;
    private volatile long redisRetryAt;

    @PostConstruct
    void init() {
        redisEnabled = redisSettings.isEnabled() && redisTemplate != null;
        log.info("封禁集合存储: {}", redisEnabled ? "Redis" : "进程内");
    }

    public SysUserBan activeBan(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return banMapper.selectActiveByUserId(userId, LocalDateTime.now());
        } catch (Exception exception) {
            log.warn("查询封禁记录失败: {}", exception.getMessage());
            return null;
        }
    }

    public List<SysUserBan> activeBans() {
        try {
            return banMapper.selectActiveBans(LocalDateTime.now());
        } catch (Exception exception) {
            log.warn("查询封禁记录列表失败: {}", exception.getMessage());
            return List.of();
        }
    }

    public boolean isBanned(Long userId) {
        if (userId == null) {
            return false;
        }
        // Redis 可用时以 Redis 集合为准，避免多实例下进程内缓存过期。
        if (useRedis()) {
            try {
                Double score = redisTemplate.opsForZSet().score(banKey(), String.valueOf(userId));
                recordRedisSuccess();
                if (score == null) {
                    memory.remove(userId);
                    return false;
                }
                long value = score.longValue();
                if (isActive(value)) {
                    memory.put(userId, value);
                    return true;
                }
                memory.remove(userId);
                redisTemplate.opsForZSet().remove(banKey(), String.valueOf(userId));
                return false;
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
        Long memoryScore = memory.get(userId);
        if (memoryScore != null) {
            if (isActive(memoryScore)) {
                return true;
            }
            memory.remove(userId);
        }
        return false;
    }

    public boolean isBanned(SysUser user) {
        if (user == null) {
            return false;
        }
        return activeBan(user.getId()) != null || isBanned(user.getId());
    }

    public String message(Long userId) {
        SysUserBan ban = activeBan(userId);
        if (ban != null) {
            return describe(ban);
        }
        if (!isBanned(userId)) {
            return null;
        }
        return "该账号已被封禁";
    }

    public String message(SysUser user) {
        return user == null ? null : message(user.getId());
    }

    @Transactional
    public SysUserBan ban(Long userId, Long tenantId, Long operatorId,
                          String banType, LocalDateTime banUntil, String reason) {
        LocalDateTime now = LocalDateTime.now();
        // 同一个用户同时只保留一条有效封禁记录，旧的自动标记为已解除。
        banMapper.revokeActive(userId, now);
        SysUserBan record = new SysUserBan();
        record.setUserId(userId);
        record.setTenantId(tenantId);
        record.setOperatorId(operatorId);
        record.setBanType(banType);
        record.setBanUntil(banUntil);
        record.setReason(reason);
        record.setBannedAt(now);
        banMapper.insert(record);
        apply(userId, true, banUntil);
        return record;
    }

    @Transactional
    public void unban(Long userId) {
        banMapper.revokeActive(userId, LocalDateTime.now());
        apply(userId, false, null);
    }

    public void apply(Long userId, boolean banned, LocalDateTime until) {
        if (userId == null) {
            return;
        }
        if (!banned) {
            memory.remove(userId);
            if (useRedis()) {
                try {
                    redisTemplate.opsForZSet().remove(banKey(), String.valueOf(userId));
                    recordRedisSuccess();
                } catch (Exception exception) {
                    recordRedisFailure(exception);
                }
            }
            return;
        }
        long score = until == null ? PERMANENT_SCORE
                : Math.max(System.currentTimeMillis() + 1000,
                until.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        memory.put(userId, score);
        if (useRedis()) {
            try {
                redisTemplate.opsForZSet().add(banKey(), String.valueOf(userId), score);
                recordRedisSuccess();
            } catch (Exception exception) {
                recordRedisFailure(exception);
            }
        }
    }

    public void warmCache() {
        try {
            List<SysUserBan> bans = banMapper.selectActiveBans(LocalDateTime.now());
            if (useRedis()) {
                try {
                    // 全量重建，避免已解除或已到期的封禁残留在 Redis。
                    redisTemplate.delete(banKey());
                    recordRedisSuccess();
                } catch (Exception exception) {
                    recordRedisFailure(exception);
                }
            }
            memory.clear();
            for (SysUserBan ban : bans) {
                apply(ban.getUserId(), true, ban.getBanUntil());
            }
            log.info("封禁集合已同步: {} 人", bans.size());
        } catch (Exception exception) {
            log.warn("封禁集合同步失败: {}", exception.getMessage());
        }
    }

    private String describe(SysUserBan ban) {
        StringBuilder text = new StringBuilder();
        if ("TEMPORARY".equalsIgnoreCase(ban.getBanType()) && ban.getBanUntil() != null) {
            text.append("该账号已被暂时封禁，解封时间：")
                    .append(ban.getBanUntil().format(TIME_FORMAT));
        } else {
            text.append("该账号已被永久封禁");
        }
        if (ban.getBannedAt() != null) {
            text.append("；封禁时间：").append(ban.getBannedAt().format(TIME_FORMAT));
        }
        if (ban.getReason() != null && !ban.getReason().isBlank()) {
            text.append("；原因：").append(ban.getReason().trim());
        }
        return text.toString();
    }

    private boolean isActive(long score) {
        return score >= PERMANENT_SCORE || score > System.currentTimeMillis();
    }

    private boolean useRedis() {
        return redisEnabled && redisTemplate != null
                && System.currentTimeMillis() >= redisRetryAt;
    }

    /** 故障后冷却并自动重试，避免一次异常永久降级为进程内存储。 */
    private void recordRedisFailure(Exception exception) {
        long failures = redisFailures.incrementAndGet();
        long delay = Math.min(REDIS_RETRY_MAX_MS,
                REDIS_RETRY_BASE_MS << Math.min(failures - 1, 4));
        redisRetryAt = System.currentTimeMillis() + delay;
        log.warn("Redis 访问失败，{} ms 后重试；封禁集合本次使用进程内存储: {}",
                delay, exception.getMessage());
    }

    private void recordRedisSuccess() {
        redisRetryAt = 0;
        if (redisFailures.getAndSet(0) > 0) {
            log.info("Redis 已恢复，封禁集合重新同步到 Redis");
        }
    }

    private String banKey() {
        return redisSettings.getKeyPrefix() + BAN_KEY;
    }
}
