package com.jobtrack.system.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 在线会话快照，存入 Redis 或进程内 Map。
 * 令牌与权限一起存入 Redis，过期时间与令牌一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {

    private Long userId;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    /** 用户权限编码列表，从 sys_user_permission 查询而来。 */
    private List<String> permissions;

    public SessionInfo(Long userId, Long tenantId, String username,
                       String displayName, String role) {
        this(userId, tenantId, username, displayName, role, List.of());
    }
}
