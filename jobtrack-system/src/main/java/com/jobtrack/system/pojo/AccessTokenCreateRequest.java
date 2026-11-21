package com.jobtrack.system.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AccessTokenCreateRequest {

    @Size(max = 64)
    private String name;

    /** read / write / readwrite */
    @NotBlank(message = "请选择权限范围")
    private String scope;

    /** 可查询的数据范围：applications / resumes / statistics / profile，为空表示全部 */
    private List<String> resources;

    /** 为空表示长期有效 */
    private LocalDate expiresOn;
}
