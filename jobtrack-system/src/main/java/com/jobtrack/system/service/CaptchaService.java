package com.jobtrack.system.service;

import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.config.CaptchaSettings;
import com.jobtrack.system.config.RedisSettings;
import com.jobtrack.system.dto.CaptchaResponse;
import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 图形验证码。验证码存储在 Redis 中并在校验后立即删除，保证一次性使用。
 *
 * <p>该功能依赖 Redis：如果 config.json 中 captcha.enabled=true 而 redis.enabled=false，
 * 应用会拒绝启动，避免出现“看似有验证码、实际无法校验”的假安全状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";

    private final CaptchaSettings captchaSettings;
    private final RedisSettings redisSettings;
    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    void validate() {
        if (captchaSettings.isEnabled() && !redisSettings.isEnabled()) {
            throw new IllegalStateException(
                    "captcha.enabled=true 时必须先开启 redis.enabled=true，验证码需要 Redis 存储");
        }
        if (captchaSettings.isEnabled()) {
            log.info("图形验证码已启用（Redis 存储，{} 秒有效）", captchaSettings.getTtlSeconds());
        }
    }

    public boolean enabled() {
        return captchaSettings.isEnabled();
    }

    public CaptchaResponse generate() {
        requireEnabled();
        Captcha captcha;
        if ("math".equalsIgnoreCase(captchaSettings.getType())) {
            captcha = new ArithmeticCaptcha(captchaSettings.getWidth(), captchaSettings.getHeight());
        } else {
            SpecCaptcha spec = new SpecCaptcha(captchaSettings.getWidth(),
                    captchaSettings.getHeight(), Math.max(3, captchaSettings.getLength()));
            spec.setCharType(Captcha.TYPE_DEFAULT);
            captcha = spec;
        }

        String code = captcha.text();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        int ttl = Math.max(60, captchaSettings.getTtlSeconds());
        try {
            redisTemplate.opsForValue().set(key(captchaId), code.toLowerCase(),
                    Duration.ofSeconds(ttl));
        } catch (Exception exception) {
            log.warn("验证码写入 Redis 失败: {}", exception.getMessage());
            throw new BusinessException(503, "captcha_unavailable", "验证码服务暂不可用，请稍后再试");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            captcha.out(output);
            String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
            return new CaptchaResponse(captchaId, image, ttl);
        } catch (Exception exception) {
            throw new BusinessException(500, "captcha_failed", "生成验证码失败");
        }
    }

    public void verify(String captchaId, String captchaCode) {
        if (!captchaSettings.isEnabled()) {
            return;
        }
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw BusinessException.badRequest("captcha_required", "请输入验证码");
        }
        String key = key(captchaId.trim());
        String expected;
        try {
            expected = redisTemplate.opsForValue().get(key);
            // 一次性使用：无论对错都删除，防止暴力尝试。
            redisTemplate.delete(key);
        } catch (Exception exception) {
            throw new BusinessException(503, "captcha_unavailable", "验证码服务暂不可用，请稍后再试");
        }
        if (expected == null) {
            throw BusinessException.badRequest("captcha_expired", "验证码已过期，请刷新后重试");
        }
        if (!expected.equalsIgnoreCase(captchaCode.trim())) {
            throw BusinessException.badRequest("invalid_captcha", "验证码错误");
        }
    }

    private void requireEnabled() {
        if (!captchaSettings.isEnabled()) {
            throw BusinessException.badRequest("captcha_disabled", "验证码功能未启用");
        }
    }

    private String key(String captchaId) {
        return redisSettings.getKeyPrefix() + KEY_PREFIX + captchaId;
    }
}
