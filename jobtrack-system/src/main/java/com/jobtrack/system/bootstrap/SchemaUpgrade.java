package com.jobtrack.system.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
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
@Order(0)
@RequiredArgsConstructor
public class SchemaUpgrade implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            boolean mysql = product.contains("mysql");
            createPermissionTables(connection, mysql);
            Map<String, String> tokenColumns = new LinkedHashMap<>();
            tokenColumns.put("resources", "VARCHAR(200)");
            tokenColumns.put("enabled", mysql
                    ? "TINYINT(1) NOT NULL DEFAULT 1" : "BOOLEAN NOT NULL DEFAULT TRUE");
            tokenColumns.put("deleted", mysql
                    ? "TINYINT(1) NOT NULL DEFAULT 0" : "BOOLEAN NOT NULL DEFAULT FALSE");
            tokenColumns.forEach((column, type) -> ensureColumn(connection, "access_token", column, type));
            ensureColumn(connection, "access_token_log", "resources", "VARCHAR(200)");
            // 用户状态：NORMAL 正常 / CANCELLED 已注销 / BANNED 已封禁，默认正常。
            ensureColumn(connection, "sys_user", "status",
                    mysql ? "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'"
                          : "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'");
            ensureColumn(connection, "sys_user", "ban_type", "VARCHAR(16) NULL");
            ensureColumn(connection, "sys_user", "ban_until",
                    mysql ? "DATETIME NULL" : "TIMESTAMP NULL");
            ensureColumn(connection, "sys_user", "banned_at",
                    mysql ? "DATETIME NULL" : "TIMESTAMP NULL");
            // 用户名唯一索引 → 普通索引（唯一性改为逻辑判断，注销后可重新注册）。
            demoteUsernameUniqueIndex(connection, mysql);
        } catch (Exception exception) {
            log.warn("结构升级检查失败: {}", exception.getMessage());
        }
    }

    /** 权限分表：为旧库补充 sys_permission / sys_user_permission。 */
    private void createPermissionTables(Connection connection, boolean mysql) {
        String sysPermission;
        String userPermission;
        if (mysql) {
            sysPermission = "CREATE TABLE IF NOT EXISTS sys_permission ("
                    + "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "code VARCHAR(32) NOT NULL,"
                    + "name VARCHAR(64) NOT NULL,"
                    + "description VARCHAR(255) NULL,"
                    + "created_at DATETIME NULL,"
                    + "PRIMARY KEY (id),"
                    + "UNIQUE KEY uk_sys_permission_code (code)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
            userPermission = "CREATE TABLE IF NOT EXISTS sys_user_permission ("
                    + "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "user_id BIGINT UNSIGNED NOT NULL,"
                    + "permission_id BIGINT UNSIGNED NOT NULL,"
                    + "created_at DATETIME NULL,"
                    + "PRIMARY KEY (id),"
                    + "UNIQUE KEY uk_sys_user_permission (user_id, permission_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        } else {
            sysPermission = "CREATE TABLE IF NOT EXISTS sys_permission ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "code VARCHAR(32) NOT NULL,"
                    + "name VARCHAR(64) NOT NULL,"
                    + "description VARCHAR(255),"
                    + "created_at TIMESTAMP,"
                    + "CONSTRAINT uk_sys_permission_code UNIQUE (code))";
            userPermission = "CREATE TABLE IF NOT EXISTS sys_user_permission ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "user_id BIGINT NOT NULL,"
                    + "permission_id BIGINT NOT NULL,"
                    + "created_at TIMESTAMP,"
                    + "CONSTRAINT uk_sys_user_permission UNIQUE (user_id, permission_id))";
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sysPermission);
            statement.execute(userPermission);
            // MySQL 的 SELECT ... WHERE 需要 FROM DUAL，PostgreSQL 则不能带。
            String fromDual = mysql ? " FROM DUAL" : "";
            // 权限字典：内置普通用户 / 管理员。
            statement.execute("INSERT INTO sys_permission (code, name, description) "
                    + "SELECT 'USER', '普通用户', '默认权限'" + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'USER')");
            statement.execute("INSERT INTO sys_permission (code, name, description) "
                    + "SELECT 'ADMIN', '管理员', '管理员权限'" + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'ADMIN')");
            // 独立的管理员控制台权限：只有持有它（或 ADMIN）才能访问 /api/admin/**。
            statement.execute("INSERT INTO sys_permission (code, name, description) "
                    + "SELECT 'ADMIN_CONSOLE', '管理员控制台', '访问管理员菜单与 /api/admin/** 接口'"
                    + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'ADMIN_CONSOLE')");
            // 历史数据迁移：把 sys_user.role 复制到用户权限对应表（只补缺，不重复）。
            statement.execute("INSERT INTO sys_user_permission (user_id, permission_id) "
                    + "SELECT u.id, p.id FROM sys_user u "
                    + "JOIN sys_permission p ON p.code = u.role "
                    + "WHERE NOT EXISTS (SELECT 1 FROM sys_user_permission up "
                    + "WHERE up.user_id = u.id AND up.permission_id = p.id)");
            // 已有 admin 角色自动获得独立的管理员控制台权限。
            statement.execute("INSERT INTO sys_user_permission (user_id, permission_id) "
                    + "SELECT u.id, p.id FROM sys_user u "
                    + "JOIN sys_permission p ON p.code = 'ADMIN_CONSOLE' "
                    + "WHERE UPPER(u.role) = 'ADMIN' AND NOT EXISTS "
                    + "(SELECT 1 FROM sys_user_permission up "
                    + "WHERE up.user_id = u.id AND up.permission_id = p.id)");
            log.info("权限表检查完成（sys_permission / sys_user_permission）");
        } catch (Exception exception) {
            log.warn("权限表创建或迁移失败: {}", exception.getMessage());
        }
    }

    /** 用户名唯一索引降级为普通索引：已注销账号的用户名允许被重新注册。 */
    private void demoteUsernameUniqueIndex(Connection connection, boolean mysql) {
        try {
            if (!hasIndex(connection, "sys_user", "uk_sys_user_username")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                if (mysql) {
                    statement.execute("ALTER TABLE sys_user DROP INDEX uk_sys_user_username");
                } else {
                    statement.execute("ALTER TABLE sys_user DROP CONSTRAINT uk_sys_user_username");
                }
            }
            log.info("已移除 sys_user.username 唯一索引（唯一性改为逻辑判断）");
        } catch (Exception exception) {
            log.warn("移除用户名唯一索引失败: {}", exception.getMessage());
        }
        try {
            if (hasIndex(connection, "sys_user", "idx_sys_user_username")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(mysql
                        ? "ALTER TABLE sys_user ADD INDEX idx_sys_user_username (username)"
                        : "CREATE INDEX idx_sys_user_username ON sys_user (username)");
            }
            log.info("已创建普通索引 idx_sys_user_username");
        } catch (Exception exception) {
            log.warn("创建用户名普通索引失败: {}", exception.getMessage());
        }
    }

    private boolean hasIndex(Connection connection, String table, String indexName) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet indexes = meta.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null && name.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        try (ResultSet indexes = meta.getIndexInfo(connection.getCatalog(), null, table.toUpperCase(), false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null && name.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
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
