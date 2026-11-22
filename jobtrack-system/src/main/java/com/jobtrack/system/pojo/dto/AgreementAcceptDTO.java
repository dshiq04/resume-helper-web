package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录后补签用户协议。语言为签署时界面语言，地点与时区用于导出存档。
 */
@Data
public class AgreementAcceptDTO {

    @Pattern(regexp = "^$|zh-CN|zh-TW|en|ja|ko|ru", message = "不支持的签署语言")
    private String language;

    @Size(max = 255)
    private String location;

    @Size(max = 64)
    private String timezone;

    @Size(max = 35)
    private String locale;
}
