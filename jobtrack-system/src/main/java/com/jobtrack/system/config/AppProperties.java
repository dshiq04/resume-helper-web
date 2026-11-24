package com.jobtrack.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * application-{profile}.yml 中 app 节点的绑定对象。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name = "履途 ResumeTrail";
    private String version = "1.2.1";
    private String dataDir = "./data";
    private String logDir = "./logs";
    private boolean seedDemo = false;
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Admin admin = new Admin();
    private Rabbit rabbit = new Rabbit();

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
}
