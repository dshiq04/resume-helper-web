package com.jobtrack.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.dto.UserProfileRequest;
import com.jobtrack.system.dto.UserProfileView;
import com.jobtrack.system.dto.UserView;
import com.jobtrack.system.entity.SysTenant;
import com.jobtrack.system.entity.SysUser;
import com.jobtrack.system.entity.UserProfile;
import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.mapper.UserProfileMapper;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.security.JwtService;
import com.jobtrack.system.security.SessionInfo;
import com.jobtrack.system.security.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 账户设置：扩展个人资料与用户名修改。
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");

    private final UserProfileMapper profileMapper;
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final SessionService sessionService;
    private final JwtService jwtService;

    public UserProfileView profile(AuthPrincipal principal) {
        UserProfile profile = profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, principal.id()));
        if (profile == null) {
            UserProfileView empty = new UserProfileView();
            empty.setUserId(principal.id());
            return empty;
        }
        return view(profile);
    }

    @Transactional
    public UserProfileView saveProfile(AuthPrincipal principal, UserProfileRequest request) {
        UserProfile profile = profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, principal.id()));
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setTenantId(principal.tenantId());
            profile.setUserId(principal.id());
        }
        BeanUtils.copyProperties(request, profile);
        if (creating) {
            profileMapper.insert(profile);
        } else {
            profile.setUpdatedAt(LocalDateTime.now());
            profileMapper.updateById(profile);
        }
        return view(profile);
    }

    @Transactional
    public UserView changeUsername(AuthPrincipal principal, String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw BusinessException.badRequest("invalid_username",
                    "用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位");
        }
        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            throw BusinessException.unauthorized("user_not_found", "账号不存在");
        }
        if (username.equalsIgnoreCase(user.getUsername())) {
            return view(user);
        }
        Long existing = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw BusinessException.conflict("username_taken", "该用户名已被占用");
        }
        user.setUsername(username);
        userMapper.updateById(user);

        // 同步当前会话，避免顶栏与授权信息继续显示旧用户名。
        if (principal.sessionId() != null && !principal.sessionId().isBlank()) {
            sessionService.save(principal.sessionId(),
                    new SessionInfo(user.getId(), user.getTenantId(), user.getUsername(),
                            user.getDisplayName(), user.getRole()),
                    Duration.ofHours(jwtService.ttlHours()));
        }
        return view(user);
    }

    private UserProfileView view(UserProfile profile) {
        UserProfileView view = new UserProfileView();
        BeanUtils.copyProperties(profile, view);
        return view;
    }

    private UserView view(SysUser user) {
        SysTenant tenant = tenantMapper.selectById(user.getTenantId());
        return new UserView(user.getId(), user.getTenantId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), tenant == null ? "" : tenant.getName());
    }
}
