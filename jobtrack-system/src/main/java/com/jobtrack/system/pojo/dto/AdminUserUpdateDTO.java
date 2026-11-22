package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员修改用户基础资料：用户名、显示名称、工作区名称。
 */
@Data
public class AdminUserUpdateDTO {

    @NotBlank(message = "请输入用户名")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$",
            message = "用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位")
    private String username;

    @NotBlank(message = "请输入显示名称")
    @Size(max = 64, message = "显示名称不能超过 64 个字符")
    private String displayName;

    @NotBlank(message = "请输入工作区名称")
    @Size(max = 64, message = "工作区名称不能超过 64 个字符")
    private String tenantName;
}
