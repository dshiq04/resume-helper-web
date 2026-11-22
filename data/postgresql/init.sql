-- 履途 ResumeTrail · PostgreSQL 16+ 数据库初始化脚本
-- 用法：psql -U postgres -f data/postgresql/init.sql
-- 说明：创建 jobtrack 数据库与全部业务表，并写入默认工作区、admin 账号及全部权限。
-- 默认账号：admin
-- 默认密码：123456
-- 脚本可重复执行：建表使用 IF NOT EXISTS，初始化数据按条件插入。
-- 注意：脚本包含 CREATE DATABASE 与 \connect，请勿使用 psql -1（单事务模式）。

\set ON_ERROR_STOP on

SELECT 'CREATE DATABASE jobtrack'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'jobtrack')\gexec

\connect jobtrack

SET client_encoding = 'UTF8';

-- ---------------------------------------------------------------------------
-- 租户与账号
-- ---------------------------------------------------------------------------

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
    updated_at TIMESTAMP
);
-- 用户名唯一性改为逻辑判断（已注销账号的用户名可被重新注册）
CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user (username);
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

CREATE TABLE IF NOT EXISTS sys_user_ban (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    ban_type VARCHAR(16) NOT NULL,
    reason VARCHAR(255),
    banned_at TIMESTAMP NOT NULL,
    ban_until TIMESTAMP,
    unbanned_at TIMESTAMP,
    operator_id BIGINT,
    created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_user_ban_user
    ON sys_user_ban (user_id, unbanned_at, ban_until);
CREATE INDEX IF NOT EXISTS idx_sys_user_ban_tenant
    ON sys_user_ban (tenant_id, banned_at);

CREATE TABLE IF NOT EXISTS tenant_setting (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    setting_value TEXT,
    updated_at TIMESTAMP,
    CONSTRAINT uk_tenant_setting UNIQUE (tenant_id, setting_key)
);

-- ---------------------------------------------------------------------------
-- 投递与回收站
-- ---------------------------------------------------------------------------

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

-- ---------------------------------------------------------------------------
-- 简历、个人资料与协议
-- ---------------------------------------------------------------------------

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
    language VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    signer_name VARCHAR(128),
    location VARCHAR(255),
    timezone VARCHAR(64),
    signed_ip VARCHAR(64),
    CONSTRAINT uk_user_agreement_user UNIQUE (user_id)
);
CREATE INDEX IF NOT EXISTS idx_user_agreement_tenant ON user_agreement (tenant_id);

-- ---------------------------------------------------------------------------
-- 操作日志与授权码
-- ---------------------------------------------------------------------------

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

-- ---------------------------------------------------------------------------
-- 初始化数据
-- ---------------------------------------------------------------------------

-- 权限字典
INSERT INTO sys_permission (code, name, description, created_at)
SELECT 'USER', '普通用户', '默认权限', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'USER');

INSERT INTO sys_permission (code, name, description, created_at)
SELECT 'ADMIN', '管理员', '管理员权限', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ADMIN');

INSERT INTO sys_permission (code, name, description, created_at)
SELECT 'ADMIN_CONSOLE', '管理员控制台', '访问管理员控制台（兼容旧权限）', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ADMIN_CONSOLE');

INSERT INTO sys_permission (code, name, description, created_at)
SELECT 'ADMIN_USERS', '用户管理', '查看与修改所有用户、投递与简历', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ADMIN_USERS');

INSERT INTO sys_permission (code, name, description, created_at)
SELECT 'ADMIN_ONLINE', '在线用户', '查看在线用户并踢出', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ADMIN_ONLINE');

INSERT INTO sys_permission (code, name, description, created_at)
SELECT 'ADMIN_OPERATIONS', '用户操作记录', '查看全局与用户维度的操作日志', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ADMIN_OPERATIONS');

INSERT INTO sys_permission (code, name, description, created_at)
SELECT 'ADMIN_PERMISSIONS', '权限授权', '给成员授予或收回权限', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ADMIN_PERMISSIONS');

-- 默认工作区
INSERT INTO sys_tenant (code, name, created_at)
SELECT 'default', '默认工作区', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE code = 'default');

-- 管理员账号（密码 123456 的 BCrypt 哈希）
INSERT INTO sys_user (tenant_id, username, password_hash, display_name, role, status,
                      enabled, created_at, updated_at)
SELECT t.id, 'admin', '$2a$10$UviviMbgwoT.VLGn50Ouq.bZqALqllREXXaL2k2ceHBzYDO7V6ipO',
       '管理员', 'ADMIN', 'NORMAL', TRUE, NOW(), NOW()
FROM sys_tenant t
WHERE t.code = 'default'
  AND NOT EXISTS (
      SELECT 1 FROM sys_user u
      WHERE u.username = 'admin'
        AND (u.status IS NULL OR u.status <> 'CANCELLED')
  );

-- 确保 admin 角色为管理员（不覆盖已修改的密码）
UPDATE sys_user
SET role = 'ADMIN', updated_at = NOW()
WHERE username = 'admin'
  AND (status IS NULL OR status <> 'CANCELLED')
  AND role <> 'ADMIN';

-- 默认授予 admin 全部权限
INSERT INTO sys_user_permission (user_id, permission_id, created_at)
SELECT u.id, p.id, NOW()
FROM sys_user u
CROSS JOIN sys_permission p
WHERE u.username = 'admin'
  AND (u.status IS NULL OR u.status <> 'CANCELLED')
ON CONFLICT (user_id, permission_id) DO NOTHING;
