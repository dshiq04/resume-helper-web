package com.jobtrack.system.service.impl;

import com.jobtrack.system.service.AccessTokenService;
import com.jobtrack.system.service.AccessTokenLogWriter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.pojo.dto.AccessTokenCreateDTO;
import com.jobtrack.system.pojo.vo.AccessTokenLogPageVO;
import com.jobtrack.system.pojo.vo.AccessTokenLogVO;
import com.jobtrack.system.pojo.vo.AccessTokenVO;
import com.jobtrack.system.pojo.vo.TokenValidationVO;
import com.jobtrack.system.pojo.po.AccessToken;
import com.jobtrack.system.pojo.po.AccessTokenLog;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.mapper.AccessTokenLogMapper;
import com.jobtrack.system.mapper.AccessTokenMapper;
import com.jobtrack.system.config.RedisSettings;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.messaging.AsyncTaskPublisher;
import com.jobtrack.system.messaging.TokenLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 授权码管理：用于浏览器扩展等外部调用方。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessTokenServiceImpl implements AccessTokenService {

    private static final Set<String> SCOPES = Set.of("read", "write", "readwrite");
    private static final Set<String> RESOURCES =
            Set.of("applications", "resumes", "statistics", "profile");
    private static final List<String> DEFAULT_RESOURCES =
            List.of("applications", "resumes", "statistics", "profile");

    private static final String CACHE_PREFIX = "access-code:";

    private final AccessTokenMapper tokenMapper;
    private final AccessTokenLogMapper logMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedisSettings redisSettings;
    private final AsyncTaskPublisher publisher;
    private final AccessTokenLogWriter logWriter;

    public List<AccessTokenVO> list(Long userId) {
        return tokenMapper.selectList(Wrappers.<AccessToken>lambdaQuery()
                        .eq(AccessToken::getUserId, userId)
                        .orderByDesc(AccessToken::getCreatedAt))
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public AccessTokenVO create(Long tenantId, Long userId, AccessTokenCreateDTO request) {
        String scope = request.getScope() == null
                ? "" : request.getScope().trim().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            throw BusinessException.badRequest("invalid_scope", "权限范围仅支持 read / write / readwrite");
        }
        LocalDate expiresOn = request.getExpiresOn();
        if (expiresOn != null && !expiresOn.isAfter(LocalDate.now())) {
            throw BusinessException.badRequest("invalid_expiry", "有效期必须晚于今天");
        }

        AccessToken token = new AccessToken();
        token.setTenantId(tenantId);
        token.setUserId(userId);
        token.setName(normalizeName(request.getName()));
        token.setScope(scope);
        token.setResources(joinResources(request.getResources()));
        token.setEnabled(true);
        token.setExpiresOn(expiresOn);
        token.setCode("rt_" + UUID.randomUUID().toString().replace("-", ""));
        tokenMapper.insert(token);
        cacheToken(token);
        return view(token);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        AccessToken token = tokenMapper.selectById(id);
        if (token == null || !userId.equals(token.getUserId())) {
            throw BusinessException.notFound("token_not_found", "授权码不存在或已删除");
        }
        // 逻辑删除：数据库保留记录，查询与校验都会自动排除。
        tokenMapper.deleteById(id);
        evictToken(token.getCode());
    }

    @Transactional
    public AccessTokenVO setEnabled(Long userId, Long id, boolean enabled) {
        AccessToken token = tokenMapper.selectById(id);
        if (token == null || !userId.equals(token.getUserId())) {
            throw BusinessException.notFound("token_not_found", "授权码不存在或已删除");
        }
        AccessToken update = new AccessToken();
        update.setId(id);
        update.setEnabled(enabled);
        tokenMapper.updateById(update);
        token.setEnabled(enabled);
        cacheToken(token);
        return view(token);
    }

    /**
     * 供扩展调用：校验授权码是否存在、是否过期、账号是否可用。
     * 该方法不需要登录态，因此 access_token 表在租户拦截器中已被忽略。
     */
    @Transactional
    public TokenValidationVO validate(String rawCode, String ip, String userAgent) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            logUse(null, null, "invalid", code, ip, userAgent);
            return TokenValidationVO.invalid("invalid");
        }
        AccessToken token = loadToken(code);
        if (token == null) {
            logUse(null, null, "invalid", code, ip, userAgent);
            return TokenValidationVO.invalid("invalid");
        }
        // 每次使用（含过期 / 停用）都刷新最近使用时间。
        AccessToken touch = new AccessToken();
        touch.setId(token.getId());
        touch.setLastUsedAt(LocalDateTime.now());
        tokenMapper.updateById(touch);

        if (token.getExpiresOn() != null && !token.getExpiresOn().isAfter(LocalDate.now())) {
            logUse(token, null, "expired", code, ip, userAgent);
            return TokenValidationVO.invalid("expired");
        }
        if (Boolean.FALSE.equals(token.getEnabled())) {
            logUse(token, null, "disabled", code, ip, userAgent);
            return TokenValidationVO.invalid("disabled");
        }
        SysUser user = userMapper.selectById(token.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            logUse(token, null, "disabled", code, ip, userAgent);
            return TokenValidationVO.invalid("disabled");
        }

        logUse(token, user, "valid", code, ip, userAgent);

        TokenValidationVO response = new TokenValidationVO();
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

    /**
     * 供 JWT 过滤器使用：授权码作为 Bearer 凭证访问 API。
     * 只做身份解析与最近使用时间刷新，不写使用日志（日志由校验接口记录）。
     */
    public AccessTokenService.TokenAuth authenticate(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            return null;
        }
        AccessToken token = loadToken(code);
        if (token == null
                || Boolean.FALSE.equals(token.getEnabled())
                || (token.getExpiresOn() != null && !token.getExpiresOn().isAfter(LocalDate.now()))) {
            return null;
        }
        SysUser user = userMapper.selectById(token.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return null;
        }
        AccessToken touch = new AccessToken();
        touch.setId(token.getId());
        touch.setLastUsedAt(LocalDateTime.now());
        tokenMapper.updateById(touch);
        return new AccessTokenService.TokenAuth(token, user);
    }

    public AccessTokenLogPageVO logs(Long userId, int page, int pageSize, String name, String result) {
        int current = Math.max(1, page);
        int size = Math.min(100, Math.max(1, pageSize));
        String normalizedResult = result == null ? "" : result.trim().toLowerCase(Locale.ROOT);
        String nameKeyword = name == null ? "" : name.trim();
        Page<AccessTokenLog> query = new Page<>(current, size);
        Page<AccessTokenLog> resultPage = logMapper.selectPage(query,
                Wrappers.<AccessTokenLog>lambdaQuery()
                        .eq(AccessTokenLog::getUserId, userId)
                        .like(!nameKeyword.isEmpty(), AccessTokenLog::getTokenName, nameKeyword)
                        .eq(!normalizedResult.isEmpty(), AccessTokenLog::getResult, normalizedResult)
                        .orderByDesc(AccessTokenLog::getCreatedAt));
        List<AccessTokenLogVO> items = resultPage.getRecords().stream()
                .map(log -> {
                    AccessTokenLogVO view = new AccessTokenLogVO();
                    BeanUtils.copyProperties(log, view, "resources");
                    view.setResources(splitResources(log.getResources()));
                    return view;
                })
                .toList();
        return new AccessTokenLogPageVO(items, resultPage.getTotal(), current, size);
    }

    /**
     * 生成 / 启动时把授权码写入 Redis Hash，校验与鉴权优先从 Redis 读取。
     * Redis 未启用时自动退回数据库。
     */
    public void warmCache() {
        if (!cacheEnabled()) {
            return;
        }
        try {
            List<AccessToken> tokens = tokenMapper.selectList(null);
            tokens.forEach(this::cacheToken);
            log.info("授权码缓存已同步: {} 条", tokens.size());
        } catch (Exception exception) {
            log.warn("授权码缓存同步失败: {}", exception.getMessage());
        }
    }

    private AccessToken loadToken(String code) {
        if (cacheEnabled()) {
            AccessToken cached = readCache(code);
            if (cached != null) {
                return cached;
            }
            // 缓存未命中视为无效（启动时会全量同步数据库中的授权码）。
            return null;
        }
        return tokenMapper.selectOne(Wrappers.<AccessToken>lambdaQuery()
                .eq(AccessToken::getCode, code));
    }

    private boolean cacheEnabled() {
        return redisSettings.isEnabled() && redisTemplate != null;
    }

    private String cacheKey(String code) {
        return redisSettings.getKeyPrefix() + CACHE_PREFIX + code;
    }

    private void cacheToken(AccessToken token) {
        if (!cacheEnabled() || token == null || token.getCode() == null) {
            return;
        }
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("id", String.valueOf(token.getId()));
            fields.put("tenantId", String.valueOf(token.getTenantId()));
            fields.put("userId", String.valueOf(token.getUserId()));
            fields.put("name", token.getName() == null ? "" : token.getName());
            fields.put("scope", token.getScope() == null ? "" : token.getScope());
            fields.put("resources", token.getResources() == null ? "" : token.getResources());
            fields.put("expiresOn", token.getExpiresOn() == null ? "" : token.getExpiresOn().toString());
            fields.put("enabled", Boolean.FALSE.equals(token.getEnabled()) ? "0" : "1");
            String key = cacheKey(token.getCode());
            redisTemplate.opsForHash().putAll(key, fields);
            if (token.getExpiresOn() == null) {
                redisTemplate.persist(key);
            } else {
                // 有效期与授权码一致：到 expiresOn 当天 00:00 整自动失效。
                Duration ttl = Duration.between(LocalDateTime.now(),
                        token.getExpiresOn().atStartOfDay());
                if (ttl.isNegative() || ttl.isZero()) {
                    // 已过有效期：缓存立即移除，校验时自然按未命中拒绝。
                    redisTemplate.delete(key);
                    return;
                }
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception exception) {
            log.warn("写入授权码缓存失败: {}", exception.getMessage());
        }
    }

    private AccessToken readCache(String code) {
        if (!cacheEnabled()) {
            return null;
        }
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(cacheKey(code));
            if (fields == null || fields.isEmpty()) {
                return null;
            }
            AccessToken token = new AccessToken();
            token.setId(Long.valueOf(String.valueOf(fields.get("id"))));
            token.setTenantId(Long.valueOf(String.valueOf(fields.get("tenantId"))));
            token.setUserId(Long.valueOf(String.valueOf(fields.get("userId"))));
            token.setName(String.valueOf(fields.get("name")));
            token.setScope(String.valueOf(fields.get("scope")));
            token.setResources(String.valueOf(fields.get("resources")));
            token.setCode(code);
            String expiresOn = String.valueOf(fields.getOrDefault("expiresOn", ""));
            if (!expiresOn.isBlank() && !"null".equals(expiresOn)) {
                token.setExpiresOn(LocalDate.parse(expiresOn));
            }
            token.setEnabled(!"0".equals(String.valueOf(fields.get("enabled"))));
            return token;
        } catch (Exception exception) {
            log.warn("读取授权码缓存失败: {}", exception.getMessage());
            return null;
        }
    }

    private void evictToken(String code) {
        if (!cacheEnabled() || code == null) {
            return;
        }
        try {
            redisTemplate.delete(cacheKey(code));
        } catch (Exception exception) {
            log.warn("删除授权码缓存失败: {}", exception.getMessage());
        }
    }

    private void logUse(AccessToken token, SysUser user, String result,
                        String code, String ip, String userAgent) {
        Long tenantId = null;
        Long userId = null;
        Long tokenId = null;
        String tokenName = null;
        String scope = null;
        String resources = null;
        if (token != null) {
            tokenId = token.getId();
            tokenName = token.getName();
            scope = token.getScope();
            resources = token.getResources();
            tenantId = token.getTenantId();
            userId = token.getUserId();
        }
        if (user != null) {
            userId = user.getId();
            tenantId = user.getTenantId();
        }
        TokenLogMessage message = new TokenLogMessage(tenantId, userId, tokenId, tokenName,
                code.length() > 10 ? code.substring(0, 10) : code,
                scope, resources, result, ip, userAgent);
        // Broker 不可用时降级为同步写库，保证日志不丢。
        if (!publisher.publishTokenLog(message)) {
            logWriter.save(message);
        }
    }

    private AccessTokenVO view(AccessToken token) {
        AccessTokenVO view = new AccessTokenVO();
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
