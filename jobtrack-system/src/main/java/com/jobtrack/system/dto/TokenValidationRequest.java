package com.jobtrack.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenValidationRequest {

    @NotBlank(message = "授权码不能为空")
    private String code;
}
