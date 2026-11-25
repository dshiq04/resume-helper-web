package com.jobtrack.application.config;

import com.jobtrack.common.context.TenantContext;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;

/**
 * 按租户隔离缓存实例。
 *
 * <p>缓存名追加 {@code @tenant:<租户ID>} 后再交给 RedisCacheManager，
 * 因此 {@code @CacheEvict(allEntries = true)} 只会清理当前账号的缓存，
 * 不会因为某个账号新增或删除投递而让其他账号的缓存全部失效。</p>
 */
public class TenantAwareCacheManager implements CacheManager {

    private final CacheManager delegate;

    public TenantAwareCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        Long tenantId = TenantContext.get();
        String scopedName = tenantId == null ? name : name + "@tenant:" + tenantId;
        return delegate.getCache(scopedName);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
