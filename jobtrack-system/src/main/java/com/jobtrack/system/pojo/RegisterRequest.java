package com.jobtrack.system.pojo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "请输入用户名")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$",
            message = "用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 个字符")
    private String password;

    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    @NotBlank(message = "请输入昵称")
    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String displayName;

    @NotBlank(message = "请输入工作区名称")
    @Size(max = 64, message = "工作区名称不能超过 64 个字符")
    private String tenantName;

    @Size(max = 64)
    private String captchaId;

    @Size(max = 16)
    private String captchaCode;

    @Size(max = 8, message = "国家/地区号过长")
    private String countryCode;

    /**
     * 联系方式类型：phone 或 email。注册时手机号与邮箱二选一。
     */
    @Pattern(regexp = "^$|phone|email", message = "不支持的联系方式类型")
    private String contactType;

    @Pattern(regexp = "^$|\\d{5,15}$", message = "请输入正确的手机号")
    private String phone;

    @Email(message = "请输入正确的邮箱地址")
    @Size(max = 128, message = "邮箱过长")
    private String email;

    @AssertTrue(message = "请先阅读并同意用户协议")
    private Boolean agreementAccepted;

    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
