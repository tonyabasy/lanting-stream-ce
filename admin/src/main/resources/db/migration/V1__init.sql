-- V1: 初始化数据库表结构
-- SQLite 语法
-- 时间字段统一使用毫秒时间戳（BIGINT），对应 Java Long 类型
-- is_delete 软删除：0 未删除，非零（值为记录 id）已删除，由 MyBatis-Plus @TableLogic 管理

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_user (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    username         VARCHAR(100)  NOT NULL UNIQUE,
    password         VARCHAR(200)  NOT NULL,
    nickname         VARCHAR(100),
    avatar_url       VARCHAR(500),
    email            VARCHAR(200),
    super_admin_flag INTEGER       NOT NULL DEFAULT 0,
    auth_source      VARCHAR(50)   NOT NULL DEFAULT 'local',
    is_delete        INTEGER       NOT NULL DEFAULT 0,
    create_time      BIGINT        NOT NULL DEFAULT 0,
    update_time      BIGINT        NOT NULL DEFAULT 0
);

-- ============================================================
-- 工作空间表
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_workspace (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(100) NOT NULL,
    git_path    VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    created_by  INTEGER,
    is_delete   INTEGER      NOT NULL DEFAULT 0,
    create_time BIGINT       NOT NULL DEFAULT 0,
    update_time BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (name, is_delete)
);

-- ============================================================
-- 集群表
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_cluster (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           VARCHAR(100) NOT NULL,
    flink_home     VARCHAR(500) NOT NULL,
    flink_version  VARCHAR(50)  NOT NULL,
    resource_type  VARCHAR(50)  NOT NULL,
    deploy_target  VARCHAR(50)  NOT NULL,
    configurations TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_delete      INTEGER      NOT NULL DEFAULT 0,
    create_time    BIGINT       NOT NULL DEFAULT 0,
    update_time    BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (name, is_delete)
);
--
-- -- ============================================================
-- -- 数据源表
-- -- ============================================================
-- CREATE TABLE IF NOT EXISTS lanting_datasource (
--     id          INTEGER PRIMARY KEY AUTOINCREMENT,
--     name        VARCHAR(100) NOT NULL,
--     type        VARCHAR(50)  NOT NULL,
--     config      TEXT         NOT NULL,
--     status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
--     is_delete   INTEGER      NOT NULL DEFAULT 0,
--     create_time BIGINT       NOT NULL DEFAULT 0,
--     update_time BIGINT       NOT NULL DEFAULT 0
-- );
--
-- -- ============================================================
-- -- 表元数据（用于编辑器自动补全）
-- -- ============================================================
-- CREATE TABLE IF NOT EXISTS lanting_table_meta (
--     id            INTEGER PRIMARY KEY AUTOINCREMENT,
--     datasource_id INTEGER      NOT NULL,
--     table_name    VARCHAR(200) NOT NULL,
--     ddl_content   TEXT,
--     description   VARCHAR(500),
--     is_delete     INTEGER      NOT NULL DEFAULT 0,
--     create_time   BIGINT       NOT NULL DEFAULT 0,
--     update_time   BIGINT       NOT NULL DEFAULT 0
-- );
--
-- -- ============================================================
-- -- 字段元数据
-- -- ============================================================
-- CREATE TABLE IF NOT EXISTS lanting_column_meta (
--     id          INTEGER PRIMARY KEY AUTOINCREMENT,
--     table_id    INTEGER      NOT NULL,
--     name        VARCHAR(200) NOT NULL,
--     type        VARCHAR(100),
--     comment     VARCHAR(500),
--     is_delete   INTEGER      NOT NULL DEFAULT 0,
--     create_time BIGINT       NOT NULL DEFAULT 0,
--     update_time BIGINT       NOT NULL DEFAULT 0
-- );
--
-- -- ============================================================
-- -- 作业提交记录
-- -- ============================================================
-- CREATE TABLE IF NOT EXISTS lanting_job_submission (
--     id             INTEGER PRIMARY KEY AUTOINCREMENT,
--     job_id         VARCHAR(100),
--     job_name       VARCHAR(200),
--     cluster_id     INTEGER     NOT NULL,
--     script_file    VARCHAR(500),
--     status         VARCHAR(20) NOT NULL DEFAULT 'SUBMITTING',
--     source         VARCHAR(20) NOT NULL DEFAULT 'PLATFORM',
--     job_manager_url VARCHAR(500),
--     submit_log     TEXT,
--     error_message  TEXT,
--     submitted_by   INTEGER,
--     is_delete      INTEGER     NOT NULL DEFAULT 0,
--     create_time    BIGINT      NOT NULL DEFAULT 0,
--     update_time    BIGINT      NOT NULL DEFAULT 0
-- );
--
-- -- ============================================================
-- -- UDF 表
-- -- ============================================================
-- CREATE TABLE IF NOT EXISTS lanting_udf (
--     id          INTEGER PRIMARY KEY AUTOINCREMENT,
--     name        VARCHAR(200) NOT NULL,
--     description VARCHAR(500),
--     is_delete   INTEGER      NOT NULL DEFAULT 0,
--     create_time BIGINT       NOT NULL DEFAULT 0,
--     update_time BIGINT       NOT NULL DEFAULT 0
-- );
--
-- -- ============================================================
-- -- UDF 版本表
-- -- ============================================================
-- CREATE TABLE IF NOT EXISTS lanting_udf_version (
--     id          INTEGER PRIMARY KEY AUTOINCREMENT,
--     udf_id      INTEGER      NOT NULL,
--     version     VARCHAR(50)  NOT NULL,
--     class_name  VARCHAR(500) NOT NULL,
--     jar_path    VARCHAR(500) NOT NULL,
--     is_current  INTEGER      NOT NULL DEFAULT 0,
--     change_log  VARCHAR(500),
--     created_by  INTEGER,
--     is_delete   INTEGER      NOT NULL DEFAULT 0,
--     create_time BIGINT       NOT NULL DEFAULT 0,
--     update_time BIGINT       NOT NULL DEFAULT 0
-- );
--
-- -- ============================================================
-- -- LLM 配置表
-- -- ============================================================
-- CREATE TABLE IF NOT EXISTS lanting_llm_config (
--     id          INTEGER PRIMARY KEY AUTOINCREMENT,
--     name        VARCHAR(100) NOT NULL,
--     provider    VARCHAR(50)  NOT NULL,
--     api_key     VARCHAR(500) NOT NULL,
--     base_url    VARCHAR(500),
--     model       VARCHAR(100),
--     is_default  INTEGER      NOT NULL DEFAULT 0,
--     is_delete   INTEGER      NOT NULL DEFAULT 0,
--     create_time BIGINT       NOT NULL DEFAULT 0,
--     update_time BIGINT       NOT NULL DEFAULT 0
-- );

-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始管理员账号
-- 密码为占位符，应用启动时由 AdminInitializer（CommandLineRunner）替换为真实的 BCrypt 哈希
INSERT OR IGNORE INTO lanting_user (id, username, password, nickname, super_admin_flag, auth_source, create_time, update_time)
VALUES (1, 'admin', '$2a$10$4hYkZrFf570NlpROFczaXumqy8gD1GEzn4CAq.bh8IT8obbXgZK8e', 'Administrator', 1, 'local', 0, 0);

-- 初始默认工作空间
-- created_by 引用 admin 用户 id=1，需在 admin 插入之后执行（SQLite 顺序执行保证）
INSERT OR IGNORE INTO lanting_workspace (name, git_path, description, created_by, create_time, update_time)
VALUES ('default', './data/workspaces/default', '默认工作空间', 1, 0, 0);
