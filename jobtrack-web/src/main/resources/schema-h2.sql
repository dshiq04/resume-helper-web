-- 投递手账 · H2 初始化脚本（默认数据库，无需额外安装）
-- 所有业务表都包含 tenant_id，由 MyBatis-Plus 租户拦截器自动维护。

CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    created_at DATETIME,
    CONSTRAINT uk_sys_tenant_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user (tenant_id);

CREATE TABLE IF NOT EXISTS tenant_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT,
    updated_at DATETIME,
    CONSTRAINT uk_tenant_setting UNIQUE (tenant_id, setting_key)
);

CREATE TABLE IF NOT EXISTS applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    next_action_at DATETIME,
    notes VARCHAR(2000),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at DATETIME NOT NULL,
    created_at DATETIME,
    updated_at DATETIME
);
CREATE INDEX IF NOT EXISTS idx_applications_tenant ON applications (tenant_id);
CREATE INDEX IF NOT EXISTS idx_applications_tenant_year ON applications (tenant_id, apply_year);
CREATE INDEX IF NOT EXISTS idx_applications_tenant_category ON applications (tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_applications_tenant_status ON applications (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_applications_company ON applications (company_name);
CREATE INDEX IF NOT EXISTS idx_applications_applied ON applications (applied_at);
CREATE INDEX IF NOT EXISTS idx_applications_pinned ON applications (pinned);

CREATE TABLE IF NOT EXISTS application_changes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    changed_at DATETIME NOT NULL,
    created_at DATETIME,
    CONSTRAINT uk_application_change UNIQUE (tenant_id, application_id, status)
);
CREATE INDEX IF NOT EXISTS idx_application_changes_application ON application_changes (tenant_id, application_id);
CREATE INDEX IF NOT EXISTS idx_application_changes_changed ON application_changes (changed_at);

CREATE TABLE IF NOT EXISTS deleted_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    original_id BIGINT NOT NULL,
    company_name VARCHAR(255),
    category VARCHAR(32),
    status VARCHAR(32),
    link VARCHAR(2048),
    payload_json TEXT,
    deleted_at DATETIME NOT NULL,
    created_at DATETIME
);
CREATE INDEX IF NOT EXISTS idx_deleted_applications_tenant ON deleted_applications (tenant_id, deleted_at);

CREATE TABLE IF NOT EXISTS resumes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    tags TEXT,
    file_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    encrypted BOOLEAN NOT NULL DEFAULT TRUE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT uk_resume_stored UNIQUE (tenant_id, stored_name)
);
CREATE INDEX IF NOT EXISTS idx_resumes_tenant ON resumes (tenant_id, pinned);
CREATE INDEX IF NOT EXISTS idx_resumes_name ON resumes (name);
