package com.jobtrack.application.controller;

import com.jobtrack.application.pojo.dto.CancelConfirmDTO;
import com.jobtrack.application.pojo.vo.CancelStatusVO;
import com.jobtrack.application.pojo.dto.CancelVerifyDTO;
import com.jobtrack.application.service.AccountCancelService;
import com.jobtrack.system.security.AuthPrincipal;
import com.jobtrack.system.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户注销：条件检查、身份验证与注销执行。
 */
@RestController
@RequestMapping("/api/account/cancel")
@RequiredArgsConstructor
public class AccountCancelController {

    private final AccountCancelService cancelService;

    /** 注销条件检查：投递已清除、简历库已删除、没有普通用户以外的权限。 */
    @GetMapping("/status")
    public CancelStatusVO status() {
        return cancelService.status(SecurityUtils.current());
    }

    /** 验证保留的手机号或邮箱与密码。 */
    @PostMapping("/verify")
    public Map<String, Object> verify(@Valid @RequestBody CancelVerifyDTO request) {
        AuthPrincipal principal = SecurityUtils.current();
        cancelService.verify(principal, request);
        return Map.of("verified", true);
    }

    /** 最终确认注销：验证码 + 身份验证，通过后把状态改成注销。 */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@Valid @RequestBody CancelConfirmDTO request) {
        AuthPrincipal principal = SecurityUtils.current();
        cancelService.confirm(principal, request);
        return Map.of("cancelled", true);
    }
}
