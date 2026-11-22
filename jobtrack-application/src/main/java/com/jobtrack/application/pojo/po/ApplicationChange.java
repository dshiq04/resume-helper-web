package com.jobtrack.application.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 状态里程碑。每个投递的每个状态只保留一条，时间线因此稳定，
 * 来回切换状态不会产生重复节点。
 */
@Data
@TableName("application_changes")
public class ApplicationChange {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    private Long tenantId;

    private Long applicationId;

    private String status;

    private LocalDateTime changedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
