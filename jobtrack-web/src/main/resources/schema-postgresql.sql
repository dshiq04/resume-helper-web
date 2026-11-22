-- 履途 ResumeTrail · PostgreSQL 初始化脚本
-- application-{profile}.yml 的 spring.sql.init.schema-locations 指向本脚本，服务启动时自动执行。

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
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user (tenant_id);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP,
    CONSTRAINT uk_sys_permission_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_user_permission (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT uk_sys_user_permission UNIQUE (user_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_sys_user_permission_user ON sys_user_permission (user_id);

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

CREATE TABLE IF NOT EXISTS user_profile (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    real_name VARCHAR(64),
    gender VARCHAR(16),
    birthday DATE,
    phone VARCHAR(32),
    email VARCHAR(128),
    location VARCHAR(64),
    hometown VARCHAR(64),
    website VARCHAR(255),
    github VARCHAR(128),
    linkedin VARCHAR(128),
    wechat VARCHAR(64),
    target_position VARCHAR(128),
    expected_salary VARCHAR(64),
    work_mode VARCHAR(16),
    job_status VARCHAR(16),
    available_date DATE,
    school VARCHAR(128),
    major VARCHAR(128),
    degree VARCHAR(32),
    graduation_year INT,
    experience_years INT,
    current_company VARCHAR(128),
    current_title VARCHAR(128),
    skills VARCHAR(1000),
    languages VARCHAR(255),
    bio VARCHAR(2000),
    avatar_url VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_user_profile_user UNIQUE (user_id)
);
CREATE INDEX IF NOT EXISTS idx_user_profile_tenant ON user_profile (tenant_id);

CREATE TABLE IF NOT EXISTS user_agreement (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    accepted_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_agreement_user UNIQUE (user_id)
);
CREATE INDEX IF NOT EXISTS idx_user_agreement_tenant ON user_agreement (tenant_id);

CREATE TABLE IF NOT EXISTS access_token (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(80) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    resources VARCHAR(200) NOT NULL DEFAULT 'applications,resumes,statistics,profile',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    expires_on DATE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT uk_access_token_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_access_token_user ON access_token (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS access_token_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT,
    token_id BIGINT,
    token_name VARCHAR(64),
    code_prefix VARCHAR(16),
    scope VARCHAR(16),
    resources VARCHAR(200),
    result VARCHAR(16) NOT NULL,
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_access_token_log_user ON access_token_log (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_access_token_log_token ON access_token_log (token_id, created_at);
