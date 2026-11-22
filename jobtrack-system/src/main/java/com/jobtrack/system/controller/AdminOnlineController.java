package com.jobtrack.system.controller;

import com.jobtrack.system.pojo.vo.OnlineUserVO;
import com.jobtrack.system.security.SecurityUtils;
import com.jobtrack.system.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理员 · 在线用户：查看全部租户的在线会话并踢出指定用户。
 */
@RestController
@RequestMapping("/api/admin/online")
@RequiredArgsConstructor
public class AdminOnlineController {

    private final AdminService adminService;

    @GetMapping
    public List<OnlineUserVO> online() {
        return adminService.onlineUsers();
    }

    @PostMapping("/{userId}/kick")
    public Map<String, Object> kick(@PathVariable Long userId) {
        int sessions = adminService.kick(SecurityUtils.currentUserId(), userId);
        return Map.of("kicked", true, "sessions", sessions);
    }
}
