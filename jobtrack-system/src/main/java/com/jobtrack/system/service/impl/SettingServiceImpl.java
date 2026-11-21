package com.jobtrack.system.service.impl;

import com.jobtrack.system.service.SettingService;
import com.jobtrack.system.service.CacheService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobtrack.common.context.TenantContext;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.pojo.PreferencesRequest;
import com.jobtrack.system.pojo.PreferencesResponse;
import com.jobtrack.system.pojo.TenantSetting;
import com.jobtrack.system.mapper.TenantSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    public static final String IDLE_LOCK_ENABLED = "preferences.idleLockEnabled";
    public static final String IDLE_LOCK_MINUTES = "preferences.idleLockMinutes";

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final TenantSettingMapper settingMapper;
    private final CacheService cacheService;

    public String get(String key, String fallback) {
        String cacheKey = cacheKey(key);
        String cached = cacheService.getString(cacheKey);
        if (cached != null) {
            return cached;
        }
        TenantSetting setting = settingMapper.selectOne(Wrappers.<TenantSetting>lambdaQuery()
                .eq(TenantSetting::getSettingKey, key));
        if (setting == null || setting.getSettingValue() == null) {
            return fallback;
        }
        cacheService.putString(cacheKey, setting.getSettingValue(), CACHE_TTL);
        return setting.getSettingValue();
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
        // 写穿缓存，其他实例下次读取即可拿到最新值。
        cacheService.putString(cacheKey(key), value, CACHE_TTL);
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

    private String cacheKey(String key) {
        return "tenant:" + TenantContext.require() + ":e" + cacheService.epoch() + ":setting:" + key;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
