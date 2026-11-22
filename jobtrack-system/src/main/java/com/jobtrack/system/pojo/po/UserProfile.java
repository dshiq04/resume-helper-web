package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账号的扩展个人信息，与 sys_user 一对一。
 * 所有字段均为选填，由用户在「账户设置 → 个人资料」中维护。
 *
 * <p>updateStrategy = ALWAYS 允许用户把已填写的字段清空，
 * 否则 MyBatis-Plus 默认会忽略 null 字段，导致清空操作无效。</p>
 */
@Data
@TableName("user_profile")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String realName;

    /** male / female / other */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String gender;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate birthday;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String location;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String hometown;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String website;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String github;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String linkedin;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String wechat;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String targetPosition;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String expectedSalary;

    /** onsite / hybrid / remote */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String workMode;

    /** looking / employed / student */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String jobStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate availableDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String school;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String major;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String degree;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer graduationYear;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer experienceYears;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentCompany;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentTitle;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String skills;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String languages;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bio;

    /** 头像地址，当前版本仅预留。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
