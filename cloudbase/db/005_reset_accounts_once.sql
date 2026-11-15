-- ============================================================
-- 危险：一次性清空当前 CloudBase 环境的全部账号及关联数据，不可恢复！
-- 仅限实施人员核对 local.properties 的目标环境与当前 APP 完全一致后手动执行。
-- 禁止在 APP 内调用，禁止对未确认的环境执行，执行前必须另行备份需要保留的数据。
-- 用法：node cloudbase/scripts/run-sql.js cloudbase/db/005_reset_accounts_once.sql
-- run-sql.js 会逐条提交语句，因此不写 BEGIN/COMMIT，也不构成多语句伪事务。
-- 清库仅由下面这一条 TRUNCATE 原子完成，后续 SELECT 只负责独立验证清后计数。
-- ============================================================

TRUNCATE TABLE
    users,
    auth_sessions,
    spaces,
    space_members,
    bind_codes,
    bind_requests,
    contents,
    media_assets,
    media_blobs,
    recycle_bin,
    conflict_snapshots,
    sync_logs,
    backup_jobs,
    settings,
    notifications,
    challenge_days,
    couple_events
CASCADE;

-- 清后计数验证：执行结果必须正好返回以下 17 行，且每行 row_count 都为 0。
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'auth_sessions', COUNT(*) FROM auth_sessions
UNION ALL SELECT 'spaces', COUNT(*) FROM spaces
UNION ALL SELECT 'space_members', COUNT(*) FROM space_members
UNION ALL SELECT 'bind_codes', COUNT(*) FROM bind_codes
UNION ALL SELECT 'bind_requests', COUNT(*) FROM bind_requests
UNION ALL SELECT 'contents', COUNT(*) FROM contents
UNION ALL SELECT 'media_assets', COUNT(*) FROM media_assets
UNION ALL SELECT 'media_blobs', COUNT(*) FROM media_blobs
UNION ALL SELECT 'recycle_bin', COUNT(*) FROM recycle_bin
UNION ALL SELECT 'conflict_snapshots', COUNT(*) FROM conflict_snapshots
UNION ALL SELECT 'sync_logs', COUNT(*) FROM sync_logs
UNION ALL SELECT 'backup_jobs', COUNT(*) FROM backup_jobs
UNION ALL SELECT 'settings', COUNT(*) FROM settings
UNION ALL SELECT 'notifications', COUNT(*) FROM notifications
UNION ALL SELECT 'challenge_days', COUNT(*) FROM challenge_days
UNION ALL SELECT 'couple_events', COUNT(*) FROM couple_events;
