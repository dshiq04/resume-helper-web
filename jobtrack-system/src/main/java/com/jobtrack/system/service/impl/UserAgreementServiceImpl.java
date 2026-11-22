package com.jobtrack.system.service.impl;

import com.jobtrack.system.mapper.SysTenantMapper;
import com.jobtrack.system.mapper.SysUserMapper;
import com.jobtrack.system.mapper.UserAgreementMapper;
import com.jobtrack.system.mapper.UserProfileMapper;
import com.jobtrack.system.pojo.dto.AgreementAcceptDTO;
import com.jobtrack.system.pojo.po.SysTenant;
import com.jobtrack.system.pojo.po.SysUser;
import com.jobtrack.system.pojo.po.UserAgreement;
import com.jobtrack.system.pojo.po.UserProfile;
import com.jobtrack.system.pojo.vo.UserAgreementVO;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.service.UserAgreementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAgreementServiceImpl implements UserAgreementService {

    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final Set<String> LANGUAGES =
            Set.of("zh-CN", "zh-TW", "en", "ja", "ko", "ru");

    private final UserAgreementMapper agreementMapper;
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final UserProfileMapper profileMapper;

    @Override
    public void recordRegistration(Long tenantId, Long userId, String signerName,
                                   String language, String timezone) {
        agreementMapper.insertIgnoreTenant(tenantId, userId, LocalDateTime.now(),
                normalizeLanguage(language, null), trimToNull(signerName),
                null, trimToNull(timezone), null);
    }

    @Override
    @Transactional
    public UserAgreementVO accept(AuthPrincipal principal, AgreementAcceptDTO request,
                                  String remoteIp) {
        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            return find(principal.id());
        }
        UserProfile profile = profileMapper.selectByUserIdIgnoreTenant(principal.id());
        String language = normalizeLanguage(request.getLanguage(), request.getLocale());
        String timezone = trimToNull(request.getTimezone());
        String location = firstNonBlank(trimToNull(request.getLocation()),
                profile == null ? null : trimToNull(profile.getLocation()), timezone);
        String signerName = firstNonBlank(profile == null ? null : trimToNull(profile.getRealName()),
                trimToNull(user.getDisplayName()), user.getUsername());
        String signedIp = limit(trimToNull(remoteIp), 64);

        UserAgreement existing = agreementMapper.selectByUserIdIgnoreTenant(principal.id());
        if (existing == null) {
            agreementMapper.insertIgnoreTenant(user.getTenantId(), user.getId(), LocalDateTime.now(),
                    language, signerName, location, timezone, signedIp);
        } else {
            // 只补全历史缺失的信息，已签署时间与语言保持不变。
            LocalDateTime acceptedAt = existing.getAcceptedAt() == null
                    ? LocalDateTime.now() : existing.getAcceptedAt();
            String mergedLanguage = blank(existing.getLanguage())
                    ? language : existing.getLanguage();
            String mergedSigner = blank(existing.getSignerName())
                    ? signerName : existing.getSignerName();
            String mergedLocation = blank(existing.getLocation())
                    ? location : existing.getLocation();
            String mergedTimezone = blank(existing.getTimezone())
                    ? timezone : existing.getTimezone();
            String mergedIp = blank(existing.getSignedIp())
                    ? signedIp : existing.getSignedIp();
            agreementMapper.updateByUserIdIgnoreTenant(user.getId(), acceptedAt,
                    mergedLanguage, mergedSigner, mergedLocation, mergedTimezone, mergedIp);
        }
        return find(user.getId());
    }

    @Override
    public UserAgreementVO find(Long userId) {
        SysUser user = userMapper.selectById(userId);
        UserAgreement agreement = agreementMapper.selectByUserIdIgnoreTenant(userId);
        UserAgreementVO view = new UserAgreementVO();
        view.setSigned(agreement != null && agreement.getAcceptedAt() != null);
        view.setUserId(userId);
        if (user != null) {
            view.setUsername(user.getUsername());
            view.setDisplayName(user.getDisplayName());
            SysTenant tenant = tenantMapper.selectById(user.getTenantId());
            view.setTenantName(tenant == null ? "" : tenant.getName());
        }
        if (agreement == null) {
            return view;
        }
        view.setAcceptedAt(agreement.getAcceptedAt());
        view.setLanguage(blank(agreement.getLanguage())
                ? DEFAULT_LANGUAGE : agreement.getLanguage());
        view.setSignerName(firstNonBlank(agreement.getSignerName(),
                user == null ? null : trimToNull(user.getDisplayName()),
                user == null ? null : user.getUsername()));
        view.setLocation(firstNonBlank(agreement.getLocation(), agreement.getTimezone()));
        view.setTimezone(agreement.getTimezone());
        view.setSignedIp(agreement.getSignedIp());
        return view;
    }

    /** 语言是固定枚举，非法值回落到简体中文；老客户端可从 locale 推断。 */
    private String normalizeLanguage(String language, String locale) {
        String value = trimToNull(language);
        if (value != null && LANGUAGES.contains(value)) {
            return value;
        }
        String localeValue = trimToNull(locale);
        if (localeValue != null) {
            String normalized = localeValue.replace('_', '-');
            if (normalized.toLowerCase().startsWith("zh-tw")
                    || normalized.toLowerCase().startsWith("zh-hk")
                    || normalized.toLowerCase().startsWith("zh-hant")) {
                return "zh-TW";
            }
            if (normalized.toLowerCase().startsWith("zh")) {
                return "zh-CN";
            }
            if (normalized.toLowerCase().startsWith("ja")) {
                return "ja";
            }
            if (normalized.toLowerCase().startsWith("ko")) {
                return "ko";
            }
            if (normalized.toLowerCase().startsWith("ru")) {
                return "ru";
            }
            if (normalized.toLowerCase().startsWith("en")) {
                return "en";
            }
        }
        return DEFAULT_LANGUAGE;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
