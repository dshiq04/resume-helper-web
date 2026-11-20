package com.jobtrack.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "请输入用户名")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;

    @Size(max = 64)
    private String captchaId;

    @Size(max = 16)
    private String captchaCode;
}
