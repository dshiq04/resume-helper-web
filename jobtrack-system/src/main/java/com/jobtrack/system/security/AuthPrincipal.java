package com.jobtrack.system.security;

/**
 * 写入 SecurityContext 的当前登录用户快照。
 */
public record AuthPrincipal(
        Long id,
        Long tenantId,
        String username,
        String displayName,
        String role) {
}
