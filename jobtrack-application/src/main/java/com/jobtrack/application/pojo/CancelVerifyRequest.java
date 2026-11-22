package com.jobtrack.application.pojo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户注销 · 身份验证请求：验证保留的手机号或邮箱与密码。
 */
@Data
public class CancelVerifyRequest {

    /** phone / email，留空时按手机号或邮箱自动识别。 */
    private String contactType;

    @NotBlank(message = "请输入手机号或邮箱")
    private String contact;

    @NotBlank(message = "请输入密码")
    private String password;
}
