package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AccessTokenVO {

    private Long id;
    private String name;
    private String code;
    private String scope;
    private Boolean enabled;
    private List<String> resources;
    private LocalDate expiresOn;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
