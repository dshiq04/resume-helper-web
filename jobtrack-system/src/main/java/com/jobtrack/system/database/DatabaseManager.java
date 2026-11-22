package com.jobtrack.system.database;

import com.jobtrack.system.config.DatabaseSettings;
import com.jobtrack.system.pojo.vo.DatabaseTestResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库连接信息读取与连通性检测。
 *
 * <p>连接池由 Spring Boot 依据 application-{profile}.yml 中的
 * spring.datasource 自动创建，本类只负责把当前配置解析出来展示，
 * 不再承担运行时切换数据库的职责。</p>
 */
@Component
@RequiredArgsConstructor
public class DatabaseManager {

    private static final Pattern JDBC_URL = Pattern.compile(
            "^jdbc:(mysql|postgresql)://([^:/]+):(\\d+)/([^?;]+)(?:\\?([^;]*))?.*$");

    private final Environment environment;
    private final DataSource dataSource;

    /**
     * 读取当前生效的数据库配置，密码不返回给前端。
     */
    public DatabaseSettings currentSettings() {
        String url = environment.getProperty("spring.datasource.url", "");
        DatabaseSettings settings = new DatabaseSettings();
        settings.setUrl(url);
        settings.setUsername(environment.getProperty("spring.datasource.username", ""));
        settings.setDriver(environment.getProperty("spring.datasource.driver-class-name", ""));
        settings.setMaxPoolSize(environment.getProperty(
                "spring.datasource.hikari.maximum-pool-size", Integer.class, 10));
        settings.setType(resolveType(url));
        settings.setPort("postgresql".equals(settings.getType()) ? 5432 : 3306);

        Matcher matcher = JDBC_URL.matcher(url);
        if (matcher.matches()) {
            settings.setType("postgresql".equals(matcher.group(1)) ? "postgresql" : "mysql");
            settings.setHost(matcher.group(2));
            try {
                settings.setPort(Integer.parseInt(matcher.group(3)));
            } catch (NumberFormatException ignored) {
                // 保留默认端口。
            }
            settings.setName(matcher.group(4));
            if (matcher.group(5) != null) {
                settings.setParams(matcher.group(5));
            }
        }
        // 仅保留“是否已配置”的信息，避免把密码回传浏览器。
        settings.setPassword(environment.getProperty("spring.datasource.password", "").isBlank() ? "" : "******");
        return settings;
    }

    /**
     * 使用当前连接池执行一次 SELECT 1，用于设置页的“测试连接”。
     */
    public DatabaseTestResultVO testCurrent() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return new DatabaseTestResultVO(true, "连接成功");
        } catch (Exception exception) {
            return new DatabaseTestResultVO(false, rootMessage(exception));
        }
    }

    private String resolveType(String url) {
        String normalized = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:postgresql:")) {
            return "postgresql";
        }
        return "mysql";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
