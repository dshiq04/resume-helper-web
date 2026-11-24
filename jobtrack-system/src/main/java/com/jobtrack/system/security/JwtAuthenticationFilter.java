package com.jobtrack.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.common.api.Result;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.system.service.AccessTokenService;
import com.jobtrack.system.service.BanService;
import com.jobtrack.system.service.PermissionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 Authorization: Bearer &lt;token&gt;，并把身份交给 Spring Security 授权。
 *
 * <p>支持两种凭证：</p>
 * <ul>
 *   <li>登录后的 JWT（网页端），授予全部 SCOPE 权限；</li>
 *   <li>授权码 rt_xxx（浏览器扩展），只授予该授权码的 SCOPE 权限，
 *       具体读/写限制由 SecurityConfig 的 authorizeHttpRequests 执行。</li>
 * </ul>
 *
 * <p>封禁集合中的用户在 JWT 与授权码两种凭证下都会立即被拒绝，
 * 并返回明确的封禁提示，而不是笼统的“未登录”。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String TOKEN_PREFIX = "rt_";

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final AccessTokenService accessTokenService;
    private final BanService banService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (token.startsWith(TOKEN_PREFIX)) {
                    if (!authenticateAccessToken(token, request, response)) {
                        return;
                    }
                } else if (!authenticateJwt(token, response)) {
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程会被容器复用，请求结束必须清理，避免串租户。
            TenantContext.clear();
        }
    }

    private boolean authenticateJwt(String token, HttpServletResponse response) throws IOException {
        try {
            AuthPrincipal principal = jwtService.parse(token);
            Duration ttl = Duration.ofHours(jwtService.ttlHours());
            // 先查 Redis：令牌不存在（已退出/已注销/过期）时不再认证，
            // 由 RestAuthenticationEntryPoint 返回 401「用户未登录」。
            SessionInfo session = sessionService.resolve(principal.sessionId(), ttl);
            if (session != null) {
                if (banService.isBanned(session.getUserId())) {
                    String message = banService.message(session.getUserId());
                    writeBanned(response, message == null ? "该账号已被封禁" : message);
                    return false;
                }
                // 将 Redis 中的权限与用户名整合进 Spring Security 上下文。
                List<String> permissions = session.getPermissions() == null
                        ? List.of() : session.getPermissions();
                List<GrantedAuthority> authorities = new ArrayList<>();
                // 网页端登录用户拥有全部读写权限。
                authorities.add(new SimpleGrantedAuthority("SCOPE_read"));
                authorities.add(new SimpleGrantedAuthority("SCOPE_write"));
                authorities.add(new SimpleGrantedAuthority("SCOPE_readwrite"));
                // Redis 权限映射为 ROLE_* 权限。
                for (String permission : permissions) {
                    if (permission != null && !permission.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + permission.trim()));
                    }
                }
                if (permissions.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role()));
                }
                // 用户名与角色以 Redis 会话为准（与权限保持一致）。
                AuthPrincipal authenticated = new AuthPrincipal(
                        session.getUserId(), session.getTenantId(), session.getUsername(),
                        session.getDisplayName(),
                        permissions.isEmpty() ? principal.role()
                                : PermissionService.roleOf(permissions),
                        principal.sessionId());
                bind(authenticated, token, authorities, true);
            }
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            return true;
        }
    }

    private boolean authenticateAccessToken(String token, HttpServletRequest request,
                                            HttpServletResponse response) throws IOException {
        // 校验接口本身用请求体中的 code，不需要把授权码当作登录态。
        if (request.getRequestURI().startsWith("/api/tokens/validate")) {
            return true;
        }
        AccessTokenService.TokenAuth auth = accessTokenService.authenticate(token);
        if (auth == null) {
            return true;
        }
        if (banService.isBanned(auth.user())) {
            String message = banService.message(auth.user());
            writeBanned(response, message == null ? "该账号已被封禁" : message);
            return false;
        }
        AuthPrincipal principal = new AuthPrincipal(
                auth.user().getId(), auth.user().getTenantId(), auth.user().getUsername(),
                auth.user().getDisplayName(), auth.user().getRole(),
                "token:" + auth.token().getId());
        // 授权码只授予 SCOPE_*，不授予 ROLE_*：避免扩展授权码访问管理员接口。
        bind(principal, token, List.of(
                new SimpleGrantedAuthority("SCOPE_" + auth.token().getScope())), false);
        return true;
    }

    private void bind(AuthPrincipal principal, String credentials,
                      List<GrantedAuthority> scopes, boolean addRole) {
        List<GrantedAuthority> authorities = new ArrayList<>(scopes);
        if (addRole) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role()));
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, credentials, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.set(principal.tenantId());
    }

    private void writeBanned(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = message == null ? "该账号已被封禁" : message;
        objectMapper.writeValue(response.getWriter(),
                Result.error(HttpServletResponse.SC_FORBIDDEN, safe));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = value.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
