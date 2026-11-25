package com.jobtrack.system.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AccessTokenVO {

    private Long id;
    private String name;
    /** 完整授权码仅在创建成功时返回一次；列表接口为 null。 */
    private String code;
    /** 脱敏后的授权码，可安全用于列表展示。 */
    private String codeMasked;
    private String scope;
    private Boolean enabled;
    private List<String> resources;
    private LocalDate expiresOn;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
