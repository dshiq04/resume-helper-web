package com.jobtrack.system.controller;

import com.jobtrack.system.dto.AccessTokenCreateRequest;
import com.jobtrack.system.dto.AccessTokenLogPage;
import com.jobtrack.system.dto.AccessTokenView;
import com.jobtrack.system.dto.TokenStatusRequest;
import com.jobtrack.system.dto.UserProfileRequest;
import com.jobtrack.system.dto.UserProfileView;
import com.jobtrack.system.dto.UserView;
import com.jobtrack.system.dto.UsernameUpdateRequest;
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
    public UserProfileView profile() {
        return accountService.profile(SecurityUtils.current());
    }

    @PutMapping("/profile")
    public UserProfileView saveProfile(@Valid @RequestBody UserProfileRequest request) {
        return accountService.saveProfile(SecurityUtils.current(), request);
    }

    @PutMapping("/username")
    public UserView changeUsername(@Valid @RequestBody UsernameUpdateRequest request) {
        return accountService.changeUsername(SecurityUtils.current(), request.getUsername());
    }

    @GetMapping("/tokens")
    public List<AccessTokenView> tokens() {
        return accessTokenService.list(SecurityUtils.currentUserId());
    }

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessTokenView createToken(@Valid @RequestBody AccessTokenCreateRequest request) {
        AuthPrincipal principal = SecurityUtils.current();
        return accessTokenService.create(principal.tenantId(), principal.id(), request);
    }

    @DeleteMapping("/tokens/{id}")
    public Map<String, Object> deleteToken(@PathVariable Long id) {
        accessTokenService.delete(SecurityUtils.currentUserId(), id);
        return Map.of("deleted", true);
    }

    @PutMapping("/tokens/{id}/enabled")
    public AccessTokenView updateTokenStatus(@PathVariable Long id,
                                             @Valid @RequestBody TokenStatusRequest request) {
        return accessTokenService.setEnabled(SecurityUtils.currentUserId(), id, request.getEnabled());
    }

    @GetMapping("/tokens/logs")
    public AccessTokenLogPage tokenLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String result) {
        return accessTokenService.logs(SecurityUtils.currentUserId(), page, pageSize, name, result);
    }
}
