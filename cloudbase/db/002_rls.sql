-- S520J 权限与行级安全（CloudBase PostgreSQL / PG 模式）
-- 设计要点：
--   1) 客户端（anon / authenticated）不允许直连表：PostgREST 暴露面全部 REVOKE。
--   2) 业务访问一律经云函数（service_role），由云函数在每次请求内二次校验
--      owner_id / space_id / visibility / 冻结状态（见 cloudbase/functions/_shared/auth.js）。
--   3) 所有表启用 RLS 并保留显式拒绝策略，作为“直连被拒”的兜底证明；
--      service_role 具备 BYPASSRLS，云函数不受此策略影响。
--   4) 幂等：可重复执行。

BEGIN;

-- 0. 清理探测表
DROP TABLE IF EXISTS s520j_probe;

-- 1. 收回 PUBLIC / anon / authenticated 的全部表权限 ---------------------------------
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM anon;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM authenticated;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM anon;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM authenticated;

ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM authenticated;

-- 2. 云函数执行角色（service_role）授予读写 ----------------------------------------
GRANT USAGE ON SCHEMA public TO service_role;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO service_role;

-- 3. 启用行级安全 ----------------------------------------------------------------
ALTER TABLE users              ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth_sessions      ENABLE ROW LEVEL SECURITY;
ALTER TABLE spaces             ENABLE ROW LEVEL SECURITY;
ALTER TABLE space_members      ENABLE ROW LEVEL SECURITY;
ALTER TABLE bind_codes         ENABLE ROW LEVEL SECURITY;
ALTER TABLE bind_requests      ENABLE ROW LEVEL SECURITY;
ALTER TABLE contents           ENABLE ROW LEVEL SECURITY;
ALTER TABLE media_assets       ENABLE ROW LEVEL SECURITY;
ALTER TABLE recycle_bin        ENABLE ROW LEVEL SECURITY;
ALTER TABLE conflict_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_logs          ENABLE ROW LEVEL SECURITY;
ALTER TABLE backup_jobs        ENABLE ROW LEVEL SECURITY;
ALTER TABLE settings           ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications      ENABLE ROW LEVEL SECURITY;

-- 4. 显式拒绝直连策略（anon / authenticated 恒不可直读写）--------------------------
DROP POLICY IF EXISTS p_deny_anon_users              ON users;
DROP POLICY IF EXISTS p_deny_anon_auth_sessions      ON auth_sessions;
DROP POLICY IF EXISTS p_deny_anon_spaces             ON spaces;
DROP POLICY IF EXISTS p_deny_anon_space_members      ON space_members;
DROP POLICY IF EXISTS p_deny_anon_bind_codes         ON bind_codes;
DROP POLICY IF EXISTS p_deny_anon_bind_requests      ON bind_requests;
DROP POLICY IF EXISTS p_deny_anon_contents           ON contents;
DROP POLICY IF EXISTS p_deny_anon_media_assets       ON media_assets;
DROP POLICY IF EXISTS p_deny_anon_recycle_bin        ON recycle_bin;
DROP POLICY IF EXISTS p_deny_anon_conflict_snapshots ON conflict_snapshots;
DROP POLICY IF EXISTS p_deny_anon_sync_logs          ON sync_logs;
DROP POLICY IF EXISTS p_deny_anon_backup_jobs        ON backup_jobs;
DROP POLICY IF EXISTS p_deny_anon_settings           ON settings;
DROP POLICY IF EXISTS p_deny_anon_notifications      ON notifications;

CREATE POLICY p_deny_anon_users              ON users              AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_auth_sessions      ON auth_sessions      AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_spaces             ON spaces             AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_space_members      ON space_members      AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_bind_codes         ON bind_codes         AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_bind_requests      ON bind_requests      AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_contents           ON contents           AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_media_assets       ON media_assets       AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_recycle_bin        ON recycle_bin        AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_conflict_snapshots ON conflict_snapshots AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_sync_logs          ON sync_logs          AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_backup_jobs        ON backup_jobs        AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_settings           ON settings           AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
CREATE POLICY p_deny_anon_notifications      ON notifications      AS RESTRICTIVE FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);

-- 5. 服务端校验辅助函数：判断用户是否为空间成员 ------------------------------------
--    （云函数为权威校验；此函数供运维排查与后续 SQL 级策略复用）
CREATE OR REPLACE FUNCTION s520j_is_space_member(p_space_id text, p_user_id text)
RETURNS boolean
LANGUAGE sql
STABLE
AS 'SELECT EXISTS (SELECT 1 FROM space_members m WHERE m.space_id = p_space_id AND m.user_id = p_user_id AND m.deleted_at IS NULL)';

COMMIT;
