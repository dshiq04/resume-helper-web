package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号与安全 · 联系方式保存请求。手机号与邮箱至少保留一项。
 */
@Data
public class ContactUpdateDTO {

    /** 国家区号，如 +86；邮箱方式时可不传。 */
    private String countryCode;

    @Size(max = 24)
    private String phone;

    @Size(max = 128)
    private String email;
}
