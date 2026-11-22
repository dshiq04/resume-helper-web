package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.dto.AdminPermissionDTO;
import com.jobtrack.system.pojo.vo.AdminUserVO;
import com.jobtrack.system.pojo.vo.PermissionVO;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理员 · 权限授权：把系统中的权限授予其他成员，或收回权限。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminService adminService;

    @GetMapping("/permissions")
    public List<PermissionVO> permissions() {
        return adminService.permissions();
    }

    @GetMapping("/users/{userId}/permissions")
    public List<String> userPermissions(@PathVariable Long userId) {
        return adminService.userPermissions(userId);
    }

    @PostMapping("/users/{userId}/permissions")
    public AdminUserVO grant(@PathVariable Long userId,
                             @Valid @RequestBody AdminPermissionDTO request) {
        return adminService.grantPermission(
                SecurityUtils.currentUserId(), userId, request.getCode());
    }

    @DeleteMapping("/users/{userId}/permissions/{code}")
    public AdminUserVO revoke(@PathVariable Long userId, @PathVariable String code) {
        return adminService.revokePermission(
                SecurityUtils.currentUserId(), userId, code);
    }
}
