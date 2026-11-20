package com.jobtrack.system.security;

import com.jobtrack.common.context.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * 解析 Authorization: Bearer &lt;token&gt;，校验在线会话，并把租户写入 ThreadLocal。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    AuthPrincipal principal = jwtService.parse(token);
                    Duration ttl = Duration.ofHours(jwtService.ttlHours());
                    SessionInfo session = sessionService.resolve(principal.sessionId(), ttl);
                    if (session != null) {
                        AuthPrincipal authenticated = new AuthPrincipal(
                                session.getUserId(), session.getTenantId(), session.getUsername(),
                                session.getDisplayName(), session.getRole(), principal.sessionId());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        authenticated,
                                        token,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + authenticated.role())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        TenantContext.set(authenticated.tenantId());
                    }
                } catch (JwtException | IllegalArgumentException exception) {
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程会被容器复用，请求结束必须清理，避免串租户。
            TenantContext.clear();
        }
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
