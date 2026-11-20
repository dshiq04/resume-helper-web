package com.jobtrack.system.database;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jobtrack.common.exception.BusinessException;
import com.jobtrack.common.util.JsonUtils;
import com.jobtrack.system.config.AppProperties;
import com.jobtrack.system.config.DatabaseSettings;
import com.jobtrack.system.dto.DatabaseSwitchResult;
import com.jobtrack.system.dto.DatabaseTestResult;
import com.jobtrack.system.service.CacheService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 数据库连接管理。启动时按 config.json 创建连接池；
 * 设置页切换数据库时创建新连接池、初始化表结构、写入配置并热切换，
 * 无需重启服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseManager {

    private final AppProperties appProperties;
    private final DatabaseSettings databaseSettings;
    private final CacheService cacheService;

    private final DynamicDataSource dynamicDataSource = new DynamicDataSource(null);
    private volatile HikariDataSource pool;
    private volatile DatabaseSettings current;

    @Value("${jobtrack.config.path:}")
    private String configPath;

    @PostConstruct
    void initialize() {
        current = normalize(databaseSettings.copy());
        pool = createPool(current);
        dynamicDataSource.setDelegate(pool);
        log.info("数据库已连接: {} · {}", current.getType(), safeUrl(buildUrl(current)));
    }

    public DataSource dataSource() {
        return dynamicDataSource;
    }

    public synchronized DatabaseSettings currentSettings() {
        return current.copy();
    }

    public Path configPath() {
        if (configPath != null && !configPath.isBlank()) {
            return Paths.get(configPath).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir", "."), "config.json").toAbsolutePath().normalize();
    }

    public DatabaseTestResult test(DatabaseSettings input) {
        DatabaseSettings candidate = normalize(input);
        HikariDataSource probe = null;
        try {
            probe = createPool(candidate);
            try (Connection connection = probe.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1");
                }
            }
            return new DatabaseTestResult(true, "连接成功");
        } catch (Exception exception) {
            return new DatabaseTestResult(false, rootMessage(exception));
        } finally {
            if (probe != null) {
                probe.close();
            }
        }
    }

    public synchronized DatabaseSwitchResult switchTo(DatabaseSettings input) {
        DatabaseSettings candidate = normalize(input);
        HikariDataSource next = null;
        try {
            next = createPool(candidate);
            try (Connection connection = next.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
            runSchema(candidate, next);
            seedAdmin(next);

            HikariDataSource previous = pool;
            pool = next;
            dynamicDataSource.setDelegate(next);
            applyToBoundSettings(candidate);
            current = candidate;
            writeConfig(candidate);
            cacheService.bumpEpoch();

            if (previous != null) {
                CompletableFuture.delayedExecutor(15, TimeUnit.SECONDS)
                        .execute(() -> {
                            try {
                                previous.close();
                            } catch (Exception ignored) {
                                // 旧连接池关闭失败不影响新连接。
                            }
                        });
            }
            log.info("数据库已切换: {} · {}", candidate.getType(), safeUrl(buildUrl(candidate)));
            return new DatabaseSwitchResult(true,
                    "数据库已切换。当前登录会话可能失效，请重新登录。", candidate.copy());
        } catch (Exception exception) {
            if (next != null) {
                next.close();
            }
            throw BusinessException.badRequest("database_switch_failed", rootMessage(exception));
        }
    }

    private DatabaseSettings normalize(DatabaseSettings input) {
        DatabaseSettings settings = input.copy();
        String type = settings.getType() == null ? "sqlite"
                : settings.getType().trim().toLowerCase(Locale.ROOT);
        if ("postgres".equals(type)) {
            type = "postgresql";
        }
        if (!type.equals("sqlite") && !type.equals("mysql")
                && !type.equals("postgresql") && !type.equals("h2")) {
            throw BusinessException.badRequest("invalid_database_type", "不支持的数据库类型");
        }
        settings.setType(type);
        if (settings.getFile() == null || settings.getFile().isBlank()) {
            settings.setFile("jobtrack.db");
        }
        if (settings.getHost() == null || settings.getHost().isBlank()) {
            settings.setHost("127.0.0.1");
        }
        if (settings.getPort() <= 0) {
            settings.setPort(type.equals("postgresql") ? 5432 : 3306);
        }
        if (settings.getName() == null || settings.getName().isBlank()) {
            settings.setName("jobtrack");
        }
        settings.setMaxPoolSize(Math.min(30, Math.max(1, settings.getMaxPoolSize())));
        return settings;
    }

    private HikariDataSource createPool(DatabaseSettings settings) {
        String url = buildUrl(settings);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        String driver = resolveDriver(settings);
        if (driver != null && !driver.isBlank()) {
            config.setDriverClassName(driver);
        }
        if (!"sqlite".equals(settings.getType())) {
            if (settings.getUsername() != null && !settings.getUsername().isBlank()) {
                config.setUsername(settings.getUsername());
            }
            if (settings.getPassword() != null && !settings.getPassword().isBlank()) {
                config.setPassword(settings.getPassword());
            }
        }
        config.setMaximumPoolSize(settings.getMaxPoolSize());
        config.setMinimumIdle(1);
        config.setPoolName("jobtrack-" + settings.getType());
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(3_000);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    private String buildUrl(DatabaseSettings settings) {
        if (settings.getUrl() != null && !settings.getUrl().isBlank()) {
            return settings.getUrl().trim();
        }
        String type = settings.getType();
        if ("sqlite".equals(type)) {
            Path file = Paths.get(settings.getFile());
            if (!file.isAbsolute()) {
                file = appProperties.dataPath().resolve(settings.getFile());
            }
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
            } catch (Exception exception) {
                throw BusinessException.badRequest("storage_unavailable", "无法创建数据库目录");
            }
            return "jdbc:sqlite:" + file.toAbsolutePath().normalize().toString().replace('\\', '/')
                    + "?busy_timeout=5000";
        }
        if ("mysql".equals(type)) {
            String params = settings.getParams() == null || settings.getParams().isBlank()
                    ? "useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
                    : settings.getParams();
            return "jdbc:mysql://" + settings.getHost() + ":" + settings.getPort() + "/"
                    + settings.getName() + (params.isBlank() ? "" : "?" + params);
        }
        if ("postgresql".equals(type)) {
            String params = settings.getParams() == null || settings.getParams().isBlank()
                    ? "sslmode=disable" : settings.getParams();
            return "jdbc:postgresql://" + settings.getHost() + ":" + settings.getPort() + "/"
                    + settings.getName() + (params.isBlank() ? "" : "?" + params);
        }
        Path file = Paths.get(settings.getFile());
        if (!file.isAbsolute()) {
            file = appProperties.dataPath().resolve(settings.getFile());
        }
        return "jdbc:h2:file:" + file.toAbsolutePath().normalize().toString().replace('\\', '/')
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    private String resolveDriver(DatabaseSettings settings) {
        if (settings.getDriver() != null && !settings.getDriver().isBlank()) {
            return settings.getDriver().trim();
        }
        return switch (settings.getType()) {
            case "sqlite" -> "org.sqlite.JDBC";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            case "h2" -> "org.h2.Driver";
            default -> "";
        };
    }

    private void runSchema(DatabaseSettings settings, DataSource dataSource) {
        String script = switch (settings.getType()) {
            case "mysql" -> "schema-mysql.sql";
            case "postgresql" -> "schema-postgresql.sql";
            case "sqlite" -> "schema-sqlite.sql";
            default -> "schema-h2.sql";
        };
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.setSeparator(";");
        populator.addScript(new ClassPathResource(script));
        try (Connection connection = dataSource.getConnection()) {
            populator.populate(connection);
        } catch (Exception exception) {
            throw new IllegalStateException("初始化数据库表结构失败: " + rootMessage(exception), exception);
        }
    }

    /**
     * 目标库为空时创建默认工作区与 admin/123456，保证切换后可以立即登录。
     */
    private void seedAdmin(DataSource dataSource) {
        AppProperties.Admin admin = appProperties.getAdmin();
        try (Connection connection = dataSource.getConnection()) {
            long tenantId = 0;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id FROM sys_tenant WHERE code = ?")) {
                query.setString(1, "default");
                try (ResultSet result = query.executeQuery()) {
                    if (result.next()) {
                        tenantId = result.getLong(1);
                    }
                }
            }
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            if (tenantId == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO sys_tenant (code, name, created_at) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, "default");
                    insert.setString(2, admin.getTenantName());
                    insert.setTimestamp(3, now);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (keys.next()) {
                            tenantId = keys.getLong(1);
                        }
                    }
                }
            }
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM sys_user");
                 ResultSet result = count.executeQuery()) {
                if (result.next() && result.getInt(1) > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO sys_user (tenant_id, username, password_hash, display_name, role, enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setLong(1, tenantId);
                insert.setString(2, admin.getUsername());
                insert.setString(3, new BCryptPasswordEncoder().encode(admin.getPassword()));
                insert.setString(4, admin.getDisplayName());
                insert.setString(5, "ADMIN");
                insert.setBoolean(6, true);
                insert.setTimestamp(7, now);
                insert.setTimestamp(8, now);
                insert.executeUpdate();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("初始化默认账号失败: " + rootMessage(exception), exception);
        }
    }

    private void applyToBoundSettings(DatabaseSettings source) {
        databaseSettings.setType(source.getType());
        databaseSettings.setFile(source.getFile());
        databaseSettings.setHost(source.getHost());
        databaseSettings.setPort(source.getPort());
        databaseSettings.setName(source.getName());
        databaseSettings.setUsername(source.getUsername());
        databaseSettings.setPassword(source.getPassword());
        databaseSettings.setParams(source.getParams());
        databaseSettings.setMaxPoolSize(source.getMaxPoolSize());
        databaseSettings.setUrl(source.getUrl());
        databaseSettings.setDriver(source.getDriver());
    }

    private void writeConfig(DatabaseSettings settings) {
        Path path = configPath();
        try {
            Map<String, Object> root = JsonUtils.mapper().readValue(
                    Files.readString(path), new TypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> database = new LinkedHashMap<>();
            database.put("type", settings.getType());
            database.put("file", settings.getFile());
            database.put("host", settings.getHost());
            database.put("port", settings.getPort());
            database.put("name", settings.getName());
            database.put("username", settings.getUsername());
            database.put("password", settings.getPassword());
            database.put("params", settings.getParams());
            database.put("maxPoolSize", settings.getMaxPoolSize());
            if (settings.getUrl() != null && !settings.getUrl().isBlank()) {
                database.put("url", settings.getUrl());
            }
            if (settings.getDriver() != null && !settings.getDriver().isBlank()) {
                database.put("driver", settings.getDriver());
            }
            root.put("database", database);
            JsonUtils.mapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
        } catch (Exception exception) {
            log.warn("数据库已切换，但写回 config.json 失败: {}", exception.getMessage());
        }
    }

    private String safeUrl(String url) {
        return url.replaceAll("password=[^&;]*", "password=***");
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
