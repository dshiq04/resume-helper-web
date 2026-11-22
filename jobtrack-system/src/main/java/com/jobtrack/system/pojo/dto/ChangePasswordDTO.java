package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDTO {

    @NotBlank(message = "请输入当前密码")
    private String currentPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 64, message = "新密码长度需为 6-64 个字符")
    private String newPassword;

    @Size(max = 160, message = "手机号或邮箱过长")
    private String contact;

    /**
     * 联系方式类型：phone 或 email。旧客户端未传时按手机号/邮箱自动识别。
     */
    private String contactType;
}
