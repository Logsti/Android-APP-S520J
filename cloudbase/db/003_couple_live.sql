-- S520J 批次二（共同生活）：每日挑战与双人事件流。
-- 与 001_schema.sql 相同约定：text 主键、bigint epoch 毫秒、幂等可重复执行。
-- 注意：002 已被 RLS 策略占用，本文件为 003。

BEGIN;

-- 每日挑战：每个空间每天一行，done_by 记录当天已标记的用户 id（幂等、不可撤销）。
CREATE TABLE IF NOT EXISTS challenge_days (
    id            text    PRIMARY KEY,
    space_id      text    NOT NULL,
    day           integer NOT NULL,                    -- 本地日期 epochDay
    prompt_index  integer NOT NULL DEFAULT 0,
    done_by       jsonb   NOT NULL DEFAULT '[]'::jsonb,
    done_at       bigint,                              -- 双方都完成的时间
    created_at    bigint  NOT NULL,
    updated_at    bigint  NOT NULL,
    version       integer NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_challenge_days_space_day ON challenge_days (space_id, day);

-- 双人事件流：碰一碰、挑战达成等，按空间隔离、按时间游标拉取。
CREATE TABLE IF NOT EXISTS couple_events (
    id          text   PRIMARY KEY,
    space_id    text   NOT NULL,
    from_user   text   NOT NULL,
    from_name   text   NOT NULL DEFAULT '',
    type        text   NOT NULL,                       -- poke / challenge_done
    body        text   NOT NULL DEFAULT '',
    created_at  bigint NOT NULL,
    updated_at  bigint NOT NULL,
    version     integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_couple_events_space_created ON couple_events (space_id, created_at);

COMMIT;
