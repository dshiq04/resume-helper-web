package com.jobtrack.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.dto.AccessTokenCreateRequest;
import com.jobtrack.system.dto.AccessTokenLogView;
import com.jobtrack.system.dto.AccessTokenView;
import com.jobtrack.system.dto.TokenValidationResponse;
import com.jobtrack.system.entity.AccessToken;
import com.jobtrack.system.entity.AccessTokenLog;
import com.jobtrack.system.entity.SysUser;
import com.jobtrack.system.mapper.AccessTokenLogMapper;
import com.jobtrack.system.mapper.AccessTokenMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 授权码管理：用于浏览器扩展等外部调用方。
 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private static final Set<String> SCOPES = Set.of("read", "write", "readwrite");
    private static final Set<String> RESOURCES =
            Set.of("applications", "resumes", "statistics", "profile");
    private static final List<String> DEFAULT_RESOURCES =
            List.of("applications", "resumes", "statistics", "profile");

    private final AccessTokenMapper tokenMapper;
    private final AccessTokenLogMapper logMapper;
    private final SysUserMapper userMapper;

    public List<AccessTokenView> list(Long userId) {
        return tokenMapper.selectList(Wrappers.<AccessToken>lambdaQuery()
                        .eq(AccessToken::getUserId, userId)
                        .orderByDesc(AccessToken::getCreatedAt))
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public AccessTokenView create(Long tenantId, Long userId, AccessTokenCreateRequest request) {
        String scope = request.getScope() == null
                ? "" : request.getScope().trim().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            throw BusinessException.badRequest("invalid_scope", "权限范围仅支持 read / write / readwrite");
        }
        LocalDate expiresOn = request.getExpiresOn();
        if (expiresOn != null && expiresOn.isBefore(LocalDate.now())) {
            throw BusinessException.badRequest("invalid_expiry", "有效期不能早于今天");
        }

        AccessToken token = new AccessToken();
        token.setTenantId(tenantId);
        token.setUserId(userId);
        token.setName(normalizeName(request.getName()));
        token.setScope(scope);
        token.setResources(joinResources(request.getResources()));
        token.setExpiresOn(expiresOn);
        token.setCode("rt_" + UUID.randomUUID().toString().replace("-", ""));
        tokenMapper.insert(token);
        return view(token);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        AccessToken token = tokenMapper.selectById(id);
        if (token == null || !userId.equals(token.getUserId())) {
            throw BusinessException.notFound("token_not_found", "授权码不存在或已删除");
        }
        tokenMapper.deleteById(id);
    }

    /**
     * 供扩展调用：校验授权码是否存在、是否过期、账号是否可用。
     * 该方法不需要登录态，因此 access_token 表在租户拦截器中已被忽略。
     */
    @Transactional
    public TokenValidationResponse validate(String rawCode, String ip, String userAgent) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            logUse(null, null, "invalid", code, ip, userAgent);
            return TokenValidationResponse.invalid("invalid");
        }
        AccessToken token = tokenMapper.selectOne(Wrappers.<AccessToken>lambdaQuery()
                .eq(AccessToken::getCode, code));
        if (token == null) {
            logUse(null, null, "invalid", code, ip, userAgent);
            return TokenValidationResponse.invalid("invalid");
        }
        if (token.getExpiresOn() != null && token.getExpiresOn().isBefore(LocalDate.now())) {
            logUse(token, null, "expired", code, ip, userAgent);
            return TokenValidationResponse.invalid("expired");
        }
        SysUser user = userMapper.selectById(token.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            logUse(token, null, "disabled", code, ip, userAgent);
            return TokenValidationResponse.invalid("disabled");
        }

        AccessToken touch = new AccessToken();
        touch.setId(token.getId());
        touch.setLastUsedAt(LocalDateTime.now());
        tokenMapper.updateById(touch);
        logUse(token, user, "valid", code, ip, userAgent);

        TokenValidationResponse response = new TokenValidationResponse();
        response.setValid(true);
        response.setScope(token.getScope());
        response.setResources(splitResources(token.getResources()));
        response.setExpiresOn(token.getExpiresOn());
        response.setUserId(user.getId());
        response.setTenantId(user.getTenantId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        return response;
    }

    public List<AccessTokenLogView> logs(Long userId, int limit) {
        int size = Math.min(200, Math.max(1, limit));
        return logMapper.selectList(Wrappers.<AccessTokenLog>lambdaQuery()
                        .eq(AccessTokenLog::getUserId, userId)
                        .orderByDesc(AccessTokenLog::getCreatedAt)
                        .last("LIMIT " + size))
                .stream()
                .map(log -> {
                    AccessTokenLogView view = new AccessTokenLogView();
                    BeanUtils.copyProperties(log, view, "resources");
                    view.setResources(splitResources(log.getResources()));
                    return view;
                })
                .toList();
    }

    private void logUse(AccessToken token, SysUser user, String result,
                        String code, String ip, String userAgent) {
        AccessTokenLog entry = new AccessTokenLog();
        if (token != null) {
            entry.setTokenId(token.getId());
            entry.setTokenName(token.getName());
            entry.setScope(token.getScope());
            entry.setResources(token.getResources());
            entry.setTenantId(token.getTenantId());
            entry.setUserId(token.getUserId());
        }
        if (user != null) {
            entry.setUserId(user.getId());
            entry.setTenantId(user.getTenantId());
        }
        entry.setCodePrefix(code.length() > 10 ? code.substring(0, 10) : code);
        entry.setResult(result);
        entry.setIp(ip);
        entry.setUserAgent(userAgent);
        logMapper.insert(entry);
    }

    private AccessTokenView view(AccessToken token) {
        AccessTokenView view = new AccessTokenView();
        BeanUtils.copyProperties(token, view, "resources");
        view.setResources(splitResources(token.getResources()));
        return view;
    }

    private String joinResources(List<String> resources) {
        List<String> normalized = resources == null ? List.of() : resources.stream()
                .map(item -> item == null ? "" : item.trim().toLowerCase(Locale.ROOT))
                .filter(RESOURCES::contains)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            normalized = DEFAULT_RESOURCES;
        }
        return String.join(",", normalized);
    }

    private List<String> splitResources(String resources) {
        if (resources == null || resources.isBlank()) {
            return DEFAULT_RESOURCES;
        }
        return Arrays.stream(resources.split(","))
                .map(String::trim)
                .filter(RESOURCES::contains)
                .toList();
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            return "快速录入插件";
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
