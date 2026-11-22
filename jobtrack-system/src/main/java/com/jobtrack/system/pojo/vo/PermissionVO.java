package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限字典项，供管理员授权页展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionVO {

    private Long id;
    private String code;
    private String name;
    private String description;
}
