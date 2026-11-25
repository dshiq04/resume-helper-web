package com.jobtrack.system.security;

import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.pojo.dto.EncryptedFormDTO;
import com.jobtrack.system.pojo.vo.FormKeyVO;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.RsaAlgorithm;
import org.springframework.security.crypto.encrypt.RsaRawEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 登录、注册、修改密码等表单的传输加密。
 *
 * <p>前端 JSEncrypt 用公钥按块加密表单 JSON，每块是 RSA/PKCS#1 v1.5 密文；
 * 后端使用 Spring Security Crypto 的 {@link RsaRawEncryptor} 解密，拼回 JSON
 * 后再映射成 DTO 并执行参数校验。这样密码等敏感字段不会以明文出现在请求体、
 * 浏览器开发者工具或网关日志中。</p>
 */
@Slf4j
@Service
public class FormCryptoService {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String PRIVATE_KEY_HEADER = "PRIVATE KEY";
    /** PKCS#1 v1.5 填充开销。 */
    private static final int PKCS1_PADDING_BYTES = 11;

    private final AppProperties appProperties;
    private final Validator validator;

    private RsaRawEncryptor encryptor;
    private String publicKeyPem;
    private String algorithm;
    private int chunkSize;

    public FormCryptoService(AppProperties appProperties, Validator validator) {
        this.appProperties = appProperties;
        this.validator = validator;
    }

    @PostConstruct
    void init() {
        AppProperties.FormCrypto settings = appProperties.getFormCrypto();
        try {
            KeyPair keyPair = loadOrCreateKeyPair(settings);
            this.encryptor = new RsaRawEncryptor(keyPair, RsaAlgorithm.DEFAULT);
            this.publicKeyPem = toPublicKeyPem(keyPair.getPublic());

            int modulusBytes = RsaRawEncryptor.getByteLength((RSAKey) keyPair.getPublic());
            int maxPlaintext = modulusBytes - PKCS1_PADDING_BYTES;
            // 前端会先对原始字节做 Base64，再交给 JSEncrypt，Base64 会膨胀 4/3。
            // 这里预留 8 字节余量，保证每一块都能被 JSEncrypt 一次加密。
            this.chunkSize = Math.max(32, ((maxPlaintext - 8) / 4) * 3);
            this.algorithm = "RSA-" + (modulusBytes * 8) + "/PKCS#1 v1.5";
            log.info("登录表单 RSA 加密已启用: keySize={}, chunkSize={}B", settings.getKeySize(),
                    chunkSize);
        } catch (Exception exception) {
            throw new IllegalStateException("初始化登录表单加密密钥失败", exception);
        }
    }

    /** 前端初始化登录页时获取公钥。 */
    public FormKeyVO publicKey() {
        return new FormKeyVO(algorithm, publicKeyPem, chunkSize);
    }

    /**
     * 解密表单并按 DTO 上的注解做参数校验。
     *
     * @throws BusinessException 数据损坏、密钥不匹配或参数校验失败
     */
    public <T> T decrypt(EncryptedFormDTO form, Class<T> type) {
        if (form == null || form.getChunks() == null || form.getChunks().isEmpty()) {
            throw BusinessException.badRequest("encrypted_form_required", "请提交加密后的表单数据");
        }
        if (encryptor == null) {
            throw BusinessException.internal("form_crypto_unavailable", "表单加密服务暂不可用");
        }
        try {
            ByteArrayOutputStream plain = new ByteArrayOutputStream();
            for (String chunk : form.getChunks()) {
                byte[] cipher = Base64.getDecoder().decode(chunk);
                byte[] encoded = encryptor.decrypt(cipher);
                // 前端每块加密的是“原始 JSON 字节的 Base64”，这里再解一层。
                plain.write(Base64.getDecoder().decode(encoded));
            }
            String json = plain.toString(StandardCharsets.UTF_8);
            T value = JsonUtils.mapper().readValue(json, type);
            validate(value);
            return value;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("表单解密失败: {}", exception.getMessage());
            throw BusinessException.badRequest("invalid_encrypted_form",
                    "请求数据解密失败，请刷新页面后重试");
        }
    }

    private void validate(Object value) {
        Set<ConstraintViolation<Object>> violations = validator.validate(value);
        if (violations.isEmpty()) {
            return;
        }
        List<String> messages = violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .toList();
        throw BusinessException.badRequest("invalid_request", messages.get(0));
    }

    private KeyPair loadOrCreateKeyPair(AppProperties.FormCrypto settings) throws Exception {
        if (StringUtils.hasText(settings.getPrivateKey())) {
            return readKeyPair(settings.getPrivateKey(), "配置项 app.form-crypto.private-key");
        }
        Path path = keyFile(settings);
        if (Files.exists(path)) {
            return readKeyPair(Files.readString(path, StandardCharsets.UTF_8), path.toString());
        }
        KeyPair keyPair = generateKeyPair(settings.getKeySize());
        Files.createDirectories(path.getParent());
        Files.writeString(path, toPrivateKeyPem(keyPair.getPrivate()), StandardCharsets.UTF_8);
        restrictPermissions(path);
        log.info("已生成登录表单 RSA 密钥: {}", path.toAbsolutePath());
        return keyPair;
    }

    /** 密钥文件只允许当前用户读写；Windows 等不支持 POSIX 权限的平台上忽略。 */
    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            // 非 POSIX 文件系统或权限不足时不影响启动。
        }
    }

    private Path keyFile(AppProperties.FormCrypto settings) {
        if (StringUtils.hasText(settings.getKeyFile())) {
            return Path.of(settings.getKeyFile().trim()).toAbsolutePath().normalize();
        }
        return appProperties.dataPath().resolve("keys").resolve("form-rsa-private.pem");
    }

    private KeyPair generateKeyPair(int keySize) throws Exception {
        int safeSize = keySize < 2048 ? 2048 : keySize;
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        generator.initialize(safeSize, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * 支持 PKCS#8 PEM（-----BEGIN PRIVATE KEY-----）。公钥由私钥的模数与指数推导，
     * 因此只需要维护一份私钥文件。
     */
    private KeyPair readKeyPair(String pem, String source) throws Exception {
        byte[] content = decodePem(pem);
        KeyFactory factory = KeyFactory.getInstance(KEY_ALGORITHM);
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(content));
        if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivate)) {
            throw new IllegalArgumentException(source + " 不是可用的 RSA 私钥");
        }
        PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(
                rsaPrivate.getModulus(), rsaPrivate.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }

    private byte[] decodePem(String pem) {
        if (!StringUtils.hasText(pem)) {
            throw new IllegalArgumentException("RSA 私钥内容为空");
        }
        String content = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }

    private String toPublicKeyPem(PublicKey publicKey) {
        return pem("PUBLIC KEY", publicKey.getEncoded());
    }

    private String toPrivateKeyPem(PrivateKey privateKey) {
        return pem(PRIVATE_KEY_HEADER, privateKey.getEncoded());
    }

    private String pem(String type, byte[] content) {
        String encoded = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(content);
        return "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----";
    }
}
