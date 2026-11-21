package com.jobtrack.system.service;

import com.jobtrack.system.pojo.CaptchaResponse;

/**
 * 图形验证码。
 */
public interface CaptchaService {

    boolean enabled();

    CaptchaResponse generate();

    void verify(String captchaId, String captchaCode);
}
