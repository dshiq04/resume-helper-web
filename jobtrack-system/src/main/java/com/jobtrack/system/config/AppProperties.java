package com.jobtrack.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * config.json 中 app 节点的绑定对象。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name = "投递手账";
    private String version = "1.0.0";
    private String dataDir = "./data";
    private String logDir = "./logs";
    private boolean seedDemo = false;
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Storage storage = new Storage();
    private Admin admin = new Admin();

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
    }

    @Data
    public static class Admin {
        private String username = "admin";
        private String password = "123456";
        private String displayName = "管理员";
        private String tenantName = "默认工作区";
    }
}
