package com.jobtrack.system.service;

import com.jobtrack.system.pojo.vo.CaptchaVO;

/**
 * 图形验证码。
 */
public interface CaptchaService {

    boolean enabled();

    CaptchaVO generate();

    void verify(String captchaId, String captchaCode);
}
