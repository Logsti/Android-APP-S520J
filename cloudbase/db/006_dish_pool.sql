-- S520J 批次三（今天吃啥）：菜谱池、每日配额与同步元数据。
-- 与 001/003 相同约定：text 主键、bigint epoch 毫秒、幂等可重复执行。
-- 三张表只服务点菜功能，不参与内容同步、回收站与备份。

BEGIN;

-- 菜谱池：按「菜名＋分类路径」去重，只保存展示与分组所需字段。
CREATE TABLE IF NOT EXISTS dishes (
    id            text   PRIMARY KEY,
    name          text   NOT NULL,
    level1        text   NOT NULL DEFAULT '',
    level2        text   NOT NULL DEFAULT '',
    group_name    text   NOT NULL,                    -- meat / veg / soup / staple
    path          text   NOT NULL DEFAULT '',         -- 分类标签拼接的分类路径
    source        text   NOT NULL DEFAULT 'live',     -- live / sync
    first_seen_at bigint NOT NULL,
    last_seen_at  bigint NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_dishes_name_path ON dishes (name, path);
CREATE INDEX IF NOT EXISTS ix_dishes_group_level2 ON dishes (group_name, level2);
CREATE INDEX IF NOT EXISTS ix_dishes_last_seen ON dishes (last_seen_at DESC);

-- 每日调用计数：day 为北京日期（YYYY-MM-DD），跨云函数实例共享。
CREATE TABLE IF NOT EXISTS dish_quota (
    day  text    PRIMARY KEY,
    used integer NOT NULL DEFAULT 0
);

-- 菜谱元数据：分类树缓存、页码上限、同步游标与最后同步时间。
CREATE TABLE IF NOT EXISTS dish_meta (
    key        text   PRIMARY KEY,
    value      jsonb  NOT NULL DEFAULT '{}'::jsonb,
    updated_at bigint NOT NULL
);

COMMIT;
