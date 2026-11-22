package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /** 封禁原因，会展示给被封禁用户。 */
    @Size(max = 255, message = "封禁原因不能超过 255 个字符")
    private String reason;
}
