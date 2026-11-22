package com.jobtrack.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.application.mapper.ApplicationRecordMapper;
import com.jobtrack.application.mapper.DeletedApplicationMapper;
import com.jobtrack.application.mapper.ResumeMapper;
import com.jobtrack.application.pojo.dto.CancelConfirmDTO;
import com.jobtrack.application.pojo.vo.CancelStatusVO;
import com.jobtrack.application.pojo.dto.CancelVerifyDTO;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.service.AccountCancelService;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.mapper.AccessTokenMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.mapper.UserProfileMapper;
import com.jobtrack.system.messaging.AsyncTaskPublisher;
import com.jobtrack.system.pojo.po.AccessToken;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.pojo.po.UserProfile;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.security.SessionService;
import com.jobtrack.system.service.CaptchaService;
import com.jobtrack.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 用户注销流程。
 *
 * <p>注销只把账号状态置为 CANCELLED，数据保留以便恢复；
 * 已注销账号的手机号/邮箱允许被重新注册使用。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountCancelServiceImpl implements AccountCancelService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{6,15}$");

    private final ApplicationRecordMapper applicationMapper;
    private final DeletedApplicationMapper trashMapper;
    private final ResumeMapper resumeMapper;
    private final AccessTokenMapper tokenMapper;
    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final SessionService sessionService;
    private final AsyncTaskPublisher publisher;

    public CancelStatusVO status(AuthPrincipal principal) {
        long applications = count(applicationMapper.selectCount(null));
        long trash = count(trashMapper.selectCount(null));
        long resumes = count(resumeMapper.selectCount(null));
        // @TableLogic 逻辑删除：selectCount 自动排除已删除的授权码。
        long tokens = count(tokenMapper.selectCount(Wrappers.<AccessToken>lambdaQuery()
                .eq(AccessToken::getUserId, principal.id())));
        return new CancelStatusVO(applications, trash, resumes, tokens,
                permissionService.hasElevated(principal.id()));
    }

    public void verify(AuthPrincipal principal, CancelVerifyDTO request) {
        SysUser user = requireUser(principal);
        verifyContactAndPassword(user, request.getContactType(),
                request.getContact(), request.getPassword());
    }

    @Transactional
    public void confirm(AuthPrincipal principal, CancelConfirmDTO request) {
        SysUser user = requireUser(principal);
        // 后台验证验证码，通过后执行注销。
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());
        verifyContactAndPassword(user, request.getContactType(),
                request.getContact(), request.getPassword());

        // 再次检查注销条件，防止验证期间用户又录入了数据。
        CancelStatusVO status = status(principal);
        if (status.getApplications() > 0 || status.getTrash() > 0) {
            throw BusinessException.badRequest("cancel_applications_not_cleared",
                    "请先清除投递记录后再注销");
        }
        if (status.getResumes() > 0) {
            throw BusinessException.badRequest("cancel_resumes_not_deleted",
                    "请先删除简历库中的简历后再注销");
        }
        if (status.getTokens() > 0) {
            throw BusinessException.badRequest("cancel_tokens_not_deleted",
                    "请先删除授权码后再注销");
        }
        if (status.isElevatedPermission()) {
            throw BusinessException.badRequest("cancel_permission_not_dropped",
                    "请先放弃普通用户以外的权限后再注销");
        }

        // 用户状态改成「注销」。
        user.setStatus("CANCELLED");
        userMapper.updateById(user);

        // Redis 中删除令牌、权限信息并从程序中注销令牌（通过 RabbitMQ）。
        if (!publisher.publishTokenInvalidate(principal.id(), null)) {
            sessionService.deleteByUser(principal.id(), principal.tenantId());
        }
    }

    private SysUser requireUser(AuthPrincipal principal) {
        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            throw BusinessException.unauthorized("user_not_found", "账号不存在");
        }
        return user;
    }

    /** 验证保留的手机号或邮箱与密码，全部正确才允许进入下一步。 */
    private void verifyContactAndPassword(SysUser user, String contactType,
                                          String contact, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BusinessException.badRequest("invalid_password", "密码不正确");
        }
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(user.getId());
        String storedPhone = profile == null || profile.getPhone() == null
                ? "" : profile.getPhone().trim();
        String storedEmail = profile == null || profile.getEmail() == null
                ? "" : profile.getEmail().trim();
        if (storedPhone.isEmpty() && storedEmail.isEmpty()) {
            throw BusinessException.badRequest("contact_not_set",
                    "请先在账号与安全中设置手机号或邮箱");
        }
        String type = contactType == null ? "" : contactType.trim().toLowerCase(Locale.ROOT);
        String value = contact == null ? "" : contact.trim();
        if (value.isEmpty()) {
            throw BusinessException.badRequest("contact_required", "请输入手机号或邮箱");
        }
        if ("phone".equals(type)) {
            if (!PHONE_PATTERN.matcher(phoneDigits(value)).matches()) {
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
            boolean emailMatched = !storedEmail.isEmpty() && value.equalsIgnoreCase(storedEmail);
            boolean phoneMatched = samePhone(value, storedPhone);
            if (!emailMatched && !phoneMatched) {
                throw BusinessException.badRequest("contact_mismatch", "手机号或邮箱与账号不符");
            }
        }
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

    private String phoneDigits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }
}
