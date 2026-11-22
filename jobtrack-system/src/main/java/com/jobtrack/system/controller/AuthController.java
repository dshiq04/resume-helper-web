package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.vo.AuthVO;
import com.jobtrack.system.pojo.vo.CaptchaVO;
import com.jobtrack.system.pojo.vo.UserAgreementVO;
import com.jobtrack.system.pojo.dto.AgreementAcceptDTO;
import com.jobtrack.system.pojo.dto.ChangePasswordDTO;
import com.jobtrack.system.pojo.dto.ForgotPasswordResetDTO;
import com.jobtrack.system.pojo.dto.ForgotPasswordVerifyDTO;
import com.jobtrack.system.pojo.dto.LoginDTO;
import com.jobtrack.system.pojo.vo.OnlineStatusVO;
import com.jobtrack.system.pojo.dto.ProfileDTO;
import com.jobtrack.system.pojo.dto.RegisterDTO;
import com.jobtrack.system.pojo.vo.UserVO;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AuthService;
import com.jobtrack.system.service.CaptchaService;
import com.jobtrack.system.service.UserAgreementService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final UserAgreementService agreementService;

    /**
     * 页面启动时调用：账号体系始终启用，authenticated 表示当前 token 是否有效。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("enabled", true, "authenticated", SecurityUtils.authenticated());
    }

    @GetMapping("/captcha")
    public CaptchaVO captcha() {
        return captchaService.generate();
    }

    @PostMapping("/login")
    public AuthVO login(@Valid @RequestBody LoginDTO request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthVO register(@Valid @RequestBody RegisterDTO request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public UserVO me() {
        return authService.me(SecurityUtils.current());
    }

    /** 当前账号的用户协议签署状态，登录后用于判断是否需要补签。 */
    @GetMapping("/agreement")
    public UserAgreementVO agreement() {
        return agreementService.find(SecurityUtils.currentUserId());
    }

    /** 登录后补签用户协议，同时上传签署语言、时区、地点与来源 IP。 */
    @PostMapping("/agreement")
    public UserAgreementVO acceptAgreement(
            @Valid @RequestBody AgreementAcceptDTO request,
            HttpServletRequest servletRequest) {
        return agreementService.accept(SecurityUtils.current(), request,
                clientIp(servletRequest));
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

    @PutMapping("/me")
    public UserVO updateProfile(@Valid @RequestBody ProfileDTO request) {
        return authService.updateProfile(SecurityUtils.current(), request);
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordDTO request) {
        authService.changePassword(SecurityUtils.current(), request);
        return Map.of("changed", true);
    }

    /** 忘记密码第一步：验证用户名与手机号/邮箱。 */
    @PostMapping("/forgot-password/verify")
    public Map<String, Object> forgotPasswordVerify(
            @Valid @RequestBody ForgotPasswordVerifyDTO request) {
        authService.forgotPasswordVerify(request);
        return Map.of("verified", true);
    }

    /** 忘记密码第二步：重置密码并注销全部旧令牌。 */
    @PostMapping("/forgot-password/reset")
    public Map<String, Object> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetDTO request) {
        authService.forgotPasswordReset(request);
        return Map.of("reset", true);
    }

    @GetMapping("/online")
    public OnlineStatusVO online() {
        return authService.online(SecurityUtils.current());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        authService.logout(SecurityUtils.current());
        return Map.of("loggedOut", true);
    }
}
