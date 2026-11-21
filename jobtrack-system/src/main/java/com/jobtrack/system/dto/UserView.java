package com.jobtrack.system.dto;

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
    private String tenantName;
    private String avatarUrl;
}
