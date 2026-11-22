package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘记密码 · 第二步：重置密码。
 */
@Data
public class ForgotPasswordResetDTO {

    @NotBlank(message = "请输入用户名")
    private String username;

    /** phone / email，留空时按手机号或邮箱自动识别。 */
    private String contactType;

    @NotBlank(message = "请输入手机号或邮箱")
    private String contact;

    @NotBlank(message = "请输入新密码")
    private String newPassword;

    @NotBlank(message = "请再次输入新密码")
    private String confirmPassword;
}
