package com.jobtrack.system.controller;

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
import com.jobtrack.system.pojo.vo.UserProfileVO;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AdminService;
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
    public AdminUserPageVO list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return adminService.listUsers(search, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public AdminUserDetailVO detail(@PathVariable Long id) {
        return adminService.detail(id);
    }

    @PutMapping("/{id}")
    public AdminUserVO update(@PathVariable Long id,
                              @Valid @RequestBody AdminUserUpdateDTO request) {
        return adminService.updateUser(id, request);
    }

    @PutMapping("/{id}/profile")
    public UserProfileVO saveProfile(@PathVariable Long id,
                                     @Valid @RequestBody UserProfileDTO request) {
        return adminService.saveProfile(id, request);
    }

    @PutMapping("/{id}/contact")
    public UserProfileVO saveContact(@PathVariable Long id,
                                     @Valid @RequestBody ContactUpdateDTO request) {
        return adminService.saveContact(id, request);
    }

    @PostMapping("/{id}/avatar")
    public UserProfileVO uploadAvatar(@PathVariable Long id,
                                      @RequestParam("file") MultipartFile file) {
        return adminService.uploadAvatar(id, file);
    }

    @PostMapping("/{id}/password")
    public AdminUserVO resetPassword(@PathVariable Long id,
                                     @Valid @RequestBody AdminPasswordDTO request) {
        return adminService.resetPassword(id, request);
    }

    @PutMapping("/{id}/status")
    public AdminUserVO updateStatus(@PathVariable Long id,
                                    @Valid @RequestBody AdminStatusDTO request) {
        return adminService.updateStatus(SecurityUtils.currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        adminService.deleteUser(SecurityUtils.currentUserId(), id);
        return Map.of("deleted", true);
    }

    @GetMapping("/{id}/tokens")
    public List<AccessTokenVO> tokens(@PathVariable Long id) {
        return adminService.tokens(id);
    }

    @PostMapping("/{id}/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessTokenVO createToken(@PathVariable Long id,
                                     @Valid @RequestBody AccessTokenCreateDTO request) {
        return adminService.createToken(id, request);
    }

    @PutMapping("/{id}/tokens/{tokenId}/enabled")
    public AccessTokenVO updateTokenStatus(@PathVariable Long id,
                                           @PathVariable Long tokenId,
                                           @Valid @RequestBody TokenStatusDTO request) {
        return adminService.setTokenEnabled(id, tokenId, request.getEnabled());
    }

    @DeleteMapping("/{id}/tokens/{tokenId}")
    public Map<String, Object> deleteToken(@PathVariable Long id, @PathVariable Long tokenId) {
        adminService.deleteToken(id, tokenId);
        return Map.of("deleted", true);
    }

    @GetMapping("/{id}/tokens/logs")
    public AccessTokenLogPageVO tokenLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String result) {
        return adminService.tokenLogs(id, page, pageSize, name, result);
    }
}
