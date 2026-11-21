package com.jobtrack.system.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户同意协议的记录，每个用户保留一条。
 */
@Data
@TableName("user_agreement")
public class UserAgreement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private LocalDateTime acceptedAt;
}
