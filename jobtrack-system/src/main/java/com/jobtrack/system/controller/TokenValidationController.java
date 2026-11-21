package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.TokenValidationRequest;
import com.jobtrack.system.pojo.TokenValidationResponse;
import com.jobtrack.system.service.AccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供浏览器扩展 / 第三方工具调用的授权码校验接口，无需登录。
 * 每次调用都会写入 access_token_log。
 */
@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenValidationController {

    private final AccessTokenService accessTokenService;

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @Valid @RequestBody TokenValidationRequest request,
            HttpServletRequest httpRequest) {
        TokenValidationResponse response = accessTokenService.validate(request.getCode(),
                clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        if (response.isValid()) {
            return ResponseEntity.ok(response);
        }
        int status = switch (String.valueOf(response.getReason())) {
            case "expired" -> 410;
            case "disabled" -> 403;
            default -> 401;
        };
        return ResponseEntity.status(status).body(response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
