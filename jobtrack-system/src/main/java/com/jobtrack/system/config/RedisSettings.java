package com.jobtrack.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 在线会话存储配置。默认关闭 Redis，使用进程内存储；
 * 打开后登录会话、在线人数与验证码通过 Redisson 写入 Redis，多实例部署时也可以共享。
 */
@Data
@Component
@ConfigurationProperties(prefix = "redis")
public class RedisSettings {

    private boolean enabled = false;
    private String host = "127.0.0.1";
    private int port = 6379;
    private String password = "";
    private int database = 0;
    private String keyPrefix = "jobtrack:";
    private String timeout = "2s";
}
