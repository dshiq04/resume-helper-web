package com.jobtrack.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图形验证码配置。验证码一次性保存在 Redis 中，
 * 因此 captcha.enabled=true 时必须同时开启 redis.enabled=true。
 */
@Data
@Component
@ConfigurationProperties(prefix = "captcha")
public class CaptchaSettings {

    private boolean enabled = false;
    /** char 字母数字，math 算术运算。 */
    private String type = "char";
    private int length = 4;
    private int width = 130;
    private int height = 44;
    private int ttlSeconds = 180;
}
