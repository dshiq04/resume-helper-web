package com.jobtrack.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "请输入当前密码")
    private String currentPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 64, message = "新密码长度需为 6-64 个字符")
    private String newPassword;
}
