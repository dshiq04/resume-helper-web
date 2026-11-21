package com.jobtrack.system.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UsernameUpdateRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$",
            message = "用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位")
    private String username;
}
