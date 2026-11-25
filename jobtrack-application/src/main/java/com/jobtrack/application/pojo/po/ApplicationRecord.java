package com.jobtrack.application.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条投递记录。
 *
 * <p>必填字段只有公司名称与投递链接，其余信息按需填写；
 * 因此表中大部分列都允许为空，界面也不会把它们标成必填项。</p>
 *
 * <p>选填字段声明 updateStrategy = ALWAYS：编辑投递时用户清空某个字段
 * （请求中为 null / 空字符串）必须真正写回 NULL。MyBatis-Plus 默认的
 * NOT_NULL 策略会跳过 null 字段，导致清空操作被静默丢弃。</p>
 */
@Data
@TableName("applications")
public class ApplicationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    private Long tenantId;

    private String category;

    private String companyName;

    private String link;

    @TableField(value = "job_title", updateStrategy = FieldStrategy.ALWAYS)
    private String position;

    private String status;

    /** 投递所属年度，用于跨年度查看春招/秋招记录。 */
    @TableField("apply_year")
    @JsonProperty("year")
    private Integer applyYear;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String channel;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String city;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String salary;

    @TableField(value = "contact_name", updateStrategy = FieldStrategy.ALWAYS)
    private String contact;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactInfo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String workMode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String priority;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer interviewRound;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextActionAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String notes;

    private Boolean pinned;

    private LocalDateTime appliedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 当前状态已持续天数，查询后计算，不落库。 */
    @TableField(exist = false)
    private Integer statusDays;

    /** 停留在初筛的天数，仅当前状态为初筛时有值。 */
    @TableField(exist = false)
    private Integer screeningDays;
}
