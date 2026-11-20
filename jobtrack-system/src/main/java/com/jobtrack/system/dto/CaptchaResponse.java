package com.jobtrack.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse {

    private String captchaId;
    /** data:image/png;base64,... */
    private String image;
    private int expiresIn;
}
