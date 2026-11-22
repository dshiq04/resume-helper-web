package com.jobtrack.system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserView {

    private Long id;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    /** 账号状态：NORMAL 正常 / CANCELLED 已注销。 */
    private String status;
    private String tenantName;
    private String avatarUrl;
}
