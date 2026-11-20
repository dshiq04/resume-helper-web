package com.jobtrack.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 32, message = "用户名长度需为 3-32 个字符")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 个字符")
    private String password;

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String displayName;

    @Size(max = 64, message = "工作区名称不能超过 64 个字符")
    private String tenantName;
}
