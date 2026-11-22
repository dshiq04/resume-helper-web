package com.jobtrack.system.pojo.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授权码使用日志。每次调用 /api/tokens/validate 都会写入一条，
 * 包括校验失败（不存在 / 过期 / 账号停用）的情况。
 */
@Data
@TableName("access_token_log")
public class AccessTokenLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Long tokenId;

    private String tokenName;

    /** 授权码前 10 位，避免把完整密钥写入日志。 */
    private String codePrefix;

    private String scope;

    /** 数据范围，逗号分隔：applications,resumes,statistics,profile */
    private String resources;

    /** valid / invalid / expired / disabled */
    private String result;

    private String ip;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
