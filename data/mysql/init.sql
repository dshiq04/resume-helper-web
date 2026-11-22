-- 履途 ResumeTrail · MySQL 默认数据库文件
-- 使用方式：mysql -u root -p < data/mysql/init.sql
-- 默认账号：admin / 123456

CREATE DATABASE IF NOT EXISTS jobtrack
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE jobtrack;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    created_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_tenant_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenant_setting (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_setting (tenant_id, setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS applications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(32) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    link VARCHAR(2048) NOT NULL,
    apply_year INT NOT NULL,
    job_title VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'screening',
    channel VARCHAR(64) NULL,
    city VARCHAR(128) NULL,
    salary VARCHAR(128) NULL,
    contact_name VARCHAR(128) NULL,
    contact_info VARCHAR(255) NULL,
    work_mode VARCHAR(32) NULL,
    priority VARCHAR(16) NULL,
    interview_round INT DEFAULT 0,
    next_action_at DATETIME NULL,
    notes VARCHAR(2000) NULL,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    applied_at DATETIME NOT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_applications_tenant (tenant_id),
    KEY idx_applications_tenant_year (tenant_id, apply_year),
    KEY idx_applications_tenant_category (tenant_id, category),
    KEY idx_applications_tenant_status (tenant_id, status),
    KEY idx_applications_company (company_name),
    KEY idx_applications_applied (applied_at),
    KEY idx_applications_pinned (pinned)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS application_changes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    application_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    changed_at DATETIME NOT NULL,
    created_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_change (tenant_id, application_id, status),
    KEY idx_application_changes_application (tenant_id, application_id),
    KEY idx_application_changes_changed (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deleted_applications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    original_id BIGINT UNSIGNED NOT NULL,
    company_name VARCHAR(255) NULL,
    category VARCHAR(32) NULL,
    status VARCHAR(32) NULL,
    link VARCHAR(2048) NULL,
    payload_json LONGTEXT NULL,
    deleted_at DATETIME NOT NULL,
    created_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_deleted_applications_tenant (tenant_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resumes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    tags TEXT NULL,
    file_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    encrypted TINYINT(1) NOT NULL DEFAULT 1,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_stored (tenant_id, stored_name),
    KEY idx_resumes_tenant (tenant_id, pinned),
    KEY idx_resumes_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_agreement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    accepted_at DATETIME NOT NULL,
    language VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    signer_name VARCHAR(128) NULL,
    location VARCHAR(255) NULL,
    timezone VARCHAR(64) NULL,
    signed_ip VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_agreement_user (user_id),
    KEY idx_user_agreement_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    username VARCHAR(64) NULL,
    display_name VARCHAR(64) NULL,
    operation_type VARCHAR(32) NOT NULL,
    action VARCHAR(32) NULL,
    description VARCHAR(255) NULL,
    method VARCHAR(16) NULL,
    path VARCHAR(255) NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_user_operation_user_time (user_id, created_at),
    KEY idx_user_operation_type_time (operation_type, created_at),
    KEY idx_user_operation_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认工作区与管理员账号（密码 123456 的 BCrypt 哈希）
INSERT INTO sys_tenant (code, name, created_at)
SELECT 'default', '默认工作区', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE code = 'default');

INSERT INTO sys_user (tenant_id, username, password_hash, display_name, role, enabled, created_at, updated_at)
SELECT t.id, 'admin', '$2a$10$UviviMbgwoT.VLGn50Ouq.bZqALqllREXXaL2k2ceHBzYDO7V6ipO', '管理员', 'ADMIN', 1, NOW(), NOW()
FROM sys_tenant t
WHERE t.code = 'default'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');
