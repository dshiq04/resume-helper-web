package com.jobtrack.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.dto.PreferencesRequest;
import com.jobtrack.system.dto.PreferencesResponse;
import com.jobtrack.system.entity.TenantSetting;
import com.jobtrack.system.mapper.TenantSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String IDLE_LOCK_ENABLED = "preferences.idleLockEnabled";
    public static final String IDLE_LOCK_MINUTES = "preferences.idleLockMinutes";

    private final TenantSettingMapper settingMapper;

    public String get(String key, String fallback) {
        TenantSetting setting = settingMapper.selectOne(Wrappers.<TenantSetting>lambdaQuery()
                .eq(TenantSetting::getSettingKey, key));
        return setting == null ? fallback : setting.getSettingValue();
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = get(key, null);
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
    }

    @Transactional
    public void put(String key, String value) {
        TenantSetting setting = settingMapper.selectOne(Wrappers.<TenantSetting>lambdaQuery()
                .eq(TenantSetting::getSettingKey, key));
        if (setting == null) {
            setting = new TenantSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            settingMapper.insert(setting);
        } else {
            setting.setSettingValue(value);
            settingMapper.updateById(setting);
        }
    }

    public PreferencesResponse preferences() {
        boolean enabled = getBoolean(IDLE_LOCK_ENABLED, false);
        int minutes = parseInt(get(IDLE_LOCK_MINUTES, "15"), 15);
        if (minutes < 1 || minutes > 240) {
            minutes = 15;
        }
        return new PreferencesResponse(enabled, minutes);
    }

    @Transactional
    public PreferencesResponse update(PreferencesRequest request) {
        if (request.getIdleLockMinutes() != null) {
            int minutes = request.getIdleLockMinutes();
            if (minutes < 1 || minutes > 240) {
                throw BusinessException.badRequest("invalid_idle_minutes", "闲置时长需在 1-240 分钟之间");
            }
            put(IDLE_LOCK_MINUTES, String.valueOf(minutes));
        }
        if (request.getIdleLockEnabled() != null) {
            put(IDLE_LOCK_ENABLED, request.getIdleLockEnabled() ? "true" : "false");
        }
        return preferences();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
