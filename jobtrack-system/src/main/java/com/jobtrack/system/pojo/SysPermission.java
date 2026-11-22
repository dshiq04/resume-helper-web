package com.jobtrack.system.pojo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限字典表。系统内置 USER / ADMIN 两种权限，可按需扩展。
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码，如 USER / ADMIN，唯一。 */
    private String code;

    /** 展示名称，如 普通用户 / 管理员。 */
    private String name;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
