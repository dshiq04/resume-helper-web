package com.jobtrack.system.bootstrap;

import com.jobtrack.system.service.BanService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把数据库中的封禁用户同步到 Redis 封禁集合。
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class BanCacheInitializer implements ApplicationRunner {

    private final BanService banService;

    @Override
    public void run(ApplicationArguments args) {
        banService.warmCache();
    }
}
