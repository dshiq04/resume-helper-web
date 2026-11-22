package com.jobtrack.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.mapper.UserProfileMapper;
import com.jobtrack.system.messaging.AsyncTaskPublisher;
import com.jobtrack.system.messaging.UserBanMessage;
import com.jobtrack.system.pojo.dto.AccessTokenCreateDTO;
import com.jobtrack.system.pojo.dto.AdminPasswordDTO;
import com.jobtrack.system.pojo.dto.AdminStatusDTO;
import com.jobtrack.system.pojo.dto.AdminUserUpdateDTO;
import com.jobtrack.system.pojo.dto.ContactUpdateDTO;
import com.jobtrack.system.pojo.dto.UserProfileDTO;
import com.jobtrack.system.pojo.po.SysTenant;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.pojo.po.UserProfile;
import com.jobtrack.system.pojo.vo.AccessTokenLogPageVO;
import com.jobtrack.system.pojo.vo.AccessTokenVO;
import com.jobtrack.system.pojo.vo.AdminUserDetailVO;
import com.jobtrack.system.pojo.vo.AdminUserPageVO;
import com.jobtrack.system.pojo.vo.AdminUserVO;
import com.jobtrack.system.pojo.vo.OnlineUserVO;
import com.jobtrack.system.pojo.vo.PermissionVO;
import com.jobtrack.system.pojo.vo.UserProfileVO;
import com.jobtrack.system.security.SessionInfo;
import com.jobtrack.system.security.SessionService;
import com.jobtrack.system.service.AccessTokenService;
import com.jobtrack.system.service.AdminService;
import com.jobtrack.system.service.AvatarService;
import com.jobtrack.system.service.BanService;
import com.jobtrack.system.service.CacheService;
import com.jobtrack.system.service.ContactService;
import com.jobtrack.system.service.PermissionService;
import com.jobtrack.system.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 管理员控制台实现。所有查询都跨租户进行，写入时临时切换到目标用户的租户上下文，
 * 保证 MyBatis-Plus 租户拦截器不会把数据写到管理员自己的租户下。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{6,15}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");
    private static final Set<String> AVATAR_TYPES = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> STATUSES = Set.of("NORMAL", "CANCELLED", "BANNED");

    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final UserProfileMapper profileMapper;
    private final PermissionService permissionService;
    private final AccessTokenService accessTokenService;
    private final BanService banService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final ContactService contactService;
    private final AvatarService avatarService;
    private final ObjectStorageService objectStorage;
    private final AsyncTaskPublisher publisher;
    private final CacheService cacheService;
    private final JdbcTemplate jdbcTemplate;

    public AdminUserPageVO listUsers(String search, String status, int page, int pageSize) {
        int current = Math.max(1, page);
        int size = Math.min(200, Math.max(1, pageSize));
        String keyword = clean(search);
        String normalizedStatus = clean(status).toUpperCase(Locale.ROOT);
        var query = Wrappers.<SysUser>lambdaQuery();
        if (!keyword.isEmpty()) {
            query.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getDisplayName, keyword));
        }
        if (STATUSES.contains(normalizedStatus)) {
            if ("NORMAL".equals(normalizedStatus)) {
                query.and(w -> w.eq(SysUser::getStatus, "NORMAL")
                        .or().isNull(SysUser::getStatus));
            } else {
                query.eq(SysUser::getStatus, normalizedStatus);
            }
        }
        query.orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(current, size), query);

        Map<Long, String> tenantNames = tenantNames();
        Map<Long, Integer> online = onlineCounts();
        List<AdminUserVO> items = result.getRecords().stream()
                .map(user -> view(user, tenantNames, online))
                .toList();
        return new AdminUserPageVO(items, result.getTotal(), current, size);
    }

    public AdminUserDetailVO detail(Long userId) {
        SysUser user = requireUser(userId);
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(userId);
        return new AdminUserDetailVO(view(user, tenantNames(), onlineCounts()), profileView(profile, userId));
    }

    @Transactional
    public AdminUserVO updateUser(Long userId, AdminUserUpdateDTO request) {
        SysUser user = requireUser(userId);
        String username = clean(request.getUsername());
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw BusinessException.badRequest("invalid_username",
                    "用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位");
        }
        Long duplicated = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(SysUser::getId, userId)
                .and(w -> w.isNull(SysUser::getStatus)
                        .or().ne(SysUser::getStatus, "CANCELLED")));
        if (duplicated != null && duplicated > 0) {
            throw BusinessException.conflict("username_taken", "该用户名已被其他账号占用");
        }
        user.setUsername(username);
        user.setDisplayName(clean(request.getDisplayName()));
        userMapper.updateById(user);

        SysTenant tenant = tenantMapper.selectById(user.getTenantId());
        if (tenant != null) {
            tenant.setName(clean(request.getTenantName()));
            tenantMapper.updateById(tenant);
        }
        return view(user, tenantNames(), onlineCounts());
    }

    @Transactional
    public UserProfileVO saveProfile(Long userId, UserProfileDTO request) {
        SysUser user = requireUser(userId);
        UserProfile profile = inTenant(user.getTenantId(), () ->
                profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                        .eq(UserProfile::getUserId, userId)));
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setTenantId(user.getTenantId());
            profile.setUserId(userId);
        }
        String avatarKey = profile.getAvatarUrl();
        BeanUtils.copyProperties(request, profile);
        profile.setAvatarUrl(avatarKey);
        profile.setUpdatedAt(LocalDateTime.now());
        UserProfile target = profile;
        inTenant(user.getTenantId(), () -> {
            if (creating) {
                profileMapper.insert(target);
            } else {
                profileMapper.updateById(target);
            }
            return null;
        });
        return profileView(target, userId);
    }

    @Transactional
    public UserProfileVO saveContact(Long userId, ContactUpdateDTO request) {
        SysUser user = requireUser(userId);
        String phone = clean(request.getPhone());
        String email = clean(request.getEmail());
        if (!phone.isEmpty()) {
            String full = fullPhone(request.getCountryCode(), phone);
            if (!PHONE_PATTERN.matcher(phoneDigits(full)).matches()) {
                throw BusinessException.badRequest("invalid_phone", "请输入正确的手机号");
            }
            phone = full;
        }
        if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
            throw BusinessException.badRequest("invalid_email", "请输入正确的邮箱地址");
        }
        if (phone.isEmpty() && email.isEmpty()) {
            throw BusinessException.badRequest("contact_required",
                    "手机号和邮箱至少保留一项，用于修改密码时验证身份");
        }
        contactService.ensureAvailable(userId, phone, email);

        UserProfile profile = inTenant(user.getTenantId(), () ->
                profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                        .eq(UserProfile::getUserId, userId)));
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setTenantId(user.getTenantId());
            profile.setUserId(userId);
        }
        profile.setPhone(phone.isEmpty() ? null : phone);
        profile.setEmail(email.isEmpty() ? null : email);
        profile.setUpdatedAt(LocalDateTime.now());
        UserProfile target = profile;
        inTenant(user.getTenantId(), () -> {
            if (creating) {
                profileMapper.insert(target);
            } else {
                profileMapper.updateById(target);
            }
            return null;
        });
        return profileView(target, userId);
    }

    @Transactional
    public UserProfileVO uploadAvatar(Long userId, MultipartFile file) {
        SysUser user = requireUser(userId);
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
        String key = "avatars/" + user.getTenantId() + "/" + userId
                + "-" + System.currentTimeMillis() + "." + extension;
        objectStorage.put(key, content, contentType(extension));

        UserProfile profile = inTenant(user.getTenantId(), () ->
                profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                        .eq(UserProfile::getUserId, userId)));
        String previous = profile == null ? null : profile.getAvatarUrl();
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setTenantId(user.getTenantId());
            profile.setUserId(userId);
        }
        profile.setAvatarUrl(key);
        profile.setUpdatedAt(LocalDateTime.now());
        UserProfile target = profile;
        inTenant(user.getTenantId(), () -> {
            if (creating) {
                profileMapper.insert(target);
            } else {
                profileMapper.updateById(target);
            }
            return null;
        });
        if (previous != null && !previous.isBlank() && !previous.equals(key)) {
            if (!publisher.publishFileCleanup(previous)) {
                objectStorage.delete(previous);
            }
        }
        return profileView(target, userId);
    }

    @Transactional
    public AdminUserVO resetPassword(Long userId, AdminPasswordDTO request) {
        SysUser user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        // 修改密码后：注销全部登录令牌，并删除 Redis 中的在线状态与令牌信息。
        sessionService.deleteByUser(userId, user.getTenantId());
        return view(user, tenantNames(), onlineCounts());
    }

    @Transactional
    public AdminUserVO updateStatus(Long operatorId, Long userId, AdminStatusDTO request) {
        SysUser user = requireUser(userId);
        String status = clean(request.getStatus()).toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw BusinessException.badRequest("invalid_status", "不支持的用户状态");
        }
        if (operatorId != null && operatorId.equals(userId) && !"NORMAL".equals(status)) {
            throw BusinessException.badRequest("cannot_change_self_status", "不能封禁或注销当前登录的管理员账号");
        }

        LocalDateTime until = null;
        String banType = null;
        if ("BANNED".equals(status)) {
            banType = clean(request.getBanType()).toUpperCase(Locale.ROOT);
            if (banType.isEmpty()) {
                banType = "PERMANENT";
            }
            if ("TEMPORARY".equals(banType)) {
                until = request.getBanUntil();
                if (until == null || !until.isAfter(LocalDateTime.now())) {
                    throw BusinessException.badRequest("ban_until_required",
                            "暂时封禁请选择未来的解封时间");
                }
            } else if (!"PERMANENT".equals(banType)) {
                throw BusinessException.badRequest("invalid_ban_type", "不支持的封禁类型");
            } else {
                banType = "PERMANENT";
            }
        }

        user.setStatus(status);
        if ("BANNED".equals(status)) {
            user.setBanType(banType);
            user.setBanUntil(until);
            user.setBannedAt(LocalDateTime.now());
        } else {
            user.setBanType(null);
            user.setBanUntil(null);
            user.setBannedAt(null);
        }
        userMapper.updateById(user);

        boolean banned = "BANNED".equals(status);
        // 先同步更新本实例的封禁集合，再通过 RabbitMQ 通知其他实例。
        banService.apply(userId, banned, until);
        publisher.publishUserBan(new UserBanMessage(userId, banned, until));
        // 封禁 / 注销 / 恢复都让旧登录态立即失效。
        sessionService.deleteByUser(userId, user.getTenantId());
        return view(user, tenantNames(), onlineCounts());
    }

    @Transactional
    public void deleteUser(Long operatorId, Long userId) {
        SysUser user = requireUser(userId);
        if (operatorId != null && operatorId.equals(userId)) {
            throw BusinessException.badRequest("cannot_delete_self", "不能删除当前登录的管理员账号");
        }
        if (permissionService.has(userId, PermissionService.ADMIN_CONSOLE)
                && countPermissionHolders(PermissionService.ADMIN_CONSOLE) <= 1) {
            throw BusinessException.badRequest("last_admin", "系统至少需要保留一个管理员控制台账号");
        }

        Long tenantId = user.getTenantId();
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(userId);
        List<String> fileKeys = new ArrayList<>();
        if (profile != null && profile.getAvatarUrl() != null && !profile.getAvatarUrl().isBlank()) {
            fileKeys.add(profile.getAvatarUrl());
        }
        fileKeys.addAll(jdbcTemplate.queryForList(
                "SELECT stored_name FROM resumes WHERE tenant_id = ?", String.class, tenantId));

        // 先清理 Redis 中的授权码缓存与登录会话，再删除数据库。
        accessTokenService.evictUserCache(userId);
        sessionService.deleteByUser(userId, tenantId);
        banService.apply(userId, false, null);

        jdbcTemplate.update("DELETE FROM application_changes WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM applications WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM deleted_applications WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM resumes WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM tenant_setting WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM user_agreement WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM access_token_log WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM access_token WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_permission WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE tenant_id = ?", Long.class, tenantId);
        if (remaining == null || remaining == 0) {
            jdbcTemplate.update("DELETE FROM sys_tenant WHERE id = ?", tenantId);
        }
        // 投递列表缓存按租户版本号失效，避免删除后仍读到旧数据。
        cacheService.bumpEpoch();

        for (String key : fileKeys) {
            if (key != null && !key.isBlank()) {
                if (!publisher.publishFileCleanup(key)) {
                    objectStorage.delete(key);
                }
            }
        }
        log.info("管理员已删除用户 {}（租户 {}），清理文件 {} 个", userId, tenantId, fileKeys.size());
    }

    public List<AccessTokenVO> tokens(Long userId) {
        requireUser(userId);
        return accessTokenService.list(userId);
    }

    @Transactional
    public AccessTokenVO createToken(Long userId, AccessTokenCreateDTO request) {
        SysUser user = requireUser(userId);
        return accessTokenService.create(user.getTenantId(), userId, request);
    }

    @Transactional
    public AccessTokenVO setTokenEnabled(Long userId, Long tokenId, boolean enabled) {
        requireUser(userId);
        return accessTokenService.setEnabled(userId, tokenId, enabled);
    }

    @Transactional
    public void deleteToken(Long userId, Long tokenId) {
        requireUser(userId);
        accessTokenService.delete(userId, tokenId);
    }

    public AccessTokenLogPageVO tokenLogs(Long userId, int page, int pageSize,
                                          String name, String result) {
        requireUser(userId);
        return accessTokenService.logs(userId, page, pageSize, name, result);
    }

    public List<OnlineUserVO> onlineUsers() {
        Map<Long, List<SessionService.OnlineSession>> grouped = new LinkedHashMap<>();
        for (SessionService.OnlineSession session : sessionService.listSessions()) {
            if (session.info() == null || session.info().getUserId() == null) {
                continue;
            }
            grouped.computeIfAbsent(session.info().getUserId(), key -> new ArrayList<>())
                    .add(session);
        }
        Map<Long, String> tenantNames = tenantNames();
        List<OnlineUserVO> result = new ArrayList<>();
        grouped.forEach((userId, sessions) -> {
            SysUser user = userMapper.selectById(userId);
            if (user == null) {
                return;
            }
            SessionInfo info = sessions.get(0).info();
            UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(userId);
            OnlineUserVO view = new OnlineUserVO();
            view.setUserId(userId);
            view.setTenantId(user.getTenantId());
            view.setUsername(user.getUsername());
            view.setDisplayName(user.getDisplayName());
            view.setTenantName(tenantNames.getOrDefault(user.getTenantId(), ""));
            view.setAvatarUrl(profile == null ? null
                    : avatarService.url(userId, profile.getAvatarUrl()));
            view.setSessions(sessions.size());
            view.setRemainingSeconds(sessions.stream()
                    .map(SessionService.OnlineSession::expiresInSeconds)
                    .sorted(Comparator.reverseOrder())
                    .toList());
            // 会话中的用户名以 Redis 快照为准，避免与数据库不一致时显示为空。
            if (view.getUsername() == null) {
                view.setUsername(info.getUsername());
            }
            result.add(view);
        });
        result.sort(Comparator.comparing(OnlineUserVO::getUsername,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    public int kick(Long operatorId, Long userId) {
        SysUser user = requireUser(userId);
        if (operatorId != null && operatorId.equals(userId)) {
            throw BusinessException.badRequest("cannot_kick_self", "不能踢出当前登录的管理员账号");
        }
        int sessions = sessionService.listSessions().stream()
                .filter(item -> item.info() != null && userId.equals(item.info().getUserId()))
                .toList().size();
        // 注销令牌 + 删除 Redis 中的在线状态与令牌信息。
        sessionService.deleteByUser(userId, user.getTenantId());
        return sessions;
    }

    public List<PermissionVO> permissions() {
        return permissionService.listPermissions().stream()
                .map(permission -> new PermissionVO(permission.getId(), permission.getCode(),
                        permission.getName(), permission.getDescription()))
                .toList();
    }

    public List<String> userPermissions(Long userId) {
        requireUser(userId);
        return permissionService.permissionsOf(userId);
    }

    @Transactional
    public AdminUserVO grantPermission(Long operatorId, Long userId, String code) {
        SysUser user = requireUser(userId);
        permissionService.grant(userId, clean(code));
        invalidatePermissionSessions(operatorId, userId, user.getTenantId(), false);
        return view(user, tenantNames(), onlineCounts());
    }

    @Transactional
    public AdminUserVO revokePermission(Long operatorId, Long userId, String code) {
        SysUser user = requireUser(userId);
        String normalized = clean(code);
        if (PermissionService.ADMIN_CONSOLE.equalsIgnoreCase(normalized)
                && permissionService.has(userId, PermissionService.ADMIN_CONSOLE)
                && countPermissionHolders(PermissionService.ADMIN_CONSOLE) <= 1) {
            throw BusinessException.badRequest("last_admin", "系统至少需要保留一个管理员控制台账号");
        }
        permissionService.revoke(userId, normalized);
        invalidatePermissionSessions(operatorId, userId, user.getTenantId(), true);
        return view(user, tenantNames(), onlineCounts());
    }

    private void invalidatePermissionSessions(Long operatorId, Long userId,
                                              Long tenantId, boolean includeSelf) {
        // 权限写入 Redis 会话，变更后让目标用户重新登录；授予自己权限时保留当前会话。
        if (!includeSelf && operatorId != null && operatorId.equals(userId)) {
            return;
        }
        sessionService.deleteByUser(userId, tenantId);
    }

    private long countPermissionHolders(String code) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT up.user_id) FROM sys_user_permission up "
                        + "JOIN sys_permission p ON p.id = up.permission_id WHERE p.code = ?",
                Long.class, code);
        return count == null ? 0 : count;
    }

    private SysUser requireUser(Long userId) {
        if (userId == null) {
            throw BusinessException.badRequest("user_required", "请选择用户");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("user_not_found", "用户不存在");
        }
        return user;
    }

    private AdminUserVO view(SysUser user, Map<Long, String> tenantNames,
                             Map<Long, Integer> online) {
        // 暂时封禁到期后自动恢复正常，避免管理员列表一直停留在已封禁。
        if ("BANNED".equalsIgnoreCase(user.getStatus())
                && user.getBanUntil() != null
                && !user.getBanUntil().isAfter(LocalDateTime.now())) {
            user.setStatus("NORMAL");
            user.setBanType(null);
            user.setBanUntil(null);
            user.setBannedAt(null);
            userMapper.updateById(user);
        }
        List<String> permissions = permissionService.permissionsOf(user.getId());
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(user.getId());
        AdminUserVO view = new AdminUserVO();
        view.setId(user.getId());
        view.setTenantId(user.getTenantId());
        view.setUsername(user.getUsername());
        view.setDisplayName(user.getDisplayName());
        view.setRole(PermissionService.roleOf(permissions));
        view.setStatus(statusOf(user));
        view.setEnabled(user.getEnabled());
        view.setTenantName(tenantNames.getOrDefault(user.getTenantId(), ""));
        view.setAvatarUrl(profile == null ? null
                : avatarService.url(user.getId(), profile.getAvatarUrl()));
        view.setPermissions(permissions);
        int sessions = online.getOrDefault(user.getId(), 0);
        view.setOnline(sessions > 0);
        view.setOnlineSessions(sessions);
        view.setBanType(user.getBanType());
        view.setBanUntil(user.getBanUntil());
        view.setBannedAt(user.getBannedAt());
        view.setCreatedAt(user.getCreatedAt());
        view.setUpdatedAt(user.getUpdatedAt());
        return view;
    }

    private UserProfileVO profileView(UserProfile profile, Long userId) {
        if (profile == null) {
            UserProfileVO empty = new UserProfileVO();
            empty.setUserId(userId);
            return empty;
        }
        UserProfileVO view = new UserProfileVO();
        BeanUtils.copyProperties(profile, view);
        view.setAvatarUrl(avatarService.url(userId, profile.getAvatarUrl()));
        return view;
    }

    private Map<Long, String> tenantNames() {
        Map<Long, String> names = new HashMap<>();
        for (SysTenant tenant : tenantMapper.selectList(null)) {
            names.put(tenant.getId(), tenant.getName());
        }
        return names;
    }

    private Map<Long, Integer> onlineCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        for (SessionService.OnlineSession session : sessionService.listSessions()) {
            if (session.info() != null && session.info().getUserId() != null) {
                counts.merge(session.info().getUserId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private <T> T inTenant(Long tenantId, Supplier<T> action) {
        Long previous = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    private String statusOf(SysUser user) {
        return user.getStatus() == null || user.getStatus().isBlank()
                ? "NORMAL" : user.getStatus();
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String fullPhone(String countryCode, String phone) {
        String code = clean(countryCode);
        if (code.isEmpty()) {
            return phone;
        }
        if (!code.startsWith("+")) {
            code = "+" + code;
        }
        String value = phone.replaceAll("[\\s-]", "");
        if (code.length() > 1 && value.startsWith(code)) {
            value = value.substring(code.length());
        }
        return code + value;
    }

    private String phoneDigits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }
}
