package com.jobtrack.application.pojo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户注销 · 最终确认请求：需要验证码。
 */
@Data
public class CancelConfirmRequest {

    private String captchaId;

    @NotBlank(message = "请输入验证码")
    private String captchaCode;

    /** phone / email，留空时按手机号或邮箱自动识别。 */
    private String contactType;

    @NotBlank(message = "请输入手机号或邮箱")
    private String contact;

    @NotBlank(message = "请输入密码")
    private String password;
}
