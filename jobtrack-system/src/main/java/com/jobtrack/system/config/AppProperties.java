package com.jobtrack.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * application-{profile}.yml 中 app 节点的绑定对象。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String dataDir = "./data";
    private String logDir = "./logs";
    private boolean seedDemo = false;
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Admin admin = new Admin();
    private Rabbit rabbit = new Rabbit();
    private Cache cache = new Cache();
    private FormCrypto formCrypto = new FormCrypto();

    public Path dataPath() {
        return Paths.get(dataDir).toAbsolutePath().normalize();
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
    }

    @Data
    public static class Jwt {
        private String secret = "jobtrack-local-dev-secret-change-me-0123456789abcdef";
        private int ttlHours = 72;
    }

    @Data
    public static class Storage {
        private String secret = "jobtrack-file-encryption-secret-change-me-0123456789";
        private int maxFileSizeMb = 50;
        /** s3 / local */
        private String type = "s3";
        private String endpoint = "http://127.0.0.1:9000";
        private String region = "us-east-1";
        private String bucket = "resume-helper";
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = true;
        /** 自动为存储桶设置匿名只读策略，便于头像直接通过 URL 展示。 */
        private boolean publicRead = true;
        /** 对外访问根路径，例如 http://127.0.0.1:9000/resume-helper */
        private String publicBaseUrl = "http://127.0.0.1:9000/resume-helper";
    }

    @Data
    public static class Admin {
        private String username = "admin";
        private String password = "123456";
        private String displayName = "管理员";
        private String tenantName = "默认工作区";
    }

    @Data
    public static class Rabbit {
        private boolean enabled = true;
    }

    /** Redis 业务缓存配置。 */
    @Data
    public static class Cache {
        /** 缓存有效期，写操作仍会立即清理，TTL 仅作为兜底。 */
        private Duration ttl = Duration.ofHours(24);
    }

    /**
     * 登录 / 注册 / 修改密码等表单的传输加密配置。
     *
     * <p>前端使用 JSEncrypt 按 RSA 公钥分块加密表单 JSON，后端使用
     * Spring Security Crypto 的 RsaRawEncryptor 解密后再做参数校验。</p>
     */
    @Data
    public static class FormCrypto {
        /** RSA 密钥长度，默认 2048 位。 */
        private int keySize = 2048;
        /** 可选的 PKCS#8 私钥 PEM 内容，配置后不再生成密钥文件。 */
        private String privateKey = "";
        /** 私钥文件路径；为空时使用 {data-dir}/keys/form-rsa-private.pem。 */
        private String keyFile = "";
    }
}
