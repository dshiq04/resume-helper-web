package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TokenStatusDTO {

    @NotNull(message = "请指定启用状态")
    private Boolean enabled;
}
