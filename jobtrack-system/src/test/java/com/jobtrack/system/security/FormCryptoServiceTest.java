package com.jobtrack.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.pojo.dto.EncryptedFormDTO;
import com.jobtrack.system.pojo.dto.LoginDTO;
import com.jobtrack.system.pojo.dto.RegisterDTO;
import com.jobtrack.system.pojo.vo.FormKeyVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.encrypt.RsaRawEncryptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 模拟前端 JSEncrypt 的分块 RSA 加密，验证后端能够解密并继续执行参数校验。
 */
class FormCryptoServiceTest {

    private final ObjectMapper objectMapper = JsonUtils.mapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @TempDir
    Path tempDir;

    private FormCryptoService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir.toString());
        service = new FormCryptoService(properties, validator);
        service.init();
    }

    @Test
    void decryptsChunkedLoginForm() throws Exception {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("username", "alice");
        form.put("password", "secret123");
        form.put("captchaId", "captcha-1");
        form.put("captchaCode", "8");

        LoginDTO login = service.decrypt(encrypt(form), LoginDTO.class);

        assertEquals("alice", login.getUsername());
        assertEquals("secret123", login.getPassword());
        assertEquals("captcha-1", login.getCaptchaId());
    }

    @Test
    void validatesDecryptedRegisterForm() throws Exception {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("username", "alice");
        form.put("password", "secret123");
        form.put("confirmPassword", "secret124");
        form.put("displayName", "Alice");
        form.put("tenantName", "工作区");
        form.put("agreementAccepted", true);

        assertThrows(RuntimeException.class,
                () -> service.decrypt(encrypt(form), RegisterDTO.class));
    }

    private EncryptedFormDTO encrypt(Map<String, Object> form) throws Exception {
        FormKeyVO key = service.publicKey();
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(decodePem(key.getPublicKey())));
        RsaRawEncryptor encryptor = new RsaRawEncryptor(publicKey);

        byte[] json = objectMapper.writeValueAsBytes(form);
        List<String> chunks = new ArrayList<>();
        int size = key.getChunkSize();
        for (int offset = 0; offset < json.length; offset += size) {
            byte[] block = java.util.Arrays.copyOfRange(json, offset,
                    Math.min(offset + size, json.length));
            // 与前端一致：先 Base64 原始字节，再交给 JSEncrypt 做 RSA 加密。
            chunks.add(encryptor.encrypt(Base64.getEncoder().encodeToString(block)));
        }
        EncryptedFormDTO request = new EncryptedFormDTO();
        request.setChunks(chunks);
        return request;
    }

    private byte[] decodePem(String pem) {
        String content = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(content.getBytes(StandardCharsets.UTF_8));
    }
}
