-- S520J 图片同步：内容记录保存云端图片引用，图片本体存于 media_blobs（按需下载缓存）。
-- 说明：当前环境未开通对象存储，图片压缩到 ≤250KB 后以 base64 存 PG，
-- 仅两人使用；后续接入对象存储时只需替换 media.upload / media.fetch 的实现。

BEGIN;

ALTER TABLE contents ADD COLUMN IF NOT EXISTS media_refs jsonb NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE IF NOT EXISTS media_blobs (
    id          text   PRIMARY KEY,                     -- 客户端生成的 assetId（UUID）
    owner_id    text   NOT NULL,
    space_id    text,
    mime        text   NOT NULL DEFAULT 'image/jpeg',
    bytes       bigint NOT NULL DEFAULT 0,
    data        text   NOT NULL,                        -- base64（不含 data: 前缀）
    created_at  bigint NOT NULL,
    updated_at  bigint NOT NULL,
    version     integer NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS ix_media_blobs_space ON media_blobs (space_id);

COMMIT;
