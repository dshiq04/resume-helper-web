package com.jobtrack.system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserView {

    public UserView(Long id2, Long tenantId2, String username2, String displayName2, String role2, Object object) {
        //TODO Auto-generated constructor stub
    }
    private Long id;
    private Long tenantId;
    private String username;
    private String displayName;
    private String role;
    private String tenantName;
    private String avatarUrl;
}
