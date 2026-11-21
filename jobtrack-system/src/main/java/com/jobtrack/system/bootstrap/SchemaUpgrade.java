package com.jobtrack.system.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 轻量结构升级：为已存在的表补充新增列。
 *
 * <p>建表脚本使用 CREATE TABLE IF NOT EXISTS，无法给旧表加字段；
 * 这里启动时检查并补齐，避免用户手动执行 SQL。DDL 会按数据库类型生成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaUpgrade implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            boolean mysql = product.contains("mysql");
            Map<String, String> tokenColumns = new LinkedHashMap<>();
            tokenColumns.put("resources", "VARCHAR(200)");
            tokenColumns.put("enabled", mysql
                    ? "TINYINT(1) NOT NULL DEFAULT 1" : "BOOLEAN NOT NULL DEFAULT TRUE");
            tokenColumns.put("deleted", mysql
                    ? "TINYINT(1) NOT NULL DEFAULT 0" : "BOOLEAN NOT NULL DEFAULT FALSE");
            tokenColumns.forEach((column, type) -> ensureColumn(connection, "access_token", column, type));
            ensureColumn(connection, "access_token_log", "resources", "VARCHAR(200)");
        } catch (Exception exception) {
            log.warn("结构升级检查失败: {}", exception.getMessage());
        }
    }

    private void ensureColumn(Connection connection, String table, String column, String type) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            if (hasColumn(meta, connection, table, column)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                log.info("已补充字段: {}.{}", table, column);
            }
        } catch (Exception exception) {
            log.warn("补充字段 {}.{} 失败: {}", table, column, exception.getMessage());
        }
    }

    private boolean hasColumn(DatabaseMetaData meta, Connection connection, String table, String column)
            throws Exception {
        try (ResultSet columns = meta.getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = meta.getColumns(connection.getCatalog(), null, table.toUpperCase(), column)) {
            return columns.next();
        }
    }
}
