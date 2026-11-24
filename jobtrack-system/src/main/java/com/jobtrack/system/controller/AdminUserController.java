package com.jobtrack.system.controller;

import com.jobtrack.common.api.Result;

import com.jobtrack.system.pojo.dto.AccessTokenCreateDTO;
import com.jobtrack.system.pojo.dto.AdminPasswordDTO;
import com.jobtrack.system.pojo.dto.AdminStatusDTO;
import com.jobtrack.system.pojo.dto.AdminUserUpdateDTO;
import com.jobtrack.system.pojo.dto.ContactUpdateDTO;
import com.jobtrack.system.pojo.dto.TokenStatusDTO;
import com.jobtrack.system.pojo.dto.UserProfileDTO;
import com.jobtrack.system.pojo.vo.AccessTokenLogPageVO;
import com.jobtrack.system.pojo.vo.AccessTokenVO;
import com.jobtrack.system.pojo.vo.AdminUserDetailVO;
import com.jobtrack.system.pojo.vo.AdminUserPageVO;
import com.jobtrack.system.pojo.vo.AdminUserVO;
import com.jobtrack.system.pojo.vo.UserAgreementVO;
import com.jobtrack.system.pojo.vo.UserProfileVO;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 管理员 · 用户管理。所有接口跨租户，由 SecurityConfig 限制为管理员访问。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping
    public Result<AdminUserPageVO> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(adminService.listUsers(search, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<AdminUserDetailVO> detail(@PathVariable Long id) {
        return Result.success(adminService.detail(id));
    }

    /** 管理端查看用户协议签署记录与签署信息，用于展示与导出。 */
    @GetMapping("/{id}/agreement")
    public Result<UserAgreementVO> agreement(@PathVariable Long id) {
        return Result.success(adminService.userAgreement(id));
    }

    @PutMapping("/{id}")
    public Result<AdminUserVO> update(@PathVariable Long id,
                              @Valid @RequestBody AdminUserUpdateDTO request) {
        return Result.success(adminService.updateUser(id, request));
    }

    @PutMapping("/{id}/profile")
    public Result<UserProfileVO> saveProfile(@PathVariable Long id,
                                     @Valid @RequestBody UserProfileDTO request) {
        return Result.success(adminService.saveProfile(id, request));
    }

    @PutMapping("/{id}/contact")
    public Result<UserProfileVO> saveContact(@PathVariable Long id,
                                     @Valid @RequestBody ContactUpdateDTO request) {
        return Result.success(adminService.saveContact(id, request));
    }

    @PostMapping("/{id}/avatar")
    public Result<UserProfileVO> uploadAvatar(@PathVariable Long id,
                                      @RequestParam("file") MultipartFile file) {
        return Result.success(adminService.uploadAvatar(id, file));
    }

    @PostMapping("/{id}/password")
    public Result<AdminUserVO> resetPassword(@PathVariable Long id,
                                     @Valid @RequestBody AdminPasswordDTO request) {
        return Result.success(adminService.resetPassword(id, request));
    }

    @PutMapping("/{id}/status")
    public Result<AdminUserVO> updateStatus(@PathVariable Long id,
                                    @Valid @RequestBody AdminStatusDTO request) {
        return Result.success(adminService.updateStatus(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminService.deleteUser(SecurityUtils.currentUserId(), id);
        return Result.success(Map.of("deleted", true));
    }

    @GetMapping("/{id}/tokens")
    public Result<List<AccessTokenVO>> tokens(@PathVariable Long id) {
        return Result.success(adminService.tokens(id));
    }

    @PostMapping("/{id}/tokens")
    public Result<AccessTokenVO> createToken(@PathVariable Long id,
                                     @Valid @RequestBody AccessTokenCreateDTO request) {
        return Result.success(adminService.createToken(id, request));
    }

    @PutMapping("/{id}/tokens/{tokenId}/enabled")
    public Result<AccessTokenVO> updateTokenStatus(@PathVariable Long id,
                                           @PathVariable Long tokenId,
                                           @Valid @RequestBody TokenStatusDTO request) {
        return Result.success(adminService.setTokenEnabled(id, tokenId, request.getEnabled()));
    }

    @DeleteMapping("/{id}/tokens/{tokenId}")
    public Result<Map<String, Object>> deleteToken(@PathVariable Long id, @PathVariable Long tokenId) {
        adminService.deleteToken(id, tokenId);
        return Result.success(Map.of("deleted", true));
    }

    @GetMapping("/{id}/tokens/logs")
    public Result<AccessTokenLogPageVO> tokenLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String result) {
        return Result.success(adminService.tokenLogs(id, page, pageSize, name, result));
    }
}
