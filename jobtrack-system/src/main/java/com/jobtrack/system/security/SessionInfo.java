package com.jobtrack.system.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线会话快照，存入 Redis 或进程内 Map。
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
}
