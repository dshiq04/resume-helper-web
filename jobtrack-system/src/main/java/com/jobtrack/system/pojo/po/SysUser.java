package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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

    /** 账号状态：NORMAL 正常 / CANCELLED 已注销 / BANNED 已封禁。 */
    private String status;

    /** 封禁类型：PERMANENT 永久 / TEMPORARY 暂时；仅 status=BANNED 时有值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String banType;

    /** 暂时封禁的到期时间；永久封禁为空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime banUntil;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime bannedAt;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
