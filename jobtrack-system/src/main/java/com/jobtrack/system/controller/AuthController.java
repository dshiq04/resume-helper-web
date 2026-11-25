package com.jobtrack.system.controller;

import com.jobtrack.common.api.Result;

import com.jobtrack.system.pojo.vo.AuthVO;
import com.jobtrack.system.pojo.vo.CaptchaVO;
import com.jobtrack.system.pojo.vo.FormKeyVO;
import com.jobtrack.system.pojo.vo.UserAgreementVO;
import com.jobtrack.system.pojo.dto.AgreementAcceptDTO;
import com.jobtrack.system.pojo.dto.ChangePasswordDTO;
import com.jobtrack.system.pojo.dto.EncryptedFormDTO;
import com.jobtrack.system.pojo.dto.ForgotPasswordResetDTO;
import com.jobtrack.system.pojo.dto.ForgotPasswordVerifyDTO;
import com.jobtrack.system.pojo.dto.LoginDTO;
import com.jobtrack.system.pojo.vo.OnlineStatusVO;
import com.jobtrack.system.pojo.dto.ProfileDTO;
import com.jobtrack.system.pojo.dto.RegisterDTO;
import com.jobtrack.system.pojo.vo.UserVO;
import com.jobtrack.system.security.FormCryptoService;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AuthService;
import com.jobtrack.system.service.CaptchaService;
import com.jobtrack.system.service.UserAgreementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final UserAgreementService agreementService;
    private final FormCryptoService formCryptoService;

    /**
     * 页面启动时调用：账号体系始终启用，authenticated 表示当前 token 是否有效。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of("enabled", true, "authenticated", SecurityUtils.authenticated()));
    }

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    /**
     * 登录页初始化时获取表单加密公钥。
     * 公钥可以公开；私钥只保存在服务端，用于解密登录、注册、修改密码表单。
     */
    @GetMapping("/public-key")
    public Result<FormKeyVO> publicKey() {
        return Result.success(formCryptoService.publicKey());
    }

    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody EncryptedFormDTO request) {
        LoginDTO login = formCryptoService.decrypt(request, LoginDTO.class);
        return Result.success(authService.login(login));
    }

    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody EncryptedFormDTO request) {
        RegisterDTO register = formCryptoService.decrypt(request, RegisterDTO.class);
        return Result.success(authService.register(register));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.me(SecurityUtils.current()));
    }

    /** 当前账号的用户协议签署状态，登录后用于判断是否需要补签。 */
    @GetMapping("/agreement")
    public Result<UserAgreementVO> agreement() {
        return Result.success(agreementService.find(SecurityUtils.currentUserId()));
    }

    /** 登录后补签用户协议，同时上传签署语言、时区、地点与来源 IP。 */
    @PostMapping("/agreement")
    public Result<UserAgreementVO> acceptAgreement(
            @Valid @RequestBody AgreementAcceptDTO request,
            HttpServletRequest servletRequest) {
        return Result.success(agreementService.accept(SecurityUtils.current(), request,
                clientIp(servletRequest)));
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
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileDTO request) {
        return Result.success(authService.updateProfile(SecurityUtils.current(), request));
    }

    @PostMapping("/change-password")
    public Result<Map<String, Object>> changePassword(@Valid @RequestBody EncryptedFormDTO request) {
        ChangePasswordDTO change = formCryptoService.decrypt(request, ChangePasswordDTO.class);
        authService.changePassword(SecurityUtils.current(), change);
        return Result.success(Map.of("changed", true));
    }

    /** 忘记密码第一步：验证用户名与手机号/邮箱。 */
    @PostMapping("/forgot-password/verify")
    public Result<Map<String, Object>> forgotPasswordVerify(
            @Valid @RequestBody EncryptedFormDTO request) {
        ForgotPasswordVerifyDTO verify =
                formCryptoService.decrypt(request, ForgotPasswordVerifyDTO.class);
        authService.forgotPasswordVerify(verify);
        return Result.success(Map.of("verified", true));
    }

    /** 忘记密码第二步：重置密码并注销全部旧令牌。 */
    @PostMapping("/forgot-password/reset")
    public Result<Map<String, Object>> forgotPasswordReset(
            @Valid @RequestBody EncryptedFormDTO request) {
        ForgotPasswordResetDTO reset =
                formCryptoService.decrypt(request, ForgotPasswordResetDTO.class);
        authService.forgotPasswordReset(reset);
        return Result.success(Map.of("reset", true));
    }

    @GetMapping("/online")
    public Result<OnlineStatusVO> online() {
        return Result.success(authService.online(SecurityUtils.current()));
    }

    @PostMapping("/logout")
    public Result<Map<String, Object>> logout() {
        authService.logout(SecurityUtils.current());
        return Result.success(Map.of("loggedOut", true));
    }
}
