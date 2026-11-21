package com.jobtrack.system.controller;

import com.jobtrack.system.dto.TokenValidationRequest;
import com.jobtrack.system.dto.TokenValidationResponse;
import com.jobtrack.system.service.AccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public TokenValidationResponse validate(@Valid @RequestBody TokenValidationRequest request,
                                            HttpServletRequest httpRequest) {
        return accessTokenService.validate(request.getCode(),
                clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
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
