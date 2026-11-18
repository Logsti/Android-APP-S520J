/**
 * 菜谱池读写：dishes（去重菜品）、dish_quota（按北京日期计数）、dish_meta（缓存与同步游标）。
 *
 * 所有写操作都是单条 SQL，跨云函数实例可见；池子上限 10 万道，超过后按最后出现时间淘汰。
 */
const { q, sha256, now } = require('./core');

const POOL_LIMIT = 100_000;
const BATCH_SIZE = 500;
const SNAPSHOT_LIMIT = 1_000;
const META_TTL = 7 * 24 * 60 * 60 * 1000;

const TAXONOMY_KEY = 'dishTaxonomy';
const LAST_SYNC_KEY = 'dishLastSyncAt';
// 抓取游标：{ kwIndex, page, keyword, pages, allPage, allNum, at }，
// kwIndex 指向分类关键词顺序，page 是该关键词下一次要抓的页码，pages 是「关键词 → allPage」。
const SYNC_CURSOR_KEY = 'dish_sync_cursor';

/** 北京日期（UTC+8），配额按自然日计数。 */
function todayKey(at = Date.now()) {
  return new Date(at + 8 * 3600 * 1000).toISOString().slice(0, 10);
}

/** 菜名＋分类路径决定稳定 id，重复入库不会产生新记录。 */
function dishId(name, path) {
  return 'dish-' + sha256(`${name}\u0000${path}`).slice(0, 40);
}

function mapRow(row) {
  return {
    name: row.name,
    level1: row.level1 || '',
    level2: row.level2 || '',
    group: row.group_name || row.group || '',
  };
}

/** 同一批内按「菜名＋分类路径」去重，避免一条 INSERT 内自冲突。 */
function dedupeRows(rows) {
  const seen = new Map();
  for (const row of rows || []) {
    if (!row || !row.name) continue;
    const name = String(row.name).trim();
    const group = String(row.group || '');
    if (!name || !['meat', 'veg', 'soup', 'staple'].includes(group)) continue;
    const path = String(row.path || '');
    const key = `${name}\u0000${path}`;
    if (!seen.has(key)) {
      seen.set(key, {
        name,
        level1: String(row.level1 || ''),
        level2: String(row.level2 || ''),
        group,
        path,
        source: String(row.source || 'live'),
      });
    }
  }
  return [...seen.values()];
}

/**
 * 批量 upsert：已存在（菜名＋分类路径相同）只更新 last_seen_at。
 * 返回处理条数（不等于新增条数，重复菜也计入）。
 */
async function upsertDishes(rows) {
  const list = dedupeRows(rows);
  let upserted = 0;
  for (let offset = 0; offset < list.length; offset += BATCH_SIZE) {
    const chunk = list.slice(offset, offset + BATCH_SIZE);
    const ts = now();
    const params = [];
    const tuples = [];
    for (const row of chunk) {
      const base = params.length;
      params.push(
        dishId(row.name, row.path), row.name, row.level1, row.level2,
        row.group, row.path, row.source, ts, ts
      );
      tuples.push(`($${base + 1},$${base + 2},$${base + 3},$${base + 4},$${base + 5},$${base + 6},$${base + 7},$${base + 8},$${base + 9})`);
    }
    await q(
      `INSERT INTO dishes (id, name, level1, level2, group_name, path, source, first_seen_at, last_seen_at)
       VALUES ${tuples.join(', ')}
       ON CONFLICT (name, path) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at`,
      params
    );
    upserted += chunk.length;
  }
  return { upserted };
}

/** 随机取菜：level2 传「全部」表示该组全部分类。 */
async function randomFromPool(group, level2, count, excludeNames = []) {
  const take = Math.max(0, Math.floor(Number(count) || 0));
  if (!take) return [];
  const sub = String(level2 == null ? '全部' : level2).trim() || '全部';
  const excluded = (Array.isArray(excludeNames) ? excludeNames : [])
    .map((name) => String(name || '').trim())
    .filter(Boolean)
    .slice(0, 500);
  const { rows } = await q(
    `SELECT name, level1, level2, group_name FROM dishes
      WHERE group_name = $1
        AND ($2 = '全部' OR level2 = $2)
        AND NOT (name = ANY($3::text[]))
      ORDER BY random() LIMIT $4`,
    [group, sub, excluded, take]
  );
  return rows.map(mapRow);
}

/** 超过上限时删除最久未出现的记录（保留最新的 limit 条）；limit 仅供测试缩小阈值。 */
async function trimPool(limit = POOL_LIMIT) {
  const cap = Math.max(0, Math.floor(Number(limit) || POOL_LIMIT));
  await q(
    'DELETE FROM dishes WHERE id IN (SELECT id FROM dishes ORDER BY last_seen_at DESC, id DESC OFFSET $1)',
    [cap]
  );
  return { limit: cap };
}

function encodeCursor(row) {
  return `${row.last_seen_at}:${row.id}`;
}

function decodeCursor(cursor) {
  const raw = String(cursor == null ? '' : cursor).trim();
  if (!raw || raw === '0') return null;
  const index = raw.indexOf(':');
  if (index <= 0) return null;
  const ts = Number(raw.slice(0, index));
  const id = raw.slice(index + 1);
  if (!Number.isFinite(ts) || !id) return null;
  return { ts: Math.floor(ts), id };
}

/** 按 last_seen_at DESC, id 游标分页（每页上限 1000）。 */
async function snapshot(cursor, limit) {
  const take = Math.min(Math.max(1, Math.floor(Number(limit) || 200)), SNAPSHOT_LIMIT);
  const position = decodeCursor(cursor);
  const { rows } = await q(
    `SELECT id, name, level1, level2, group_name, last_seen_at FROM dishes
      WHERE ($1::bigint IS NULL OR (last_seen_at, id) < ($1::bigint, $2::text))
      ORDER BY last_seen_at DESC, id DESC
      LIMIT $3`,
    [position ? position.ts : null, position ? position.id : null, take]
  );
  const items = rows.map(mapRow);
  const nextCursor = rows.length === take ? encodeCursor(rows[rows.length - 1]) : null;
  return { items, nextCursor };
}

async function count() {
  const { rows } = await q('SELECT COUNT(*)::int AS n FROM dishes');
  return Number(rows[0] && rows[0].n) || 0;
}

/** 当日剩余额度（只读）。 */
async function remaining(day, limit) {
  const total = Math.max(0, Math.floor(Number(limit) || 0));
  const { rows } = await q('SELECT used FROM dish_quota WHERE day = $1', [day]);
  const used = rows.length ? Number(rows[0].used) || 0 : 0;
  return Math.max(0, total - used);
}

/**
 * 原子占用 n 次额度：单条 INSERT ... ON CONFLICT DO UPDATE ... WHERE used+n <= limit。
 * 返回 { ok, remaining }；并发下超额请求拿不到返回行，不会多扣。
 */
async function tryConsume(n, limit) {
  const amount = Math.max(0, Math.floor(Number(n) || 0));
  const total = Math.max(0, Math.floor(Number(limit) || 0));
  const day = todayKey();
  if (!amount) return { ok: true, remaining: await remaining(day, total) };
  if (amount > total) return { ok: false, remaining: await remaining(day, total) };

  const { rows } = await q(
    `INSERT INTO dish_quota (day, used) VALUES ($1, $2)
     ON CONFLICT (day) DO UPDATE SET used = dish_quota.used + $2
     WHERE dish_quota.used + $2 <= $3
     RETURNING used`,
    [day, amount, total]
  );
  if (!rows.length) return { ok: false, remaining: await remaining(day, total) };
  return { ok: true, remaining: Math.max(0, total - (Number(rows[0].used) || 0)) };
}

async function getMeta(key) {
  const { rows } = await q('SELECT value, updated_at FROM dish_meta WHERE key = $1', [key]);
  if (!rows.length) return null;
  let value = rows[0].value;
  if (typeof value === 'string') {
    try { value = JSON.parse(value); } catch (_) { value = null; }
  }
  return { value, updatedAt: Number(rows[0].updated_at || 0) };
}

async function putMeta(key, value) {
  await q(
    `INSERT INTO dish_meta (key, value, updated_at) VALUES ($1, $2::jsonb, $3)
     ON CONFLICT (key) DO UPDATE SET value = $2::jsonb, updated_at = $3`,
    [key, JSON.stringify(value === undefined ? null : value), now()]
  );
  return { ok: true };
}

/** 读取 7 天内的分类树缓存，过期或缺失返回 null；level2 为关键词列表（旧缓存可能缺失）。 */
async function getCachedTaxonomy() {
  const meta = await getMeta(TAXONOMY_KEY);
  if (!meta || !meta.value) return null;
  if (now() - meta.updatedAt > META_TTL) return null;
  const level1 = meta.value.level1 || meta.value;
  if (!level1 || typeof level1 !== 'object' || !Object.keys(level1).length) return null;
  const level2 = Array.isArray(meta.value.level2)
    ? meta.value.level2.map((name) => String(name == null ? '' : name).trim()).filter(Boolean)
    : [];
  return { level1, level2 };
}

/** 缓存分类树：{ level1: 一级 → 二级列表, level2: 关键词列表 }。 */
async function putCachedTaxonomy(tree) {
  const level1 = (tree && tree.level1) || tree || {};
  const level2 = Array.isArray(tree && tree.level2) ? tree.level2 : null;
  await putMeta(TAXONOMY_KEY, level2 ? { level1, level2 } : { level1 });
  return { ok: true };
}

module.exports = {
  POOL_LIMIT,
  BATCH_SIZE,
  SNAPSHOT_LIMIT,
  META_TTL,
  TAXONOMY_KEY,
  LAST_SYNC_KEY,
  SYNC_CURSOR_KEY,
  todayKey,
  dishId,
  upsertDishes,
  randomFromPool,
  trimPool,
  snapshot,
  count,
  remaining,
  tryConsume,
  getMeta,
  putMeta,
  getCachedTaxonomy,
  putCachedTaxonomy,
};
