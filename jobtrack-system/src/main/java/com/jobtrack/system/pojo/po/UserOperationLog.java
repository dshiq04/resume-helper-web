package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户操作记录。由 Spring AOP 捕获控制器操作后通过 RabbitMQ 异步写入。
 *
 * <p>用户名与昵称冗余保存，避免用户删除后操作记录失去主体信息。</p>
 */
@Data
@TableName("user_operation_log")
public class UserOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String username;

    private String displayName;

    /** LOGIN / LOGOUT / REGISTER / AGREEMENT / PROFILE / SECURITY / APPLICATION / RESUME / TOKEN / ACCOUNT / ADMIN / OTHER */
    private String operationType;

    /** CREATE / UPDATE / DELETE / LOGIN / LOGOUT / REGISTER / SIGN / OTHER */
    private String action;

    private String description;

    private String method;

    private String path;

    private String ip;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
