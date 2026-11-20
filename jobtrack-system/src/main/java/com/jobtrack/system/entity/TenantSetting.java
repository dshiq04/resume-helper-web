package com.jobtrack.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按租户隔离的键值配置，例如闲置自动锁定。
 */
@Data
@TableName("tenant_setting")
public class TenantSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String settingKey;

    private String settingValue;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
