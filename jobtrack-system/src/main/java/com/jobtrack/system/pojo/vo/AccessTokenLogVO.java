package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AccessTokenLogVO {

    private Long id;
    private Long tokenId;
    private String tokenName;
    private String codePrefix;
    private String scope;
    private List<String> resources;
    private String result;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
