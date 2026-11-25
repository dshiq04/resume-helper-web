package com.jobtrack.system.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.core.math.Calculator;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.system.config.CaptchaSettings;
import com.jobtrack.system.config.RedisSettings;
import com.jobtrack.system.pojo.vo.CaptchaVO;
import com.jobtrack.system.service.CaptchaService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * 图形验证码（Hutool Captcha + Redisson）。
 *
 * <p>验证码一次性保存在 Redisson 中，校验时直接 getAndDelete，天然防重放。
 * 该功能依赖 Redis：如果配置项 captcha.enabled=true 而 Redis 不可用，
 * 应用会拒绝启动，避免出现“看似有验证码、实际无法校验”的假安全状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final int LINE_COUNT = 30;

    private final CaptchaSettings captchaSettings;
    private final RedisSettings redisSettings;
    private final ObjectProvider<RedissonClient> redissonProvider;

    private RedissonClient redisson;

    @PostConstruct
    void validate() {
        if (captchaSettings.isEnabled()) {
            if (!redisSettings.isEnabled()) {
                throw new IllegalStateException(
                        "captcha.enabled=true 时必须先开启 redis.enabled=true，验证码需要 Redisson 存储");
            }
            try {
                redisson = redissonProvider.getIfAvailable();
            } catch (Exception exception) {
                throw new IllegalStateException("图形验证码需要 Redisson，但客户端初始化失败", exception);
            }
            if (redisson == null) {
                throw new IllegalStateException("图形验证码需要 Redisson，但 Redisson 客户端未创建");
            }
            log.info("图形验证码已启用（Hutool Captcha + Redisson，{} 秒有效）",
                    captchaSettings.getTtlSeconds());
        }
    }

    public boolean enabled() {
        return captchaSettings.isEnabled();
    }

    public CaptchaVO generate() {
        requireEnabled();
        // Hutool 的线干扰验证码：兼容字符与算术两种模式。
        LineCaptcha captcha = createCaptcha();
        captcha.createCode();
        String code = captcha.getCode();
        String stored = "math".equalsIgnoreCase(captchaSettings.getType())
                ? String.valueOf((int) Calculator.conversion(code))
                : code.toLowerCase(Locale.ROOT);

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        int ttl = Math.max(60, captchaSettings.getTtlSeconds());
        try {
            bucket(captchaId).set(stored, Duration.ofSeconds(ttl));
        } catch (Exception exception) {
            log.warn("验证码写入 Redisson 失败: {}", exception.getMessage());
            throw new BusinessException(503, "captcha_unavailable", "验证码服务暂不可用，请稍后再试");
        }

        return new CaptchaVO(captchaId, captcha.getImageBase64Data(), ttl);
    }

    public void verify(String captchaId, String captchaCode) {
        if (!captchaSettings.isEnabled()) {
            return;
        }
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw BusinessException.badRequest("captcha_required", "请输入验证码");
        }
        String expected;
        try {
            // 一次性使用：无论对错都删除，防止暴力尝试。
            expected = bucket(captchaId.trim()).getAndDelete();
        } catch (Exception exception) {
            throw new BusinessException(503, "captcha_unavailable", "验证码服务暂不可用，请稍后再试");
        }
        if (expected == null) {
            throw BusinessException.badRequest("captcha_expired", "验证码已过期，请刷新后重试");
        }
        if ("math".equalsIgnoreCase(captchaSettings.getType())) {
            try {
                if (Integer.parseInt(expected) != Integer.parseInt(captchaCode.trim())) {
                    throw BusinessException.badRequest("invalid_captcha", "验证码错误");
                }
            } catch (NumberFormatException exception) {
                throw BusinessException.badRequest("invalid_captcha", "验证码错误");
            }
            return;
        }
        if (!expected.equalsIgnoreCase(captchaCode.trim())) {
            throw BusinessException.badRequest("invalid_captcha", "验证码错误");
        }
    }

    private LineCaptcha createCaptcha() {
        int width = Math.max(80, captchaSettings.getWidth());
        int height = Math.max(30, captchaSettings.getHeight());
        int length = Math.max(3, captchaSettings.getLength());
        if ("math".equalsIgnoreCase(captchaSettings.getType())) {
            return CaptchaUtil.createLineCaptcha(width, height,
                    new MathGenerator(1, false), LINE_COUNT);
        }
        return CaptchaUtil.createLineCaptcha(width, height, length, LINE_COUNT);
    }

    private RBucket<String> bucket(String captchaId) {
        if (redisson == null) {
            throw new IllegalStateException("图形验证码需要 Redisson");
        }
        String prefix = StringUtils.hasText(redisSettings.getKeyPrefix())
                ? redisSettings.getKeyPrefix() : "";
        return redisson.getBucket(prefix + KEY_PREFIX + captchaId, StringCodec.INSTANCE);
    }

    private void requireEnabled() {
        if (!captchaSettings.isEnabled()) {
            throw BusinessException.badRequest("captcha_disabled", "验证码功能未启用");
        }
    }
}
