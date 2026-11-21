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
import com.jobtrack.system.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
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
    private final ObjectStorageService objectStorage;
    private final AvatarService avatarService;

    private static final Set<String> AVATAR_TYPES = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;

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
        // 头像通过独立接口上传，普通资料保存不能覆盖或清空它。
        String avatarKey = profile.getAvatarUrl();
        BeanUtils.copyProperties(request, profile);
        profile.setAvatarUrl(avatarKey);
        if (creating) {
            profileMapper.insert(profile);
        } else {
            profile.setUpdatedAt(LocalDateTime.now());
            profileMapper.updateById(profile);
        }
        return view(profile);
    }

    @Transactional
    public UserProfileView uploadAvatar(AuthPrincipal principal, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("file_required", "请选择头像图片");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw BusinessException.badRequest("file_too_large", "头像不能超过 5 MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!AVATAR_TYPES.contains(extension)) {
            throw BusinessException.badRequest("unsupported_file_type",
                    "头像仅支持 PNG、JPG、JPEG、WEBP、GIF");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception exception) {
            throw BusinessException.badRequest("file_unreadable", "无法读取头像文件");
        }
        String key = "avatars/" + principal.tenantId() + "/" + principal.id()
                + "-" + System.currentTimeMillis() + "." + extension;
        objectStorage.put(key, content, contentType(extension));

        UserProfile profile = profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, principal.id()));
        String previous = profile == null ? null : profile.getAvatarUrl();
        if (profile == null) {
            profile = new UserProfile();
            profile.setTenantId(principal.tenantId());
            profile.setUserId(principal.id());
            profile.setAvatarUrl(key);
            profile.setUpdatedAt(LocalDateTime.now());
            profileMapper.insert(profile);
        } else {
            profile.setAvatarUrl(key);
            profile.setUpdatedAt(LocalDateTime.now());
            profileMapper.updateById(profile);
        }
        if (previous != null && !previous.isBlank() && !previous.equals(key)) {
            objectStorage.delete(previous);
        }
        return view(profile);
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
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
        view.setAvatarUrl(avatarService.url(profile.getUserId(), profile.getAvatarUrl()));
        return view;
    }

    private UserView view(SysUser user) {
        SysTenant tenant = tenantMapper.selectById(user.getTenantId());
        return new UserView(user.getId(), user.getTenantId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), tenant == null ? "" : tenant.getName());
    }
}
