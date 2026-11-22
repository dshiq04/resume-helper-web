package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.AuthResponse;
import com.jobtrack.system.pojo.CaptchaResponse;
import com.jobtrack.system.pojo.ChangePasswordRequest;
import com.jobtrack.system.pojo.ForgotPasswordResetRequest;
import com.jobtrack.system.pojo.ForgotPasswordVerifyRequest;
import com.jobtrack.system.pojo.LoginRequest;
import com.jobtrack.system.pojo.OnlineStatusResponse;
import com.jobtrack.system.pojo.ProfileRequest;
import com.jobtrack.system.pojo.RegisterRequest;
import com.jobtrack.system.pojo.UserView;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AuthService;
import com.jobtrack.system.service.CaptchaService;
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
    private final CaptchaService captchaService;

    /**
     * 页面启动时调用：账号体系始终启用，authenticated 表示当前 token 是否有效。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("enabled", true, "authenticated", SecurityUtils.authenticated());
    }

    @GetMapping("/captcha")
    public CaptchaResponse captcha() {
        return captchaService.generate();
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

    /** 忘记密码第一步：验证用户名与手机号/邮箱。 */
    @PostMapping("/forgot-password/verify")
    public Map<String, Object> forgotPasswordVerify(
            @Valid @RequestBody ForgotPasswordVerifyRequest request) {
        authService.forgotPasswordVerify(request);
        return Map.of("verified", true);
    }

    /** 忘记密码第二步：重置密码并注销全部旧令牌。 */
    @PostMapping("/forgot-password/reset")
    public Map<String, Object> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {
        authService.forgotPasswordReset(request);
        return Map.of("reset", true);
    }

    @GetMapping("/online")
    public OnlineStatusResponse online() {
        return authService.online(SecurityUtils.current());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        authService.logout(SecurityUtils.current());
        return Map.of("loggedOut", true);
    }
}
