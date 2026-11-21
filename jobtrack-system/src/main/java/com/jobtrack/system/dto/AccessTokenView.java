package com.jobtrack.system.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AccessTokenView {

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
