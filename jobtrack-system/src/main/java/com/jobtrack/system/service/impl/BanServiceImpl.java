package com.jobtrack.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.system.config.RedisSettings;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.service.BanService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 封禁集合：Redis ZSet + 进程内兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanServiceImpl implements BanService {

    private static final String BAN_KEY = "ban:users";
    private static final long PERMANENT_SCORE = Long.MAX_VALUE;
    private static final DateTimeFormatter UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RedisSettings redisSettings;
    private final StringRedisTemplate redisTemplate;
    private final SysUserMapper userMapper;

    private final Map<Long, Long> memory = new ConcurrentHashMap<>();
    private volatile boolean redisAvailable;

    @PostConstruct
    void init() {
        redisAvailable = redisSettings.isEnabled() && redisTemplate != null;
        log.info("封禁集合存储: {}", redisAvailable ? "Redis" : "进程内");
    }

    public boolean isBanned(Long userId) {
        if (userId == null) {
            return false;
        }
        // Redis 可用时以 Redis 集合为准，避免多实例下进程内缓存过期。
        if (useRedis()) {
            try {
                Double score = redisTemplate.opsForZSet().score(banKey(), String.valueOf(userId));
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
                disableRedis(exception);
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
        if ("BANNED".equalsIgnoreCase(user.getStatus())) {
            LocalDateTime until = user.getBanUntil();
            if (until == null || until.isAfter(LocalDateTime.now())) {
                return true;
            }
            // 临时封禁已到期：缓存中的旧记录也一并清掉。
            apply(user.getId(), false, null);
            return false;
        }
        return isBanned(user.getId());
    }

    public String message(Long userId) {
        if (!isBanned(userId)) {
            return null;
        }
        Long score = memory.get(userId);
        if (score == null && useRedis()) {
            try {
                Double value = redisTemplate.opsForZSet().score(banKey(), String.valueOf(userId));
                score = value == null ? null : value.longValue();
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
        return describe(score);
    }

    public String message(SysUser user) {
        if (user == null || !isBanned(user)) {
            return null;
        }
        if ("BANNED".equalsIgnoreCase(user.getStatus())) {
            if ("TEMPORARY".equalsIgnoreCase(user.getBanType()) && user.getBanUntil() != null) {
                return "该账号已被暂时封禁，解封时间：" + user.getBanUntil().format(UNTIL_FORMAT);
            }
            return "该账号已被永久封禁";
        }
        return message(user.getId());
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
                } catch (Exception exception) {
                    disableRedis(exception);
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
            } catch (Exception exception) {
                disableRedis(exception);
            }
        }
    }

    public void warmCache() {
        try {
            List<SysUser> banned = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getStatus, "BANNED"));
            if (useRedis()) {
                try {
                    // 全量重建，避免数据库中已解封的账号残留。
                    redisTemplate.delete(banKey());
                } catch (Exception exception) {
                    disableRedis(exception);
                }
            }
            memory.clear();
            for (SysUser user : banned) {
                if (user.getBanUntil() == null || user.getBanUntil().isAfter(LocalDateTime.now())) {
                    apply(user.getId(), true, user.getBanUntil());
                }
            }
            log.info("封禁集合已同步: {} 人", banned.size());
        } catch (Exception exception) {
            log.warn("封禁集合同步失败: {}", exception.getMessage());
        }
    }

    private String describe(Long score) {
        if (score == null) {
            return "该账号已被封禁";
        }
        if (score >= PERMANENT_SCORE) {
            return "该账号已被永久封禁";
        }
        LocalDateTime until = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(score), ZoneId.systemDefault());
        return "该账号已被暂时封禁，解封时间：" + until.format(UNTIL_FORMAT);
    }

    private boolean isActive(long score) {
        return score >= PERMANENT_SCORE || score > System.currentTimeMillis();
    }

    private boolean useRedis() {
        return redisAvailable && redisTemplate != null;
    }

    private void disableRedis(Exception exception) {
        if (redisAvailable) {
            log.warn("Redis 不可用，封禁集合临时切换到进程内存储: {}", exception.getMessage());
            redisAvailable = false;
        }
    }

    private String banKey() {
        return redisSettings.getKeyPrefix() + BAN_KEY;
    }
}
