package com.jobtrack.system.pojo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TokenStatusRequest {

    @NotNull(message = "请指定启用状态")
    private Boolean enabled;
}
