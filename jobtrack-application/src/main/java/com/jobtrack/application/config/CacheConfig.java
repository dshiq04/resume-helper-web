package com.jobtrack.application.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.config.RedisSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring Cache 配置。
 *
 * <p>查询结果统一写入 Redis，缓存键为
 * {@code <key-prefix>cache:<缓存名>@tenant:<租户>}，不同账号互不影响；
 * 增删改通过 {@code @CacheEvict} 清理，缓存未命中时重新查询数据库并回填。</p>
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer {

    private final RedisConnectionFactory connectionFactory;
    private final RedisSettings redisSettings;
    private final AppProperties appProperties;

    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(appProperties.getCache().getTtl())
                .disableCachingNullValues()
                .prefixCacheNameWith(cacheKeyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisSerializer()));

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .enableCreateOnMissingCache()
                .transactionAware()
                .build();
        // 缓存名追加租户后缀，allEntries 清理只影响当前账号。
        return new TenantAwareCacheManager(redisCacheManager);
    }

    /**
     * 全局缓存键生成器，统一加入租户前缀，保证多账号数据隔离。
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return new TenantAwareKeyGenerator();
    }

    /**
     * Redis 故障时只记录日志并回退数据库，避免缓存不可用拖垮查询接口。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 缓存读取失败，本次查询回退数据库: cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache,
                                            Object key, Object value) {
                log.warn("Redis 缓存写入失败: cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis 缓存清理失败: cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis 缓存清空失败: cache={}, error={}",
                        cache.getName(), exception.getMessage());
            }
        };
    }

    /**
     * Redis 值序列化：保留业务类型信息，LocalDateTime 等时间字段按 ISO 字符串读写。
     */
    public static GenericJackson2JsonRedisSerializer redisSerializer() {
        ObjectMapper mapper = JsonUtils.mapper().copy();
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.jobtrack.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.math.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    private String cacheKeyPrefix() {
        String prefix = redisSettings.getKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            return "cache:";
        }
        return (prefix.endsWith(":") ? prefix : prefix + ":") + "cache:";
    }
}
