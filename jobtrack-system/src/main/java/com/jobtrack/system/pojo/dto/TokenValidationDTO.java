package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenValidationDTO {

    @NotBlank(message = "授权码不能为空")
    private String code;
}
