package com.jobtrack.application.service;

import java.util.function.Supplier;

/**
 * 投递数据的读缓存。
 */
public interface ApplicationCacheService {

    <T> T cached(String suffix, Class<T> type, Supplier<T> loader);

    void invalidate();

    long version(long tenantId);
}
