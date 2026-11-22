package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户协议签署记录。signed=false 表示尚未签署，其余字段为空。
 */
@Data
public class UserAgreementVO {

    private boolean signed;
    private Long userId;
    private String username;
    private String displayName;
    private String tenantName;
    private LocalDateTime acceptedAt;
    /** 签署时界面语言，用于按签署语言展示与导出协议正文。 */
    private String language;
    private String signerName;
    private String location;
    private String timezone;
    private String signedIp;
}
