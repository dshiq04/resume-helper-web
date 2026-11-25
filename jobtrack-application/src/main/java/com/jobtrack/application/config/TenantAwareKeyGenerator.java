package com.jobtrack.application.config;

import com.jobtrack.common.context.TenantContext;
import org.springframework.cache.interceptor.KeyGenerator;

import java.lang.reflect.Method;

/**
 * 带租户隔离的缓存键生成器。
 *
 * <p>缓存键由「租户 + 方法 + 参数」组成，避免不同账号之间读到彼此的投递、
 * 统计、简历或回收站数据。参数使用长度前缀拼接，防止值里含有分隔符时产生键冲突。</p>
 */
public class TenantAwareKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        Long tenantId = TenantContext.get();
        StringBuilder key = new StringBuilder();
        key.append(tenantId == null ? "public" : tenantId)
                .append(':')
                .append(method.getDeclaringClass().getSimpleName())
                .append('.')
                .append(method.getName())
                .append('(');
        for (int index = 0; index < params.length; index++) {
            if (index > 0) {
                key.append(',');
            }
            String value = params[index] == null ? "" : String.valueOf(params[index]);
            key.append(value.length()).append(':').append(value);
        }
        return key.append(')').toString();
    }
}
