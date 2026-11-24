package com.jobtrack.system.controller;

import com.jobtrack.common.api.Result;
import com.jobtrack.system.pojo.dto.TokenValidationDTO;
import com.jobtrack.system.pojo.vo.TokenValidationVO;
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
    public ResponseEntity<Result<TokenValidationVO>> validate(
            @Valid @RequestBody TokenValidationDTO request,
            HttpServletRequest httpRequest) {
        TokenValidationVO response = accessTokenService.validate(request.getCode(),
                clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        if (response.isValid()) {
            return ResponseEntity.ok(Result.success(response));
        }
        int status = switch (String.valueOf(response.getReason())) {
            case "expired" -> 410;
            case "disabled", "banned" -> 403;
            default -> 401;
        };
        return ResponseEntity.status(status)
                .body(Result.error(status, failureMessage(response)));
    }

    private String failureMessage(TokenValidationVO response) {
        if (response.getMessage() != null && !response.getMessage().isBlank()) {
            return response.getMessage();
        }
        return switch (String.valueOf(response.getReason())) {
            case "expired" -> "授权码已过期";
            case "disabled" -> "授权码已停用或账号状态异常";
            case "banned" -> "该账号已被封禁";
            default -> "授权码无效或已被删除";
        };
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
