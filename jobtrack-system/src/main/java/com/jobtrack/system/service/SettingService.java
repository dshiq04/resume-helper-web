package com.jobtrack.system.service;

import com.jobtrack.system.pojo.dto.PreferencesDTO;
import com.jobtrack.system.pojo.vo.PreferencesVO;

/**
 * 租户级配置读写。
 */
public interface SettingService {

    String get(String key, String fallback);

    boolean getBoolean(String key, boolean fallback);

    void put(String key, String value);

    PreferencesVO preferences();

    PreferencesVO update(PreferencesDTO request);
}
