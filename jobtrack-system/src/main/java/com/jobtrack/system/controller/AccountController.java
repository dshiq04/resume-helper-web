package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.dto.AccessTokenCreateDTO;
import com.jobtrack.system.pojo.vo.AccessTokenLogPageVO;
import com.jobtrack.system.pojo.vo.AccessTokenVO;
import com.jobtrack.system.pojo.dto.ContactUpdateDTO;
import com.jobtrack.system.pojo.dto.TokenStatusDTO;
import com.jobtrack.system.pojo.dto.UserProfileDTO;
import com.jobtrack.system.pojo.vo.UserProfileVO;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AccessTokenService;
import com.jobtrack.system.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 账户设置：个人资料、用户名与授权码。
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccessTokenService accessTokenService;

    @GetMapping("/profile")
    public UserProfileVO profile() {
        return accountService.profile(SecurityUtils.current());
    }

    @PutMapping("/profile")
    public UserProfileVO saveProfile(@Valid @RequestBody UserProfileDTO request) {
        return accountService.saveProfile(SecurityUtils.current(), request);
    }

    /** 账号与安全 · 保存联系方式：占用检查通过后才能修改。 */
    @PutMapping("/contact")
    public UserProfileVO saveContact(@Valid @RequestBody ContactUpdateDTO request) {
        return accountService.saveContact(SecurityUtils.current(), request);
    }

    @PostMapping("/avatar")
    public UserProfileVO uploadAvatar(@RequestParam("file") MultipartFile file) {
        return accountService.uploadAvatar(SecurityUtils.current(), file);
    }

    @GetMapping("/tokens")
    public List<AccessTokenVO> tokens() {
        return accessTokenService.list(SecurityUtils.currentUserId());
    }

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessTokenVO createToken(@Valid @RequestBody AccessTokenCreateDTO request) {
        AuthPrincipal principal = SecurityUtils.current();
        return accessTokenService.create(principal.tenantId(), principal.id(), request);
    }

    @DeleteMapping("/tokens/{id}")
    public Map<String, Object> deleteToken(@PathVariable Long id) {
        accessTokenService.delete(SecurityUtils.currentUserId(), id);
        return Map.of("deleted", true);
    }

    @PutMapping("/tokens/{id}/enabled")
    public AccessTokenVO updateTokenStatus(@PathVariable Long id,
                                             @Valid @RequestBody TokenStatusDTO request) {
        return accessTokenService.setEnabled(SecurityUtils.currentUserId(), id, request.getEnabled());
    }

    @GetMapping("/tokens/logs")
    public AccessTokenLogPageVO tokenLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String result) {
        return accessTokenService.logs(SecurityUtils.currentUserId(), page, pageSize, name, result);
    }
}
