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
-- 发布批次头表（lanting_publish）
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_publish (
    id           VARCHAR(64)  PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    published_by VARCHAR(100) NOT NULL,
    create_time  BIGINT       NOT NULL DEFAULT 0,
    update_time  BIGINT       NOT NULL DEFAULT 0
);

-- ============================================================
-- 发布文件生命周期表（lanting_publish_file）
-- 候选态：publish_id = 0，commit_hash = ''；发布时定格 commit_hash 并翻 PUBLISHED
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_publish_file (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    publish_id  VARCHAR(64)  NOT NULL DEFAULT '',
    file_id     INTEGER      NOT NULL,
    file_name   VARCHAR(200) NOT NULL,
    commit_hash VARCHAR(100) NOT NULL DEFAULT '',
    status      VARCHAR(20)  NOT NULL DEFAULT 'COMMITTED',
    created_by  VARCHAR(100) NOT NULL DEFAULT '',
    updated_by  VARCHAR(100) NOT NULL DEFAULT '',
    is_delete   INTEGER      NOT NULL DEFAULT 0,
    create_time BIGINT       NOT NULL DEFAULT 0,
    update_time BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pf_publish     ON lanting_publish_file(publish_id);
CREATE INDEX IF NOT EXISTS idx_pf_file_status ON lanting_publish_file(file_id, status);

-- ============================================================
-- 发布评审记录表（lanting_file_review）
-- 绑定 (file_id, commit_hash)，不阻断发布，可多条，仅作审计日志
-- ============================================================
CREATE TABLE IF NOT EXISTS lanting_file_review (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id     INTEGER      NOT NULL,
    commit_hash VARCHAR(100) NOT NULL,
    reviewer    VARCHAR(100) NOT NULL,
    comment     VARCHAR(500) NOT NULL DEFAULT '',
    result      VARCHAR(20)  NOT NULL,
    is_delete   INTEGER      NOT NULL DEFAULT 0,
    create_time BIGINT       NOT NULL DEFAULT 0,
    update_time BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_fr_file ON lanting_file_review(file_id);

-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始管理员账号
-- 密码为占位符，应用启动时由 AdminInitializer（CommandLineRunner）替换为真实的 BCrypt 哈希
INSERT OR IGNORE INTO lanting_user (id, username, password, nickname, super_admin_flag, auth_source, create_time, update_time)
VALUES (1, 'admin', '$2a$10$4hYkZrFf570NlpROFczaXumqy8gD1GEzn4CAq.bh8IT8obbXgZK8e', 'Administrator', 1, 'local', 0, 0);
