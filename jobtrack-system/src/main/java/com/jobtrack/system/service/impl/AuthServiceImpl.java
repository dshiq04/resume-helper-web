package com.jobtrack.system.service.impl;

import com.jobtrack.system.service.PermissionService;
import com.jobtrack.system.service.AuthService;
import com.jobtrack.system.service.CaptchaService;
import com.jobtrack.system.service.AvatarService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.pojo.AuthResponse;
import com.jobtrack.system.pojo.ChangePasswordRequest;
import com.jobtrack.system.pojo.ForgotPasswordResetRequest;
import com.jobtrack.system.pojo.ForgotPasswordVerifyRequest;
import com.jobtrack.system.pojo.LoginRequest;
import com.jobtrack.system.pojo.OnlineStatusResponse;
import com.jobtrack.system.pojo.ProfileRequest;
import com.jobtrack.system.pojo.RegisterRequest;
import com.jobtrack.system.pojo.UserView;
import com.jobtrack.system.pojo.SysTenant;
import com.jobtrack.system.pojo.SysUser;
import com.jobtrack.system.pojo.UserProfile;
import com.jobtrack.system.mapper.UserAgreementMapper;
import com.jobtrack.system.mapper.UserProfileMapper;

import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.security.JwtService;
import com.jobtrack.system.security.SessionInfo;
import com.jobtrack.system.security.SessionService;
import com.jobtrack.system.messaging.AsyncTaskPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "用户名或密码错误";
    private static final String CONTACT_TAKEN_MESSAGE =
            "当前手机号/邮箱已存在，请更换其他的手机号/邮箱或者直接登录";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{6,15}$");

    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final CaptchaService captchaService;
    private final UserProfileMapper profileMapper;
    private final UserAgreementMapper agreementMapper;
    private final AvatarService avatarService;
    private final PermissionService permissionService;
    private final AsyncTaskPublisher publisher;

    public AuthResponse login(LoginRequest request) {
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());
        String username = normalizeUsername(request.getUsername());
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("invalid_credentials", INVALID_CREDENTIALS_MESSAGE);
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw BusinessException.forbidden("account_disabled", "该账号已被停用");
        }
        if (user.getStatus() != null && "CANCELLED".equalsIgnoreCase(user.getStatus())) {
            throw BusinessException.forbidden("account_cancelled", "该账号已注销");
        }
        return session(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());
        if (!Boolean.TRUE.equals(request.getAgreementAccepted())) {
            throw BusinessException.badRequest("agreement_required", "请先阅读并同意用户协议");
        }
        String contactType = clean(request.getContactType()).toLowerCase(Locale.ROOT);
        String phone = clean(request.getPhone());
        String email = clean(request.getEmail());
        if ("phone".equals(contactType)) {
            email = "";
        } else if ("email".equals(contactType)) {
            phone = "";
        } else if (!contactType.isEmpty()) {
            throw BusinessException.badRequest("invalid_contact_type", "不支持的联系方式类型");
        }
        if (phone.isEmpty() && email.isEmpty()) {
            throw BusinessException.badRequest("contact_required", "请选择手机号或邮箱其中一种");
        }
        if (!phone.isEmpty()
                && !PHONE_PATTERN.matcher(phoneDigits(fullPhone(request.getCountryCode(), phone))).matches()) {
            throw BusinessException.badRequest("invalid_phone", "请输入正确的手机号");
        }
        if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
            throw BusinessException.badRequest("invalid_email", "请输入正确的邮箱地址");
        }
        if (request.getPassword() == null
                || !request.getPassword().equals(request.getConfirmPassword())) {
            throw BusinessException.badRequest("password_mismatch", "两次输入的密码不一致");
        }
        String username = normalizeUsername(request.getUsername());
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw BusinessException.badRequest("invalid_username",
                    "用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位");
        }
        Long existing = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw BusinessException.conflict("username_taken",
                    "该用户名已被占用，请更换其他用户名或者直接登录");
        }
        // 已注销账号占用的手机号/邮箱允许被重新注册，这里只检查未注销账号。
        for (UserProfile saved : profileMapper.selectActiveContactsIgnoreTenant()) {
            if (!phone.isEmpty() && samePhone(fullPhone(request.getCountryCode(), phone), saved.getPhone())) {
                throw BusinessException.conflict("contact_taken", CONTACT_TAKEN_MESSAGE);
            }
            if (!email.isEmpty() && saved.getEmail() != null
                    && email.equalsIgnoreCase(saved.getEmail().trim())) {
                throw BusinessException.conflict("contact_taken", CONTACT_TAKEN_MESSAGE);
            }
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
        user.setRole("USER");
        user.setEnabled(true);
        userMapper.insert(user);
        // 普通用户权限写入用户权限对应表。
        permissionService.grant(user.getId(), "USER");
        // 注册成功即记录该用户已同意协议。
        agreementMapper.insertIgnoreTenant(tenant.getId(), user.getId(), LocalDateTime.now());

        UserProfile profile = new UserProfile();
        profile.setTenantId(tenant.getId());
        profile.setUserId(user.getId());
        profile.setPhone(phone.isEmpty() ? null : fullPhone(request.getCountryCode(), phone));
        profile.setEmail(email.isEmpty() ? null : email);
        profileMapper.insert(profile);

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
            // 当前密码错误属于输入校验失败，不能返回 401，否则前端会误判为登录失效。
            throw BusinessException.badRequest("invalid_password", "当前密码不正确");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw BusinessException.badRequest("same_password", "新密码不能与当前密码相同");
        }
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(principal.id());
        String storedPhone = profile == null ? "" : clean(profile.getPhone());
        String storedEmail = profile == null ? "" : clean(profile.getEmail());
        if (storedPhone.isEmpty() && storedEmail.isEmpty()) {
            throw BusinessException.badRequest("contact_not_set",
                    "请先在账号与安全中设置手机号或邮箱");
        }
        String contactType = clean(request.getContactType()).toLowerCase(Locale.ROOT);
        String contact = clean(request.getContact());
        if (contact.isEmpty()) {
            String message = "email".equals(contactType) ? "请输入邮箱"
                    : "phone".equals(contactType) ? "请输入手机号" : "请输入手机号或邮箱";
            throw BusinessException.badRequest("contact_required", message);
        }
        if ("phone".equals(contactType)) {
            String contactPhone = phoneDigits(contact);
            if (!PHONE_PATTERN.matcher(contactPhone).matches()) {
                throw BusinessException.badRequest("invalid_phone", "请输入正确的手机号");
            }
            String storedPhoneDigits = phoneDigits(storedPhone);
            // 兼容历史数据：账号里可能没有保存国家区号，只保存了本地号码。
            boolean matched = !storedPhoneDigits.isEmpty()
                    && (contactPhone.equals(storedPhoneDigits)
                    || storedPhoneDigits.endsWith(contactPhone)
                    || contactPhone.endsWith(storedPhoneDigits));
            if (!matched) {
                throw BusinessException.badRequest("contact_mismatch", "手机号与账号不符");
            }
        } else if ("email".equals(contactType)) {
            if (!EMAIL_PATTERN.matcher(contact).matches()) {
                throw BusinessException.badRequest("invalid_email", "请输入正确的邮箱地址");
            }
            if (storedEmail.isEmpty() || !contact.equalsIgnoreCase(storedEmail)) {
                throw BusinessException.badRequest("contact_mismatch", "邮箱与账号不符");
            }
        } else if (!contactType.isEmpty()) {
            throw BusinessException.badRequest("invalid_contact_type", "不支持的联系方式类型");
        } else {
            // 兼容旧客户端：未传类型时按手机号或邮箱自动识别。
            boolean emailMatched = !storedEmail.isEmpty() && contact.equalsIgnoreCase(storedEmail);
            String contactPhone = normalizePhone(contact);
            String storedPhoneDigits = normalizePhone(storedPhone);
            boolean phoneMatched = contactPhone.length() >= 7 && !storedPhoneDigits.isEmpty()
                    && (contactPhone.equals(storedPhoneDigits)
                    || storedPhoneDigits.endsWith(contactPhone)
                    || contactPhone.endsWith(storedPhoneDigits));
            if (!emailMatched && !phoneMatched) {
                throw BusinessException.badRequest("contact_mismatch",
                        "手机号或邮箱与账号不符");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        // 修改密码后让所有旧会话立即失效。
        sessionService.deleteByUser(principal.id(), principal.tenantId());
    }

    /** 忘记密码第一步：验证用户名与保留的手机号/邮箱，验证方式与修改密码一致。 */
    public void forgotPasswordVerify(ForgotPasswordVerifyRequest request) {
        SysUser user = findUserForForgotPassword(request.getUsername());
        verifyForgotContact(user, request.getContactType(), request.getContact());
    }

    @Transactional
    public void forgotPasswordReset(ForgotPasswordResetRequest request) {
        SysUser user = findUserForForgotPassword(request.getUsername());
        verifyForgotContact(user, request.getContactType(), request.getContact());
        if (request.getNewPassword() == null
                || !request.getNewPassword().equals(request.getConfirmPassword())) {
            throw BusinessException.badRequest("password_mismatch", "两次输入的密码不一致");
        }
        if (request.getNewPassword().length() < 6 || request.getNewPassword().length() > 64) {
            throw BusinessException.badRequest("invalid_password", "密码长度需为 6-64 位");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        // 重置成功后：通过 RabbitMQ 删除 Redis 中的令牌与权限信息并注销全部旧令牌；
        // Broker 不可用时降级为同步删除。
        if (!publisher.publishTokenInvalidate(user.getId(), null)) {
            sessionService.deleteByUser(user.getId(), user.getTenantId());
        }
    }

    private SysUser findUserForForgotPassword(String rawUsername) {
        String username = normalizeUsername(rawUsername);
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw BusinessException.badRequest("user_not_found", "用户名不存在");
        }
        if (user.getStatus() != null && "CANCELLED".equalsIgnoreCase(user.getStatus())) {
            throw BusinessException.badRequest("account_cancelled", "该账号已注销");
        }
        return user;
    }

    private void verifyForgotContact(SysUser user, String contactType, String contact) {
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(user.getId());
        String storedPhone = profile == null ? "" : clean(profile.getPhone());
        String storedEmail = profile == null ? "" : clean(profile.getEmail());
        if (storedPhone.isEmpty() && storedEmail.isEmpty()) {
            throw BusinessException.badRequest("contact_not_set",
                    "该账号未设置手机号或邮箱，无法自助找回密码");
        }
        String type = clean(contactType).toLowerCase(Locale.ROOT);
        String value = clean(contact);
        if (value.isEmpty()) {
            throw BusinessException.badRequest("contact_required", "请输入手机号或邮箱");
        }
        if ("phone".equals(type)) {
            String contactPhone = phoneDigits(value);
            if (!PHONE_PATTERN.matcher(contactPhone).matches()) {
                throw BusinessException.badRequest("invalid_phone", "请输入正确的手机号");
            }
            if (!samePhone(value, storedPhone)) {
                throw BusinessException.badRequest("contact_mismatch", "手机号与账号不符");
            }
        } else if ("email".equals(type)) {
            if (!EMAIL_PATTERN.matcher(value).matches()) {
                throw BusinessException.badRequest("invalid_email", "请输入正确的邮箱地址");
            }
            if (storedEmail.isEmpty() || !value.equalsIgnoreCase(storedEmail)) {
                throw BusinessException.badRequest("contact_mismatch", "邮箱与账号不符");
            }
        } else {
            // 未传类型时按手机号或邮箱自动识别。
            boolean emailMatched = !storedEmail.isEmpty() && value.equalsIgnoreCase(storedEmail);
            boolean phoneMatched = samePhone(value, storedPhone);
            if (!emailMatched && !phoneMatched) {
                throw BusinessException.badRequest("contact_mismatch",
                        "手机号或邮箱与账号不符");
            }
        }
    }

    public void logout(AuthPrincipal principal) {        // 退出登录：通过 RabbitMQ 删除 Redis 中的令牌与权限信息；
        // Broker 不可用时降级为同步删除，保证令牌一定被注销。
        if (!publisher.publishTokenInvalidate(null, principal.sessionId())) {
            sessionService.delete(principal.sessionId());
        }
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
        // 登录通过后，从数据库查询权限，与令牌一起写入 Redis（过期时间与令牌一致）。
        List<String> permissions = permissionService.permissionsOf(user.getId());
        if (permissions.isEmpty()) {
            // 兼容旧数据：权限对应表缺失时按 role 兜底授予普通用户权限。
            permissionService.grant(user.getId(), "USER".equalsIgnoreCase(user.getRole())
                    ? "USER" : user.getRole());
            permissions = permissionService.permissionsOf(user.getId());
        }
        String role = PermissionService.roleOf(permissions);
        SessionInfo info = new SessionInfo(user.getId(), user.getTenantId(),
                user.getUsername(), user.getDisplayName(), role, permissions);
        String sessionId = sessionService.create(info, ttl);
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
                user.getDisplayName(), user.getRole(), statusOf(user),
                tenant == null ? "" : tenant.getName(),
                avatarUrl);
    }

    /** 空状态按正常处理，兼容历史数据。 */
    private String statusOf(SysUser user) {
        return user.getStatus() == null || user.getStatus().isBlank()
                ? "NORMAL" : user.getStatus();
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
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
        return code + phone.replaceAll("[\\s-]", "");
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("[^0-9+]", "");
    }

    private String phoneDigits(String value) {
        return normalizePhone(value).replace("+", "");
    }

    /** 兼容账号中只保存本地号码、没有国家区号的历史数据。 */
    private boolean samePhone(String left, String right) {
        String a = phoneDigits(left);
        String b = phoneDigits(right);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b)
                || (a.length() >= 7 && b.length() >= 7
                && (a.endsWith(b) || b.endsWith(a)));
    }
}
