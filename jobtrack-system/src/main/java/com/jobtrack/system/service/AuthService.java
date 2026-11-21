package com.jobtrack.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.dto.AuthResponse;
import com.jobtrack.system.dto.ChangePasswordRequest;
import com.jobtrack.system.dto.LoginRequest;
import com.jobtrack.system.dto.OnlineStatusResponse;
import com.jobtrack.system.dto.ProfileRequest;
import com.jobtrack.system.dto.RegisterRequest;
import com.jobtrack.system.dto.UserView;
import com.jobtrack.system.entity.SysTenant;
import com.jobtrack.system.entity.SysUser;
import com.jobtrack.system.entity.UserProfile;
import com.jobtrack.system.mapper.UserProfileMapper;

import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.security.JwtService;
import com.jobtrack.system.security.SessionInfo;
import com.jobtrack.system.security.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");

    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final CaptchaService captchaService;
    private final UserProfileMapper profileMapper;
    private final AvatarService avatarService;

    public AuthResponse login(LoginRequest request) {
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());
        String username = normalizeUsername(request.getUsername());
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("invalid_credentials", "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw BusinessException.forbidden("account_disabled", "该账号已被停用");
        }
        return session(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());
        String username = normalizeUsername(request.getUsername());
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw BusinessException.badRequest("invalid_username",
                    "用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位");
        }
        Long existing = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw BusinessException.conflict("username_taken", "该用户名已被占用");
        }

        String tenantName = clean(request.getTenantName());
        if (tenantName.isEmpty()) {
            tenantName = username + " 的工作区";
        }
        if (tenantName.length() > 64) {
            throw BusinessException.badRequest("tenant_name_too_long", "工作区名称过长");
        }

        SysTenant tenant = new SysTenant();
        tenant.setCode("ws-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        tenant.setName(tenantName);
        tenantMapper.insert(tenant);

        SysUser user = new SysUser();
        user.setTenantId(tenant.getId());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(clean(request.getDisplayName()).isEmpty()
                ? username : clean(request.getDisplayName()));
        user.setRole("ADMIN");
        user.setEnabled(true);
        userMapper.insert(user);

        return session(user, tenant);
    }

    public UserView me(AuthPrincipal principal) {
        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            throw BusinessException.unauthorized("user_not_found", "账号不存在");
        }
        return view(user);
    }

    @Transactional
    public UserView updateProfile(AuthPrincipal principal, ProfileRequest request) {
        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            throw BusinessException.unauthorized("user_not_found", "账号不存在");
        }
        user.setDisplayName(request.getDisplayName().trim());
        userMapper.updateById(user);
        return view(user);
    }

    @Transactional
    public void changePassword(AuthPrincipal principal, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            throw BusinessException.unauthorized("user_not_found", "账号不存在");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("invalid_password", "当前密码不正确");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw BusinessException.badRequest("same_password", "新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        // 修改密码后让所有旧会话立即失效。
        sessionService.deleteByUser(principal.id(), principal.tenantId());
    }

    public void logout(AuthPrincipal principal) {
        sessionService.delete(principal.sessionId());
    }

    public OnlineStatusResponse online(AuthPrincipal principal) {
        return new OnlineStatusResponse(sessionService.mode(),
                sessionService.countOnline(principal.tenantId()), true);
    }

    private AuthResponse session(SysUser user) {
        SysTenant tenant = tenantMapper.selectById(user.getTenantId());
        return session(user, tenant);
    }

    private AuthResponse session(SysUser user, SysTenant tenant) {
        Duration ttl = Duration.ofHours(jwtService.ttlHours());
        String sessionId = sessionService.create(new SessionInfo(user.getId(), user.getTenantId(),
                user.getUsername(), user.getDisplayName(), user.getRole()), ttl);
        JwtService.IssuedToken issued = jwtService.issue(user, sessionId);
        return new AuthResponse(issued.token(), issued.expiresAt(), view(user, tenant));
    }

    private UserView view(SysUser user) {
        return view(user, tenantMapper.selectById(user.getTenantId()));
    }

    private UserView view(SysUser user, SysTenant tenant) {
        // 登录时还没有 TenantContext，按 user_id 直接查询。
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(user.getId());
        String avatarUrl = profile == null ? null
                : avatarService.url(user.getId(), profile.getAvatarUrl());
        return new UserView(user.getId(), user.getTenantId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), tenant == null ? "" : tenant.getName(),
                avatarUrl);
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
