package com.jobtrack.system.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 授权码校验结果。valid=false 时 reason 说明原因：
 * invalid（不存在）/ expired（已过期）/ disabled（账号已停用）。
 */
@Data
public class TokenValidationResponse {

    private boolean valid;
    private String reason;
    private String scope;
    private List<String> resources;
    private LocalDate expiresOn;
    private Long userId;
    private Long tenantId;
    private String username;
    private String displayName;

    public static TokenValidationResponse invalid(String reason) {
        TokenValidationResponse response = new TokenValidationResponse();
        response.setValid(false);
        response.setReason(reason);
        return response;
    }
}
