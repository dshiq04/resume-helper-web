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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
            boolean banReady = createBanTable(connection, mysql);
            boolean banMigrated = banReady && migrateLegacyBans(connection, mysql);
            createOperationLogTable(connection, mysql);
            Map<String, String> tokenColumns = new LinkedHashMap<>();
            tokenColumns.put("resources", "VARCHAR(200)");
            tokenColumns.put("enabled", mysql
                    ? "TINYINT(1) NOT NULL DEFAULT 1" : "BOOLEAN NOT NULL DEFAULT TRUE");
            tokenColumns.put("deleted", mysql
                    ? "TINYINT(1) NOT NULL DEFAULT 0" : "BOOLEAN NOT NULL DEFAULT FALSE");
            tokenColumns.forEach((column, type) -> ensureColumn(connection, "access_token", column, type));
            ensureColumn(connection, "access_token_log", "resources", "VARCHAR(200)");
            // 用户状态：NORMAL 正常 / CANCELLED 已注销；封禁状态由 sys_user_ban 判断。
            ensureColumn(connection, "sys_user", "status",
                    mysql ? "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'"
                          : "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'");
            // 旧版本把封禁信息放在 sys_user 上，迁移到封禁记录表后删除这 3 个列。
            // 只有封禁表可用且迁移成功才删除，避免迁移失败时丢失封禁数据。
            if (banMigrated) {
                dropColumnIfExists(connection, "sys_user", "ban_type");
                dropColumnIfExists(connection, "sys_user", "ban_until");
                dropColumnIfExists(connection, "sys_user", "banned_at");
            }
            // 用户名唯一索引 → 普通索引（唯一性改为逻辑判断，注销后可重新注册）。
            demoteUsernameUniqueIndex(connection, mysql);
            // 用户协议签署记录：补充签署语言、签署人、地点、时区与来源 IP。
            Map<String, String> agreementColumns = new LinkedHashMap<>();
            agreementColumns.put("language", "VARCHAR(16) NOT NULL DEFAULT 'zh-CN'");
            agreementColumns.put("signer_name", "VARCHAR(128)");
            agreementColumns.put("location", "VARCHAR(255)");
            agreementColumns.put("timezone", "VARCHAR(64)");
            agreementColumns.put("signed_ip", "VARCHAR(64)");
            agreementColumns.forEach((column, type) ->
                    ensureColumn(connection, "user_agreement", column, type));
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
                    + "SELECT 'ADMIN_CONSOLE', '管理员控制台', '访问管理员控制台（兼容旧权限）'"
                    + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'ADMIN_CONSOLE')");
            // 管理员控制台细分权限：用户管理 / 在线用户 / 操作记录 / 权限授权。
            statement.execute("INSERT INTO sys_permission (code, name, description) "
                    + "SELECT 'ADMIN_USERS', '用户管理', '查看与修改所有用户、投递与简历'"
                    + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'ADMIN_USERS')");
            statement.execute("INSERT INTO sys_permission (code, name, description) "
                    + "SELECT 'ADMIN_ONLINE', '在线用户', '查看在线用户并踢出'"
                    + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'ADMIN_ONLINE')");
            statement.execute("INSERT INTO sys_permission (code, name, description) "
                    + "SELECT 'ADMIN_OPERATIONS', '用户操作记录', '查看全局与用户维度的操作日志'"
                    + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'ADMIN_OPERATIONS')");
            statement.execute("INSERT INTO sys_permission (code, name, description) "
                    + "SELECT 'ADMIN_PERMISSIONS', '权限授权', '给成员授予或收回权限'"
                    + fromDual + " WHERE NOT EXISTS "
                    + "(SELECT 1 FROM sys_permission WHERE code = 'ADMIN_PERMISSIONS')");
            // 历史数据迁移：把 sys_user.role 复制到用户权限对应表（只补缺，不重复）。
            statement.execute("INSERT INTO sys_user_permission (user_id, permission_id) "
                    + "SELECT u.id, p.id FROM sys_user u "
                    + "JOIN sys_permission p ON p.code = u.role "
                    + "WHERE NOT EXISTS (SELECT 1 FROM sys_user_permission up "
                    + "WHERE up.user_id = u.id AND up.permission_id = p.id)");
            // 已有 admin 角色自动获得全部管理员控制台权限。
            statement.execute("INSERT INTO sys_user_permission (user_id, permission_id) "
                    + "SELECT u.id, p.id FROM sys_user u "
                    + "JOIN sys_permission p ON p.code IN ('ADMIN_CONSOLE', "
                    + "'ADMIN_USERS', 'ADMIN_ONLINE', 'ADMIN_PERMISSIONS') "
                    + "WHERE UPPER(u.role) = 'ADMIN' AND NOT EXISTS "
                    + "(SELECT 1 FROM sys_user_permission up "
                    + "WHERE up.user_id = u.id AND up.permission_id = p.id)");
            log.info("权限表检查完成（sys_permission / sys_user_permission）");
        } catch (Exception exception) {
            log.warn("权限表创建或迁移失败: {}", exception.getMessage());
        }
    }

    /** 封禁记录表：封禁状态、时间与原因都保存到这里。 */
    private boolean createBanTable(Connection connection, boolean mysql) {
        String sql;
        if (mysql) {
            sql = "CREATE TABLE IF NOT EXISTS sys_user_ban ("
                    + "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "tenant_id BIGINT UNSIGNED NOT NULL,"
                    + "user_id BIGINT UNSIGNED NOT NULL,"
                    + "ban_type VARCHAR(16) NOT NULL,"
                    + "reason VARCHAR(255) NULL,"
                    + "banned_at DATETIME NOT NULL,"
                    + "ban_until DATETIME NULL,"
                    + "unbanned_at DATETIME NULL,"
                    + "operator_id BIGINT UNSIGNED NULL,"
                    + "created_at DATETIME NULL,"
                    + "PRIMARY KEY (id),"
                    + "KEY idx_sys_user_ban_user (user_id, unbanned_at, ban_until),"
                    + "KEY idx_sys_user_ban_tenant (tenant_id, banned_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS sys_user_ban ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "tenant_id BIGINT NOT NULL,"
                    + "user_id BIGINT NOT NULL,"
                    + "ban_type VARCHAR(16) NOT NULL,"
                    + "reason VARCHAR(255),"
                    + "banned_at TIMESTAMP NOT NULL,"
                    + "ban_until TIMESTAMP,"
                    + "unbanned_at TIMESTAMP,"
                    + "operator_id BIGINT,"
                    + "created_at TIMESTAMP)";
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return true;
        } catch (Exception exception) {
            log.warn("创建封禁记录表失败: {}", exception.getMessage());
            return false;
        }
    }

    /** 用户操作记录表：AOP 捕获并经 RabbitMQ 异步写入。 */
    private void createOperationLogTable(Connection connection, boolean mysql) {
        String sql;
        if (mysql) {
            sql = "CREATE TABLE IF NOT EXISTS user_operation_log ("
                    + "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "tenant_id BIGINT UNSIGNED NOT NULL,"
                    + "user_id BIGINT UNSIGNED NOT NULL,"
                    + "username VARCHAR(64) NULL,"
                    + "display_name VARCHAR(64) NULL,"
                    + "operation_type VARCHAR(32) NOT NULL,"
                    + "action VARCHAR(32) NULL,"
                    + "description VARCHAR(255) NULL,"
                    + "method VARCHAR(16) NULL,"
                    + "path VARCHAR(255) NULL,"
                    + "ip VARCHAR(64) NULL,"
                    + "user_agent VARCHAR(255) NULL,"
                    + "created_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (id),"
                    + "KEY idx_user_operation_user_time (user_id, created_at),"
                    + "KEY idx_user_operation_type_time (operation_type, created_at),"
                    + "KEY idx_user_operation_tenant (tenant_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS user_operation_log ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "tenant_id BIGINT NOT NULL,"
                    + "user_id BIGINT NOT NULL,"
                    + "username VARCHAR(64),"
                    + "display_name VARCHAR(64),"
                    + "operation_type VARCHAR(32) NOT NULL,"
                    + "action VARCHAR(32),"
                    + "description VARCHAR(255),"
                    + "method VARCHAR(16),"
                    + "path VARCHAR(255),"
                    + "ip VARCHAR(64),"
                    + "user_agent VARCHAR(255),"
                    + "created_at TIMESTAMP NOT NULL"
                    + ")";
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            if (!mysql) {
                statement.execute("CREATE INDEX IF NOT EXISTS idx_user_operation_user_time "
                        + "ON user_operation_log (user_id, created_at)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_user_operation_type_time "
                        + "ON user_operation_log (operation_type, created_at)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_user_operation_tenant "
                        + "ON user_operation_log (tenant_id)");
            }
            log.info("用户操作记录表检查完成（user_operation_log）");
        } catch (Exception exception) {
            log.warn("创建用户操作记录表失败: {}", exception.getMessage());
        }
    }

    /**
     * 把旧版 sys_user.ban_type / ban_until / banned_at 迁移到封禁记录表，
     * 随后由调用方删除这 3 个列。
     */
    private boolean migrateLegacyBans(Connection connection, boolean mysql) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            boolean hasBanType = hasColumn(meta, connection, "sys_user", "ban_type");
            boolean hasBanUntil = hasColumn(meta, connection, "sys_user", "ban_until");
            boolean hasBannedAt = hasColumn(meta, connection, "sys_user", "banned_at");
            String select = "SELECT id, tenant_id, status"
                    + (hasBanType ? ", ban_type" : ", NULL AS ban_type")
                    + (hasBanUntil ? ", ban_until" : ", NULL AS ban_until")
                    + (hasBannedAt ? ", banned_at" : ", NULL AS banned_at")
                    + " FROM sys_user WHERE status = 'BANNED'"
                    + (hasBanType ? " OR ban_type IS NOT NULL" : "");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO sys_user_ban (tenant_id, user_id, ban_type, reason, "
                            + "banned_at, ban_until, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement exists = connection.prepareStatement(
                         "SELECT COUNT(*) FROM sys_user_ban WHERE user_id = ?");
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(select)) {
                int migrated = 0;
                while (rows.next()) {
                    long userId = rows.getLong("id");
                    exists.setLong(1, userId);
                    try (ResultSet found = exists.executeQuery()) {
                        if (found.next() && found.getLong(1) > 0) {
                            continue;
                        }
                    }
                    String banType = rows.getString("ban_type");
                    Timestamp until = rows.getTimestamp("ban_until");
                    Timestamp bannedAt = rows.getTimestamp("banned_at");
                    if (banType == null || banType.isBlank()) {
                        banType = "PERMANENT";
                    }
                    LocalDateTime now = LocalDateTime.now();
                    insert.setLong(1, rows.getLong("tenant_id"));
                    insert.setLong(2, userId);
                    insert.setString(3, banType);
                    insert.setString(4, "历史封禁数据");
                    insert.setTimestamp(5, bannedAt == null ? Timestamp.valueOf(now) : bannedAt);
                    if (until == null) {
                        insert.setNull(6, java.sql.Types.TIMESTAMP);
                    } else {
                        insert.setTimestamp(6, until);
                    }
                    insert.setTimestamp(7, Timestamp.valueOf(now));
                    insert.executeUpdate();
                    migrated++;
                }
                if (migrated > 0) {
                    log.info("已迁移 {} 条历史封禁记录到 sys_user_ban", migrated);
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE sys_user SET status = 'NORMAL' "
                        + "WHERE status = 'BANNED'");
            }
            return true;
        } catch (Exception exception) {
            log.warn("迁移历史封禁记录失败: {}", exception.getMessage());
            return false;
        }
    }

    private void dropColumnIfExists(Connection connection, String table, String column) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            if (!hasColumn(meta, connection, table, column)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
                log.info("已删除字段: {}.{}", table, column);
            }
        } catch (Exception exception) {
            log.warn("删除字段 {}.{} 失败: {}", table, column, exception.getMessage());
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
