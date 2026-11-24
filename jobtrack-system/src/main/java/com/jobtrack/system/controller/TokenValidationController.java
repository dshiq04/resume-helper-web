package com.jobtrack.system.controller;

import com.jobtrack.common.api.Result;
import com.jobtrack.system.pojo.dto.TokenValidationDTO;
import com.jobtrack.system.pojo.vo.TokenValidationVO;
import com.jobtrack.system.service.AccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public Result<TokenValidationVO> validate(
            @Valid @RequestBody TokenValidationDTO request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        TokenValidationVO result = accessTokenService.validate(request.getCode(),
                clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        if (result.isValid()) {
            return Result.success(result);
        }
        int status = switch (String.valueOf(result.getReason())) {
            case "expired" -> 410;
            case "disabled", "banned" -> 403;
            default -> 401;
        };
        response.setStatus(status);
        return Result.error(status, failureMessage(result));
    }

    private String failureMessage(TokenValidationVO result) {
        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            return result.getMessage();
        }
        return switch (String.valueOf(result.getReason())) {
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
