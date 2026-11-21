package com.jobtrack.system.service;

import com.jobtrack.system.pojo.PreferencesRequest;
import com.jobtrack.system.pojo.PreferencesResponse;

/**
 * 租户级配置读写。
 */
public interface SettingService {

    String get(String key, String fallback);

    boolean getBoolean(String key, boolean fallback);

    void put(String key, String value);

    PreferencesResponse preferences();

    PreferencesResponse update(PreferencesRequest request);
}
