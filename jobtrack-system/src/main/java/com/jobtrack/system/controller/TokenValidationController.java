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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 供浏览器扩展 / 第三方工具调用的授权码校验接口，无需登录。
 * 每次调用都会写入 access_token_log。
 */
@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenValidationController {

    /** 单 IP 每分钟最多校验次数，避免匿名接口被刷表。 */
    private static final int MAX_PER_MINUTE = 60;
    private static final long WINDOW_MS = 60_000L;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    private final AccessTokenService accessTokenService;

    @PostMapping("/validate")
    public Result<TokenValidationVO> validate(
            @Valid @RequestBody TokenValidationDTO request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String ip = clientIp(httpRequest);
        if (!allow(ip)) {
            response.setStatus(429);
            return Result.error(429, "请求过于频繁，请稍后再试");
        }
        TokenValidationVO result = accessTokenService.validate(request.getCode(),
                ip, httpRequest.getHeader("User-Agent"));
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

    /** 简单的滑动窗口限流：只保护校验接口，避免无效授权码把日志表刷满。 */
    private boolean allow(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(ip == null ? "-" : ip,
                key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_PER_MINUTE) {
                return false;
            }
            window.addLast(now);
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        return true;
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
