package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封禁记录。每次封禁新增一条记录，解除封禁时写入 unbannedAt，
 * 历史记录保留以便审计。封禁状态、时间与原因都从这里读取。
 */
@Data
@TableName("sys_user_ban")
public class SysUserBan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** PERMANENT 永久 / TEMPORARY 暂时。 */
    private String banType;

    /** 封禁原因，展示给被封禁用户。 */
    private String reason;

    private LocalDateTime bannedAt;

    /** 暂时封禁的到期时间；永久封禁为空。 */
    private LocalDateTime banUntil;

    /** 手动解除封禁的时间；为空表示尚未手动解除。 */
    private LocalDateTime unbannedAt;

    /** 执行封禁的管理员 ID。 */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
