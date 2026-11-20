package com.jobtrack.common.context;

/**
 * 当前请求所属的租户（账号）标识。
 *
 * <p>由 JWT 过滤器在请求进入时写入，MyBatis-Plus 的租户拦截器读取它，
 * 自动为所有业务 SQL 追加 {@code tenant_id = ?} 条件。请求结束时必须清理，
 * 避免线程复用导致数据串租户。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        HOLDER.set(tenantId);
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static long require() {
        Long tenantId = HOLDER.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant bound to the current request");
        }
        return tenantId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
