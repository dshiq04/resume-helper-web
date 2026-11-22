package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘记密码 · 第一步：验证用户名和手机号/邮箱。
 */
@Data
public class ForgotPasswordVerifyDTO {

    @NotBlank(message = "请输入用户名")
    private String username;

    /** phone / email，留空时按手机号或邮箱自动识别。 */
    private String contactType;

    @NotBlank(message = "请输入手机号或邮箱")
    private String contact;
}
