package com.jobtrack.system.security;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 在线会话快照，存入 Redis 或进程内 Map。
 * 令牌与权限一起存入 Redis，过期时间与令牌一致。
 */
@Data
@NoArgsConstructor
public class SessionInfo {

    private Long userId;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    /** 用户权限编码列表，从 sys_user_permission 查询而来。 */
    private List<String> permissions;
    /** 会话创建时间；用于在撤销墓碑生效后区分旧会话与新登录。 */
    private Instant createdAt;

    public SessionInfo(Long userId, Long tenantId, String username,
                       String displayName, String role) {
        this(userId, tenantId, username, displayName, role, List.of());
    }

    public SessionInfo(Long userId, Long tenantId, String username,
                       String displayName, String role, List<String> permissions) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.permissions = permissions;
        this.createdAt = Instant.now();
    }
}
