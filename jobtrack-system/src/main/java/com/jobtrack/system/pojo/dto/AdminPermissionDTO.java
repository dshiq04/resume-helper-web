package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员授权 / 收回权限。
 */
@Data
public class AdminPermissionDTO {

    @NotBlank(message = "请选择权限")
    private String code;
}
