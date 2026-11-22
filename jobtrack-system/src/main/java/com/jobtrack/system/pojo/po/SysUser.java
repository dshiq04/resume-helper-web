package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录账号。用户名全局唯一，tenant_id 指向该账号独占的工作区。
 *
 * <p>sys_user 被租户拦截器忽略，否则登录时无法在未知租户的情况下查询用户。</p>
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String username;

    @JsonIgnore
    private String passwordHash;

    private String displayName;

    private String role;

    /** 账号状态：NORMAL 正常 / CANCELLED 已注销；封禁状态由 sys_user_ban 记录判断。 */
    private String status;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
