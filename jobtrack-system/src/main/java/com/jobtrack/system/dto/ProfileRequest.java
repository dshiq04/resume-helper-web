package com.jobtrack.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileRequest {

    @NotBlank(message = "请输入昵称")
    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String displayName;
}
