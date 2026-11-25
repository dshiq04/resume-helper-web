package com.jobtrack.system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端配置。
 *
 * <p>登录会话、在线人数与图形验证码统一走 Redisson；业务查询缓存仍由
 * Spring Cache + Spring Data Redis 负责，两者连接同一个 Redis 实例。</p>
 *
 * <p>开启懒初始化：Redis 临时不可用时应用仍能启动，业务侧按“内存兜底 /
 * 返回 503”的策略处理，等 Redis 恢复后自动继续使用。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedisSettings redisSettings;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "redis", name = "enabled", havingValue = "true")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setLazyInitialization(true);

        String address = "redis://" + redisSettings.getHost() + ":" + redisSettings.getPort();
        SingleServerConfig server = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisSettings.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setTimeout(timeoutMillis(redisSettings.getTimeout()))
                .setConnectTimeout(timeoutMillis(redisSettings.getTimeout()))
                .setRetryAttempts(3)
                .setRetryInterval(1500);
        if (StringUtils.hasText(redisSettings.getPassword())) {
            server.setPassword(redisSettings.getPassword());
        }
        log.info("Redisson 已配置: address={}, database={}, keyPrefix={}",
                address, redisSettings.getDatabase(), redisSettings.getKeyPrefix());
        return Redisson.create(config);
    }

    /** 把 2s / 500ms 这类配置值转换为毫秒。 */
    private int timeoutMillis(String value) {
        if (!StringUtils.hasText(value)) {
            return 3000;
        }
        String text = value.trim().toLowerCase();
        try {
            if (text.endsWith("ms")) {
                return Integer.parseInt(text.substring(0, text.length() - 2).trim());
            }
            if (text.endsWith("s")) {
                return (int) (Double.parseDouble(text.substring(0, text.length() - 1).trim()) * 1000);
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            log.warn("无法解析 redis.timeout={}，使用默认值 3000ms", value);
            return 3000;
        }
    }
}
