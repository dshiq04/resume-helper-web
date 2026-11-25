package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 加密表单请求体。
 *
 * <p>前端用 RSA 公钥把表单 JSON 按块加密后放入 chunks，每块都是独立的
 * RSA/PKCS#1 v1.5 密文（Base64）。后端按顺序解密并拼回完整 JSON，
 * 再映射成对应 DTO 执行 Jakarta Validation。</p>
 */
@Data
public class EncryptedFormDTO {

    @NotEmpty(message = "请求数据不能为空")
    @Size(max = 64, message = "请求数据块过多")
    private List<@NotBlank(message = "请求数据块不能为空") String> chunks;
}
