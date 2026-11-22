package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户操作记录列表项。 */
@Data
public class UserOperationVO {

    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String operationType;
    private String action;
    private String description;
    private String method;
    private String path;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
