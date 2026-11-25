package com.jobtrack.system.controller;

import com.jobtrack.common.api.Result;

import com.jobtrack.system.service.PublicConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class PublicConfigController {

    private final PublicConfigService publicConfigService;

    /**
     * 登录页即可读取的公共配置，启用 Redis 时由缓存提供。
     */
    @GetMapping
    public Result<Map<String, Object>> config() {
        return Result.success(publicConfigService.config());
    }
}
