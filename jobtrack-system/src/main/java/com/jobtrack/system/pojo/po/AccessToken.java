package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 浏览器扩展 / 第三方调用使用的授权码。
 *
 * <p>scope 取值：read（只读）、write（只写）、readwrite（读写）。
 * expiresOn 为空表示长期有效。</p>
 */
@Data
@TableName("access_token")
public class AccessToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String name;

    private String code;

    private String scope;

    /** 数据范围，逗号分隔：applications,resumes,statistics,profile */
    private String resources;

    private Boolean enabled;

    @TableLogic
    private Integer deleted;

    private LocalDate expiresOn;

    private LocalDateTime lastUsedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
