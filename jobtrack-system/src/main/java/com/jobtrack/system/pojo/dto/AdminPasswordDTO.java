package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置用户密码：无需验证原密码。
 */
@Data
public class AdminPasswordDTO {

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 64, message = "新密码长度需为 6-64 个字符")
    private String newPassword;
}
