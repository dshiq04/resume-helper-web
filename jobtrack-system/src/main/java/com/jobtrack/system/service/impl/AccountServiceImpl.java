package com.jobtrack.system.service.impl;

import com.jobtrack.system.service.AccountService;
import com.jobtrack.system.service.AvatarService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.pojo.dto.ContactUpdateDTO;
import com.jobtrack.system.pojo.dto.UserProfileDTO;
import com.jobtrack.system.pojo.vo.UserProfileVO;
import com.jobtrack.system.pojo.vo.UserVO;
import com.jobtrack.system.pojo.po.SysTenant;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.pojo.po.UserProfile;
import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.mapper.UserProfileMapper;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.service.ContactService;
import com.jobtrack.system.storage.FileCleanupScheduler;
import com.jobtrack.system.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 账户设置：扩展个人资料与联系方式。用户名注册后禁止修改。
 */
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{6,15}$");

    private final UserProfileMapper profileMapper;
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final ObjectStorageService objectStorage;
    private final AvatarService avatarService;
    private final ContactService contactService;
    private final FileCleanupScheduler fileCleanupScheduler;

    private static final Set<String> AVATAR_TYPES = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;

    public UserProfileVO profile(AuthPrincipal principal) {
        UserProfile profile = profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, principal.id()));
        if (profile == null) {
            UserProfileVO empty = new UserProfileVO();
            empty.setUserId(principal.id());
            return empty;
        }
        return view(profile);
    }

    @Transactional
    public UserProfileVO saveProfile(AuthPrincipal principal, UserProfileDTO request) {
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
            profileMapper.updateById(profile);
        }
        return view(profile);
    }

    /**
     * 保存联系方式：先检查本用户是否存在，再检查手机号/邮箱（OR 条件）
     * 是否已被其他未注销账号占用；被占用时禁止修改并提示用户。
     */
    @Transactional
    public UserProfileVO saveContact(AuthPrincipal principal, ContactUpdateDTO request) {
        // 1. 检查本用户是否存在。
        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            throw BusinessException.unauthorized("user_not_found", "账号不存在");
        }
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
        // 2. 手机号或邮箱（OR）已被其他账号占用 → 禁止修改。已注销账号会被过滤。
        contactService.ensureAvailable(principal.id(), phone, email);

        UserProfile profile = profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, principal.id()));
        if (profile == null) {
            profile = new UserProfile();
            profile.setTenantId(principal.tenantId());
            profile.setUserId(principal.id());
            profile.setPhone(phone.isEmpty() ? null : phone);
            profile.setEmail(email.isEmpty() ? null : email);
            profileMapper.insert(profile);
        } else {
            profile.setPhone(phone.isEmpty() ? null : phone);
            profile.setEmail(email.isEmpty() ? null : email);
            profileMapper.updateById(profile);
        }
        return view(profile);
    }

    @Transactional
    public UserProfileVO uploadAvatar(AuthPrincipal principal, MultipartFile file) {
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
        // 资料写入或事务提交失败时，删除刚上传的头像文件。
        fileCleanupScheduler.deleteOnRollback(key);

        UserProfile profile = profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, principal.id()));
        String previous = profile == null ? null : profile.getAvatarUrl();
        if (profile == null) {
            profile = new UserProfile();
            profile.setTenantId(principal.tenantId());
            profile.setUserId(principal.id());
            profile.setAvatarUrl(key);
            profileMapper.insert(profile);
        } else {
            profile.setAvatarUrl(key);
            profileMapper.updateById(profile);
        }
        if (previous != null && !previous.isBlank() && !previous.equals(key)) {
            // 提交后才删除旧头像，避免事务回滚后数据库仍引用已被删除的文件。
            fileCleanupScheduler.deleteAfterCommit(previous);
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

    private UserProfileVO view(UserProfile profile) {
        UserProfileVO view = new UserProfileVO();
        BeanUtils.copyProperties(profile, view);
        view.setAvatarUrl(avatarService.url(profile.getUserId(), profile.getAvatarUrl()));
        return view;
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
        // 用户把带区号的完整号码填进了手机号输入框：去掉与所选区号重复的部分，
        // 保证入库/查询的都是「区号 + 本地号码」各一份的完整号码。
        if (code.length() > 1 && value.startsWith(code)) {
            value = value.substring(code.length());
        }
        return code + value;
    }

    private String phoneDigits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private UserVO view(SysUser user) {
        SysTenant tenant = tenantMapper.selectById(user.getTenantId());
        String status = user.getStatus() == null || user.getStatus().isBlank()
                ? "NORMAL" : user.getStatus();
        return new UserVO(user.getId(), user.getTenantId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), status,
                tenant == null ? "" : tenant.getName(), null);
    }
}
