package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员修改用户状态：正常 / 注销 / 封禁。
 */
@Data
public class AdminStatusDTO {

    /** NORMAL / CANCELLED / BANNED */
    @NotBlank(message = "请选择用户状态")
    private String status;

    /** 封禁类型：PERMANENT / TEMPORARY；status=BANNED 时必填。 */
    private String banType;

    /** 暂时封禁的到期时间；永久封禁时为空。 */
    private LocalDateTime banUntil;
}
