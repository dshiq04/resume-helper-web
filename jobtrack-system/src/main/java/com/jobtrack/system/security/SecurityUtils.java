package com.jobtrack.system.security;

import com.jobtrack.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw BusinessException.unauthorized("unauthorized", "请先登录");
        }
        return principal;
    }

    public static Long currentUserId() {
        return current().id();
    }

    public static Long currentTenantId() {
        return current().tenantId();
    }

    public static boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal;
    }

    public static boolean isAdmin() {
        return authenticated() && "ADMIN".equalsIgnoreCase(current().role());
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw BusinessException.forbidden("forbidden", "需要管理员权限");
        }
    }
}
