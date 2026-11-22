package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理端在线用户：按账号聚合在线会话。
 */
@Data
public class OnlineUserVO {

    private Long userId;
    private Long tenantId;
    private String username;
    private String displayName;
    private String tenantName;
    private String avatarUrl;
    private int sessions;
    /** 各会话剩余有效秒数，按从长到短排列。 */
    private List<Long> remainingSeconds;
}
