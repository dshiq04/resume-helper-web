package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端用户详情：账号信息 + 账户设置中的个人资料。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailVO {

    private AdminUserVO user;
    private UserProfileVO profile;
}
