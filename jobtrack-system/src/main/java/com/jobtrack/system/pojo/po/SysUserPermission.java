package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户与权限的对应表：一个用户可拥有多个权限。
 * 登录时从这里查询用户权限，不再读取 sys_user.role。
 */
@Data
@TableName("sys_user_permission")
public class SysUserPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long permissionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
