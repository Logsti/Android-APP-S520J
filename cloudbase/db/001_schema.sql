-- S520J 云数据库初始化脚本（CloudBase PostgreSQL / PG 模式）
-- 约定：
--   * 主键为 text（UUID 字符串由云函数或客户端生成），时间统一为 bigint（epoch 毫秒）。
--   * 所有内容类表统一包含 space_id / owner_id / visibility / deleted_at / version，
--     服务端据此做权限校验（见 002_rls.sql 与云函数二次校验）。
--   * 幂等：全部使用 IF NOT EXISTS，可重复执行。

BEGIN;

-- 1. 账号与会话 ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id               text PRIMARY KEY,
    nickname         text    NOT NULL,
    account_id       text    NOT NULL,
    recovery_salt    text    NOT NULL,
    recovery_hash    text    NOT NULL,
    avatar           text,
    couple_start_at  bigint  NOT NULL DEFAULT 0,
    created_at       bigint  NOT NULL,
    updated_at       bigint  NOT NULL,
    deleted_at       bigint,
    version          integer NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_account_id ON users (account_id);
CREATE INDEX IF NOT EXISTS ix_users_updated_at ON users (updated_at);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id                  text PRIMARY KEY,
    user_id             text    NOT NULL,
    access_token_hash   text    NOT NULL,
    refresh_token_hash  text    NOT NULL,
    device_info         text,
    expires_at          bigint  NOT NULL,
    refresh_expires_at  bigint  NOT NULL,
    revoked             boolean NOT NULL DEFAULT false,
    created_at          bigint  NOT NULL,
    updated_at          bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_auth_sessions_user ON auth_sessions (user_id);
CREATE INDEX IF NOT EXISTS ix_auth_sessions_access ON auth_sessions (access_token_hash);
CREATE INDEX IF NOT EXISTS ix_auth_sessions_refresh ON auth_sessions (refresh_token_hash);
CREATE INDEX IF NOT EXISTS ix_auth_sessions_expires ON auth_sessions (expires_at);

-- 2. 共同空间与绑定 -----------------------------------------------------
CREATE TABLE IF NOT EXISTS spaces (
    id          text PRIMARY KEY,
    name        text    NOT NULL,
    intro       text    NOT NULL DEFAULT '',
    status      text    NOT NULL DEFAULT 'active',   -- active / frozen
    created_by  text    NOT NULL,
    created_at  bigint  NOT NULL,
    updated_at  bigint  NOT NULL,
    deleted_at  bigint,
    version     integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_spaces_status ON spaces (status);
CREATE INDEX IF NOT EXISTS ix_spaces_updated_at ON spaces (updated_at);

CREATE TABLE IF NOT EXISTS space_members (
    id          text PRIMARY KEY,
    space_id    text    NOT NULL,
    user_id     text    NOT NULL,
    role        text    NOT NULL DEFAULT 'member',   -- creator / partner
    joined_at   bigint  NOT NULL,
    created_at  bigint  NOT NULL,
    updated_at  bigint  NOT NULL,
    deleted_at  bigint,
    version     integer NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_space_members_pair ON space_members (space_id, user_id);
CREATE INDEX IF NOT EXISTS ix_space_members_user ON space_members (user_id);

CREATE TABLE IF NOT EXISTS bind_codes (
    id          text PRIMARY KEY,
    space_id    text    NOT NULL,
    code_hash   text    NOT NULL,
    created_by  text    NOT NULL,
    expires_at  bigint  NOT NULL,
    status      text    NOT NULL DEFAULT 'active',   -- active / used / expired
    created_at  bigint  NOT NULL,
    updated_at  bigint  NOT NULL,
    deleted_at  bigint,
    version     integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_bind_codes_hash ON bind_codes (code_hash);
CREATE INDEX IF NOT EXISTS ix_bind_codes_space ON bind_codes (space_id);

CREATE TABLE IF NOT EXISTS bind_requests (
    id              text PRIMARY KEY,
    space_id        text    NOT NULL,
    from_user       text    NOT NULL,
    to_user         text    NOT NULL,
    status          text    NOT NULL DEFAULT 'pending',  -- pending / confirmed / failed
    confirmed_by_a  text,
    confirmed_by_b  text,
    created_at      bigint  NOT NULL,
    updated_at      bigint  NOT NULL,
    deleted_at      bigint,
    version         integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_bind_requests_space ON bind_requests (space_id);
CREATE INDEX IF NOT EXISTS ix_bind_requests_status ON bind_requests (status);

-- 3. 内容（统一内容表，按 module 区分模块）-----------------------------
CREATE TABLE IF NOT EXISTS contents (
    id          text PRIMARY KEY,
    module      text    NOT NULL,
    space_id    text,
    owner_id    text    NOT NULL,
    owner_name  text    NOT NULL DEFAULT '',
    visibility  text    NOT NULL DEFAULT 'private',    -- private / couple
    title       text    NOT NULL DEFAULT '',
    content     text    NOT NULL DEFAULT '',
    event_date  bigint,
    media_ids   jsonb   NOT NULL DEFAULT '[]'::jsonb,
    extra       jsonb   NOT NULL DEFAULT '{}'::jsonb,
    created_at  bigint  NOT NULL,
    updated_at  bigint  NOT NULL,
    deleted_at  bigint,
    version     integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_contents_module ON contents (module);
CREATE INDEX IF NOT EXISTS ix_contents_space_updated ON contents (space_id, updated_at);
CREATE INDEX IF NOT EXISTS ix_contents_owner_updated ON contents (owner_id, updated_at);
CREATE INDEX IF NOT EXISTS ix_contents_updated_at ON contents (updated_at);
CREATE INDEX IF NOT EXISTS ix_contents_deleted_at ON contents (deleted_at);

-- 4. 媒体 ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS media_assets (
    id          text PRIMARY KEY,
    owner_id    text    NOT NULL,
    space_id    text,
    record_id   text,
    type        text    NOT NULL DEFAULT 'photo',
    storage_key text    NOT NULL,
    thumb_key   text,
    size        bigint  NOT NULL DEFAULT 0,
    status      text    NOT NULL DEFAULT 'pending',    -- pending / uploaded / failed
    visibility  text    NOT NULL DEFAULT 'private',
    created_at  bigint  NOT NULL,
    updated_at  bigint  NOT NULL,
    deleted_at  bigint,
    version     integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_media_assets_record ON media_assets (record_id);
CREATE INDEX IF NOT EXISTS ix_media_assets_space ON media_assets (space_id);
CREATE INDEX IF NOT EXISTS ix_media_assets_owner ON media_assets (owner_id);

-- 5. 回收站、冲突快照 ---------------------------------------------------
CREATE TABLE IF NOT EXISTS recycle_bin (
    id                  text PRIMARY KEY,
    record_type         text   NOT NULL,
    record_id           text   NOT NULL,
    space_id            text,
    deleted_by          text   NOT NULL,
    deleted_at          bigint NOT NULL,
    purge_requested_by  text,
    purge_confirmed_by  text,
    expire_at           bigint NOT NULL,
    created_at          bigint NOT NULL,
    updated_at          bigint NOT NULL,
    deleted_at_col      bigint,
    version             integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_recycle_bin_expire ON recycle_bin (expire_at);
CREATE INDEX IF NOT EXISTS ix_recycle_bin_space ON recycle_bin (space_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_recycle_bin_record ON recycle_bin (record_type, record_id);

CREATE TABLE IF NOT EXISTS conflict_snapshots (
    id           text PRIMARY KEY,
    record_type  text   NOT NULL,
    record_id    text   NOT NULL,
    field_json   jsonb  NOT NULL DEFAULT '{}'::jsonb,
    source_user  text   NOT NULL,
    created_at   bigint NOT NULL,
    expire_at    bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_conflict_snapshots_record ON conflict_snapshots (record_id);
CREATE INDEX IF NOT EXISTS ix_conflict_snapshots_expire ON conflict_snapshots (expire_at);

-- 6. 同步与备份 ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS sync_logs (
    id            text PRIMARY KEY,
    user_id       text   NOT NULL,
    device_id     text   NOT NULL,
    cursor        bigint NOT NULL DEFAULT 0,
    last_sync_at  bigint NOT NULL,
    created_at    bigint NOT NULL,
    updated_at    bigint NOT NULL,
    version       integer NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sync_logs_user_device ON sync_logs (user_id, device_id);

CREATE TABLE IF NOT EXISTS backup_jobs (
    id          text PRIMARY KEY,
    user_id     text   NOT NULL,
    status      text   NOT NULL DEFAULT 'pending',   -- pending / running / done / failed
    file_key    text,
    created_at  bigint NOT NULL,
    updated_at  bigint NOT NULL,
    version     integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_backup_jobs_user ON backup_jobs (user_id);

-- 7. 设置与通知 ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS settings (
    id          text PRIMARY KEY,
    scope       text   NOT NULL DEFAULT 'user',      -- user / space
    owner_id    text,
    space_id    text,
    key         text   NOT NULL,
    value       jsonb  NOT NULL DEFAULT '{}'::jsonb,
    created_at  bigint NOT NULL,
    updated_at  bigint NOT NULL,
    deleted_at  bigint,
    version     integer NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_settings_scope_key ON settings (scope, owner_id, space_id, key);

CREATE TABLE IF NOT EXISTS notifications (
    id           text PRIMARY KEY,
    user_id      text   NOT NULL,
    space_id     text,
    type         text   NOT NULL DEFAULT 'system',
    title        text   NOT NULL DEFAULT '',
    body         text   NOT NULL DEFAULT '',
    read         boolean NOT NULL DEFAULT false,
    created_at   bigint NOT NULL,
    updated_at   bigint NOT NULL,
    deleted_at   bigint,
    version      integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_notifications_user ON notifications (user_id, created_at);
CREATE INDEX IF NOT EXISTS ix_notifications_unread ON notifications (user_id, read);

COMMIT;
