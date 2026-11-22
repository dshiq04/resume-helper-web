package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户最后一次操作与最后一次登录时间，用于用户列表与详情。 */
@Data
public class UserActivityVO {

    private Long userId;
    private LocalDateTime lastOperationAt;
    private LocalDateTime lastLoginAt;
}
