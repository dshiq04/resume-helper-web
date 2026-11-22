package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    /** 账号状态：NORMAL 正常 / CANCELLED 已注销 / BANNED 已封禁。 */
    private String status;
    private String tenantName;
    private String avatarUrl;
    /** 用户拥有的权限编码，如 USER / ADMIN / ADMIN_CONSOLE。 */
    private List<String> permissions;
    private Boolean enabled;
    /** 封禁类型：PERMANENT 永久 / TEMPORARY 暂时。 */
    private String banType;
    private LocalDateTime banUntil;
    private LocalDateTime bannedAt;
    private LocalDateTime createdAt;

    public UserVO(Long id, Long tenantId, String username, String displayName,
                  String role, String status, String tenantName, String avatarUrl) {
        this(id, tenantId, username, displayName, role, status, tenantName, avatarUrl,
                null, null, null, null, null, null);
    }
}
