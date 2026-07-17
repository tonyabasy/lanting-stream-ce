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
    preferences      TEXT                   DEFAULT '{}',
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
    config      TEXT                  DEFAULT '{}',
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

-- ============================================================
-- 文件系统发布记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_file_publish (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tag_name     VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    commit_hash  VARCHAR(100) NOT NULL,
    created_by   VARCHAR(100) NOT NULL,
    is_delete    INTEGER      NOT NULL DEFAULT 0,
    create_time  BIGINT       NOT NULL DEFAULT 0,
    update_time  BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (tag_name, is_delete)
);

-- ============================================================
-- 文件系统 Review 记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_file_review (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    tag_name    VARCHAR(100) NOT NULL,
    reviewer    VARCHAR(100) NOT NULL,
    comment     VARCHAR(500),
    is_delete   INTEGER      NOT NULL DEFAULT 0,
    create_time BIGINT       NOT NULL DEFAULT 0,
    update_time BIGINT       NOT NULL DEFAULT 0
);

-- ============================================================
-- 文件系统元数据索引表
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_file_index (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    path               VARCHAR(1000) NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    type               VARCHAR(10)   NOT NULL,             -- file / folder
    parent_path        VARCHAR(1000) NOT NULL DEFAULT '',  -- 根目录子节点为空字符串
    mtime              BIGINT        NOT NULL DEFAULT 0,   -- 磁盘文件最后修改时间（毫秒）
    crc32              BIGINT        NOT NULL DEFAULT 0,   -- 文件内容 CRC32 校验和，用于检测 mtime 不变但内容变化的情况；folder 固定为 0
    deleted_at         BIGINT        NOT NULL DEFAULT 0,   -- 删除时间戳（毫秒），0 表示未删除
    latest_commit_hash VARCHAR(40),                      -- 最后一次进入 Git 的 commit hash
    create_time        BIGINT        NOT NULL DEFAULT 0,
    update_time        BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (path, deleted_at)
);

-- tree() 按 parent_path 分组查询，此索引是核心
CREATE INDEX IF NOT EXISTS idx_file_index_parent ON lanting_file_index(parent_path);

-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始管理员账号
-- 密码为占位符，应用启动时由 AdminInitializer（CommandLineRunner）替换为真实的 BCrypt 哈希
INSERT OR IGNORE INTO lanting_user (id, username, password, nickname, super_admin_flag, auth_source, create_time, update_time)
VALUES (1, 'admin', '$2a$10$4hYkZrFf570NlpROFczaXumqy8gD1GEzn4CAq.bh8IT8obbXgZK8e', 'Administrator', 1, 'local', 0, 0);
