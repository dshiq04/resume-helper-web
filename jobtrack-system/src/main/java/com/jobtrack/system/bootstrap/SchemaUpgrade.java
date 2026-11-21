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

/**
 * 轻量结构升级：为已存在的表补充新增列。
 *
 * <p>建表脚本使用 CREATE TABLE IF NOT EXISTS，无法给旧表加字段；
 * 这里启动时检查并补齐，避免用户手动执行 SQL。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaUpgrade implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("access_token", "resources", "VARCHAR(200)");
        ensureColumn("access_token_log", "resources", "VARCHAR(200)");
    }

    private void ensureColumn(String table, String column, String type) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            if (hasColumn(meta, connection, table, column)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                log.info("已补充字段: {}.{}", table, column);
            }
        } catch (Exception exception) {
            log.warn("检查字段 {}.{} 失败: {}", table, column, exception.getMessage());
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
