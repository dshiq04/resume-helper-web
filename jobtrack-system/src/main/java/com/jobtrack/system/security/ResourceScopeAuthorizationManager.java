package com.jobtrack.system.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 按授权码的「数据范围」校验接口访问权限。
 *
 * <p>授权码同时携带两类权限：</p>
 * <ul>
 *   <li>SCOPE_read / SCOPE_write / SCOPE_readwrite：决定能读还是能写；</li>
 *   <li>RES_applications / RES_resumes / RES_statistics / RES_profile：决定能访问哪类数据。</li>
 * </ul>
 *
 * <p>两者必须同时满足。网页端 JWT 登录用户拥有全部 SCOPE_* 与 RES_*，
 * 行为与之前保持一致；授权码只获得被勾选的数据范围。</p>
 */
public final class ResourceScopeAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String RESOURCE_PREFIX = "RES_";

    private final String resource;

    private ResourceScopeAuthorizationManager(String resource) {
        this.resource = resource;
    }

    /** 创建针对指定数据范围（applications / resumes / statistics / profile）的校验器。 */
    public static ResourceScopeAuthorizationManager of(String resource) {
        return new ResourceScopeAuthorizationManager(resource);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        Set<String> authorities = current.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean writing = isWrite(context);
        boolean scopeAllowed = writing
                ? authorities.contains("SCOPE_write") || authorities.contains("SCOPE_readwrite")
                : authorities.contains("SCOPE_read") || authorities.contains("SCOPE_readwrite");
        boolean resourceAllowed = authorities.contains(RESOURCE_PREFIX + resource);
        return new AuthorizationDecision(scopeAllowed && resourceAllowed);
    }

    private boolean isWrite(RequestAuthorizationContext context) {
        String method = context.getRequest().getMethod();
        return !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
    }
}
