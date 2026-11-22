-- 履途 ResumeTrail · PostgreSQL 默认数据库文件
-- 使用方式：psql -U postgres -f data/postgresql/init.sql
-- 默认账号：admin / 123456

\set ON_ERROR_STOP on

SELECT 'CREATE DATABASE jobtrack'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'jobtrack')\gexec

\connect jobtrack

CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT uk_sys_tenant_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user (tenant_id);

CREATE TABLE IF NOT EXISTS tenant_setting (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT,
    updated_at TIMESTAMP,
    CONSTRAINT uk_tenant_setting UNIQUE (tenant_id, setting_key)
);

CREATE TABLE IF NOT EXISTS applications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    link VARCHAR(2048) NOT NULL,
    apply_year INT NOT NULL,
    job_title VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'screening',
    channel VARCHAR(64),
    city VARCHAR(128),
    salary VARCHAR(128),
    contact_name VARCHAR(128),
    contact_info VARCHAR(255),
    work_mode VARCHAR(32),
    priority VARCHAR(16),
    interview_round INT DEFAULT 0,
    next_action_at TIMESTAMP,
    notes VARCHAR(2000),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_applications_tenant ON applications (tenant_id);
CREATE INDEX IF NOT EXISTS idx_applications_tenant_year ON applications (tenant_id, apply_year);
CREATE INDEX IF NOT EXISTS idx_applications_tenant_category ON applications (tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_applications_tenant_status ON applications (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_applications_company ON applications (company_name);
CREATE INDEX IF NOT EXISTS idx_applications_applied ON applications (applied_at);
CREATE INDEX IF NOT EXISTS idx_applications_pinned ON applications (pinned);

CREATE TABLE IF NOT EXISTS application_changes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT uk_application_change UNIQUE (tenant_id, application_id, status)
);
CREATE INDEX IF NOT EXISTS idx_application_changes_application ON application_changes (tenant_id, application_id);
CREATE INDEX IF NOT EXISTS idx_application_changes_changed ON application_changes (changed_at);

CREATE TABLE IF NOT EXISTS deleted_applications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    original_id BIGINT NOT NULL,
    company_name VARCHAR(255),
    category VARCHAR(32),
    status VARCHAR(32),
    link VARCHAR(2048),
    payload_json TEXT,
    deleted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_deleted_applications_tenant ON deleted_applications (tenant_id, deleted_at);

CREATE TABLE IF NOT EXISTS resumes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    tags TEXT,
    file_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    encrypted BOOLEAN NOT NULL DEFAULT TRUE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_resume_stored UNIQUE (tenant_id, stored_name)
);
CREATE INDEX IF NOT EXISTS idx_resumes_tenant ON resumes (tenant_id, pinned);
CREATE INDEX IF NOT EXISTS idx_resumes_name ON resumes (name);

CREATE TABLE IF NOT EXISTS user_agreement (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    accepted_at TIMESTAMP NOT NULL,
    language VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    signer_name VARCHAR(128),
    location VARCHAR(255),
    timezone VARCHAR(64),
    signed_ip VARCHAR(64),
    CONSTRAINT uk_user_agreement_user UNIQUE (user_id)
);
CREATE INDEX IF NOT EXISTS idx_user_agreement_tenant ON user_agreement (tenant_id);

CREATE TABLE IF NOT EXISTS user_operation_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(64),
    display_name VARCHAR(64),
    operation_type VARCHAR(32) NOT NULL,
    action VARCHAR(32),
    description VARCHAR(255),
    method VARCHAR(16),
    path VARCHAR(255),
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_operation_user_time ON user_operation_log (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_operation_type_time ON user_operation_log (operation_type, created_at);
CREATE INDEX IF NOT EXISTS idx_user_operation_tenant ON user_operation_log (tenant_id);

-- 默认工作区与管理员账号（密码 123456 的 BCrypt 哈希）
INSERT INTO sys_tenant (code, name, created_at)
SELECT 'default', '默认工作区', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE code = 'default');

INSERT INTO sys_user (tenant_id, username, password_hash, display_name, role, enabled, created_at, updated_at)
SELECT t.id, 'admin', '$2a$10$UviviMbgwoT.VLGn50Ouq.bZqALqllREXXaL2k2ceHBzYDO7V6ipO', '管理员', 'ADMIN', TRUE, NOW(), NOW()
FROM sys_tenant t
WHERE t.code = 'default'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');
