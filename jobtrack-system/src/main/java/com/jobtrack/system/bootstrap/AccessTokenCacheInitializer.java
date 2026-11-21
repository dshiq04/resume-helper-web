package com.jobtrack.system.bootstrap;

import com.jobtrack.system.service.AccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把数据库中的授权码同步到 Redis Hash，保证升级后旧授权码可继续使用。
 */
@Component
@RequiredArgsConstructor
public class AccessTokenCacheInitializer implements ApplicationRunner {

    private final AccessTokenService accessTokenService;

    @Override
    public void run(ApplicationArguments args) {
        accessTokenService.warmCache();
    }
}
