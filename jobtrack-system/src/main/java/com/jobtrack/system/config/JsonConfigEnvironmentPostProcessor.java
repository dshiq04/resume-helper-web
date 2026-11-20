package com.jobtrack.system.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jobtrack.common.util.JsonUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从项目根目录的 config.json 读取服务端配置。
 *
 * <p>Spring Boot 原生不支持 JSON 配置，这里在环境准备阶段把 JSON 拍平成
 * 点分属性并放到最高优先级，同时把简洁的 database 配置翻译成
 * spring.datasource.*。这样 application.yaml 只需要保留框架级默认值，
 * 端口、数据库、日志、JWT、存储等运行配置全部在根目录 config.json 中维护。</p>
 *
 * <p>查找顺序：-Djobtrack.config 指定的文件 → 环境变量 JOBTRACK_CONFIG →
 * 当前目录 config.json → 程序目录上一级 config.json → 当前目录 config/config.json。</p>
 */
public class JsonConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String CONFIG_FILE_PROPERTY = "jobtrack.config";
    public static final String CONFIG_FILE_ENV = "JOBTRACK_CONFIG";
    private static final String SOURCE_NAME = "jobtrackConfig";

    @Override
    public int getOrder() {
        // 在 Spring Boot 处理 application.yaml 之前放入属性源。
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path configPath = locateConfig();
        if (configPath == null) {
            System.out.println("[jobtrack] 未找到 config.json，使用 application.yaml 中的默认配置。");
            return;
        }

        Map<String, Object> raw;
        try {
            raw = JsonUtils.mapper().readValue(
                    Files.readString(configPath),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取配置文件: " + configPath, exception);
        }

        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", raw, flat);
        Map<String, Object> derived = deriveServerProperties(flat, configPath);
        flat.putAll(derived);

        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, flat));
        System.out.println("[jobtrack] 已加载服务端配置: " + configPath.toAbsolutePath().normalize());
    }

    private Path locateConfig() {
        String explicit = System.getProperty(CONFIG_FILE_PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv(CONFIG_FILE_ENV);
        }
        if (explicit != null && !explicit.isBlank()) {
            Path path = Paths.get(explicit);
            if (Files.isRegularFile(path)) {
                return path;
            }
            throw new IllegalStateException("指定的配置文件不存在: " + path.toAbsolutePath());
        }

        Path workingDirectory = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                workingDirectory.resolve("config.json"),
                workingDirectory.resolveSibling("config.json"),
                workingDirectory.resolve("config").resolve("config.json"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, target);
            } else if (value instanceof List<?> list) {
                // 标量数组按逗号拼成字符串，供 Spring 的 String 列表属性使用。
                StringBuilder joined = new StringBuilder();
                for (Object item : list) {
                    if (joined.length() > 0) {
                        joined.append(',');
                    }
                    joined.append(item);
                }
                target.put(key, joined.toString());
            } else if (value != null) {
                target.put(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deriveServerProperties(Map<String, Object> flat, Path configPath) {
        Map<String, Object> derived = new LinkedHashMap<>();

        Path dataDir = resolvePath(configPath, stringValue(flat.get("app.dataDir"), "./data"));
        Path logDir = resolvePath(configPath, stringValue(flat.get("app.logDir"), "./logs"));
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(logDir);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建数据目录: " + dataDir, exception);
        }
        // 统一使用绝对路径，保证从任意工作目录启动都写入同一份数据。
        derived.put("app.dataDir", dataDir.toString());
        derived.put("app.logDir", logDir.toString());
        derived.put("spring.servlet.multipart.max-file-size", "50MB");
        derived.put("spring.servlet.multipart.max-request-size", "55MB");

        Map<String, Object> database = nested(flat, "database");
        String driverType = stringValue(database.get("type"), "h2").toLowerCase(Locale.ROOT);
        String url = stringValue(database.get("url"), "");
        String username = stringValue(database.get("username"), "");
        String password = stringValue(database.get("password"), "");
        String driverClass;

        if (!url.isBlank()) {
            driverClass = stringValue(database.get("driver"), "");
        } else if ("mysql".equals(driverType)) {
            String host = stringValue(database.get("host"), "127.0.0.1");
            int port = intValue(database.get("port"), 3306);
            String name = stringValue(database.get("name"), "jobtrack");
            String params = stringValue(database.get("params"),
                    "useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
            url = "jdbc:mysql://" + host + ":" + port + "/" + name
                    + (params.isBlank() ? "" : "?" + params);
            driverClass = "com.mysql.cj.jdbc.Driver";
        } else {
            String fileName = stringValue(database.get("file"), "jobtrack");
            Path databaseFile = Paths.get(fileName).isAbsolute()
                    ? Paths.get(fileName)
                    : dataDir.resolve(fileName);
            url = "jdbc:h2:file:" + databaseFile.toString().replace('\\', '/')
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
            driverClass = "org.h2.Driver";
            if (username.isBlank()) {
                username = "sa";
            }
            password = "";
        }

        derived.put("spring.datasource.url", url);
        derived.put("spring.datasource.username", username);
        derived.put("spring.datasource.password", password);
        if (driverClass != null && !driverClass.isBlank()) {
            derived.put("spring.datasource.driver-class-name", driverClass);
        }
        derived.put("spring.datasource.hikari.maximum-pool-size",
                intValue(database.get("maxPoolSize"), 10));

        if (!flat.containsKey("logging.file.name")) {
            derived.put("logging.file.name", logDir.resolve("jobtrack.log").toString());
        }
        if (!flat.containsKey("logging.level.root")) {
            derived.put("logging.level.root", stringValue(flat.get("logging.level"), "INFO"));
        }
        return derived;
    }

    private Path resolvePath(Path configPath, String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path base = configPath.toAbsolutePath().getParent();
        return base.resolve(path).normalize();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> flat, String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        String start = prefix + ".";
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            if (entry.getKey().startsWith(start)) {
                result.put(entry.getKey().substring(start.length()), entry.getValue());
            }
        }
        return result;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
