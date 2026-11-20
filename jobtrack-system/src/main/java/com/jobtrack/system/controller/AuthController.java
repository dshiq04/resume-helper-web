package com.jobtrack.system.controller;

import com.jobtrack.system.dto.AuthResponse;
import com.jobtrack.system.dto.ChangePasswordRequest;
import com.jobtrack.system.dto.LoginRequest;
import com.jobtrack.system.dto.ProfileRequest;
import com.jobtrack.system.dto.RegisterRequest;
import com.jobtrack.system.dto.UserView;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 页面启动时调用：账号体系始终启用，authenticated 表示当前 token 是否有效。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("enabled", true, "authenticated", SecurityUtils.authenticated());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public UserView me() {
        return authService.me(SecurityUtils.current());
    }

    @PutMapping("/me")
    public UserView updateProfile(@Valid @RequestBody ProfileRequest request) {
        return authService.updateProfile(SecurityUtils.current(), request);
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.current(), request);
        return Map.of("changed", true);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        // JWT 无状态，客户端删除 token 即可；接口保留用于统一前端调用。
        return Map.of("loggedOut", true);
    }
}
