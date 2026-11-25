package com.jobtrack.system.service.impl;

import com.jobtrack.system.service.PublicConfigService;
import com.jobtrack.system.service.CacheService;

import com.jobtrack.common.constant.DomainConstants;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.config.CaptchaSettings;
import com.jobtrack.system.security.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前后端共享的公共配置（选项列表、上传上限等）。
 * 结果写入 Redis 或进程内缓存，避免每次页面启动都重复组装。
 */
@Service
@RequiredArgsConstructor
public class PublicConfigServiceImpl implements PublicConfigService {

    private static final String CACHE_KEY = "public:config:v2";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final AppProperties appProperties;
    private final CacheService cacheService;
    private final SessionService sessionService;
    private final CaptchaSettings captchaSettings;

    @SuppressWarnings("unchecked")
    public Map<String, Object> config() {
        return cacheService.get(CACHE_KEY, Map.class, TTL, this::build);
    }

    private Map<String, Object> build() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("categories", DomainConstants.CATEGORIES);
        config.put("statuses", DomainConstants.STATUSES);
        config.put("timelineStatuses", DomainConstants.TIMELINE_ORDER);
        config.put("channels", List.of("官网", "内推", "Boss直聘", "牛客", "猎聘", "实习僧", "拉勾", "其他"));
        config.put("priorities", List.of("high", "medium", "low"));
        config.put("workModes", List.of("onsite", "hybrid", "remote"));
        config.put("maxResumeSizeMb", appProperties.getStorage().getMaxFileSizeMb());
        config.put("registrationEnabled", true);
        config.put("captchaEnabled", captchaSettings.isEnabled());
        config.put("onlineMode", sessionService.mode());
        return config;
    }
}
