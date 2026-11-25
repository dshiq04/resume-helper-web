package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录表单加密公钥。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormKeyVO {

    /** 加密算法说明，便于前端排查。 */
    private String algorithm;
    /** X.509（SPKI）格式的 RSA 公钥 PEM。 */
    private String publicKey;
    /** 单块最大原始明文字节数（前端据此切分 JSON 字节）。 */
    private int chunkSize;
}
