package com.jobtrack.system.security;

import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final int ttlHours;

    public JwtService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.ttlHours = properties.getJwt().getTtlHours();
    }

    public IssuedToken issue(SysUser user) {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(ttlHours);
        Date issued = new Date();
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("tenantId", user.getTenantId())
                .claim("username", user.getUsername())
                .claim("displayName", user.getDisplayName())
                .claim("role", user.getRole())
                .issuedAt(issued)
                .expiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        Long tenantId = ((Number) claims.get("tenantId")).longValue();
        String role = String.valueOf(claims.getOrDefault("role", "USER"));
        String displayName = claims.get("displayName") == null ? "" : String.valueOf(claims.get("displayName"));
        return new AuthPrincipal(userId, tenantId, String.valueOf(claims.get("username")), displayName, role);
    }

    public LocalDateTime expiresAt(Claims claims) {
        return LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.systemDefault());
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {
    }
}
