package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户列表项：包含跨租户的基础信息、权限、在线状态与封禁信息。
 */
@Data
public class AdminUserVO {

    private Long id;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    private String status;
    private Boolean enabled;
    private String tenantName;
    private String avatarUrl;
    private List<String> permissions;
    private boolean online;
    private int onlineSessions;
    private String banType;
    private LocalDateTime banUntil;
    private LocalDateTime bannedAt;
    private String banReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
