/**
 * 点菜接口：dish.list（池子快照 + 分类/自建库）、dish.pick（实时抽页 + 降级）、
 * dish.sync（定时抓页，只吃剩余配额）。
 *
 * 供应商 1164-1 必须带 cpName 关键词，无法按页取全量混排结果，因此：
 *  - 分类缓存里同时存 level2 关键词列表（二级分类名），实时与定时都拿它当 cpName；
 *  - 实时阶段随机取一个关键词、随机一页，整页入库后按本次请求过滤；
 *  - 定时阶段按 level2 稳定顺序轮询关键词，每个关键词内页码递增。
 *
 * 任何返回值都不包含 AppKey、供应商原始响应或堆栈；第三方失败只降级并记受控日志。
 */
const { badRequest, now } = require('./core');
const {
  BUILTIN_GROUPS, BUILTIN_TAXONOMY, GROUP_ORDER, groupsFromTaxonomy, classifyDish,
} = require('./dishTaxonomy');
const provider = require('./dishProvider');
const pool = require('./dishPool');
const { DISHES } = require('./dishes');

const MAX_PER_GROUP = 4;
const MAX_LIVE_CALLS = 3;
const PAGE_SIZE = 50;
const MAX_PAGE_CEILING = 1_000;
// 实时抽页只在一个关键词的前 20 页里随机，避免在长尾页码上空跑。
const MAX_LIVE_PAGE = 20;
// meta 里「关键词 → allPage」映射只保留最近 200 个键，避免单条 JSON 无限膨胀。
const MAX_KEYWORD_PAGES = 200;
const MAX_EXCLUDE = 500;
// 单轮定时任务最多抓 10 页（500 道）：串行 ≥1s + 单页解析入库，60s 超时内可稳定跑完。
const MAX_PAGES_PER_RUN = 10;
// 连续 3 页空结果（含失败）提前收手，避免白耗额度。
const SYNC_EMPTY_PAGE_LIMIT = 3;

function dailyLimit() {
  const value = Number(process.env.DISH_DAILY_LIMIT);
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 100;
}

function reserveCount() {
  const value = Number(process.env.DISH_RESERVE);
  return Number.isFinite(value) && value >= 0 ? Math.floor(value) : 20;
}

/** 未配置 AppKey 时直接跳过实时抽页，避免白耗当日额度。 */
function showapiConfigured() {
  return Boolean(String(process.env.SHOWAPI_APP_KEY || '').trim());
}

/** 受控日志：只输出我们自己定义的文案与状态码，不输出密钥或响应体。 */
function logDish(label, error) {
  const status = error && error.status ? `status=${error.status}` : '';
  const message = error && error.message ? String(error.message) : '';
  console.error('[s520j][dish]', label, status, message.slice(0, 80));
}

function shuffle(list) {
  const out = list.slice();
  for (let i = out.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

/** 随机页码，范围 [1, maxPages]；导出便于测试边界。 */
function randomPage(maxPages) {
  const max = Math.max(1, Math.floor(Number(maxPages) || 1));
  return 1 + Math.floor(Math.random() * max);
}

/** 校验份数与组内二级分类；份数 0–4，总数至少 1。 */
function parseGroups(input) {
  if (!Array.isArray(input) || !input.length) throw badRequest('请选择菜品份数');
  const counts = { meat: 0, veg: 0, soup: 0, staple: 0 };
  const subs = { meat: '', veg: '', soup: '', staple: '' };
  for (const raw of input) {
    const group = String((raw && raw.group) || '');
    if (!GROUP_ORDER.includes(group)) throw badRequest('菜品分组不合法');
    const count = Number(raw && raw.count);
    if (!Number.isInteger(count) || count < 0 || count > MAX_PER_GROUP) {
      throw badRequest('每组份数需为 0–4');
    }
    counts[group] += count;
    if (counts[group] > MAX_PER_GROUP) throw badRequest('每组份数需为 0–4');
    const sub = String((raw && raw.subCategory) || '').trim().slice(0, 20);
    if (sub && sub !== '全部') subs[group] = sub;
  }
  const total = GROUP_ORDER.reduce((sum, group) => sum + counts[group], 0);
  if (total < 1) throw badRequest('至少选择 1 道菜');
  return { counts, subs, total };
}

function parseNames(input) {
  if (!Array.isArray(input)) return [];
  const out = [];
  const seen = new Set();
  for (const raw of input) {
    const name = String(raw == null ? '' : raw).trim().slice(0, 40);
    if (!name || seen.has(name)) continue;
    seen.add(name);
    out.push(name);
    if (out.length >= MAX_EXCLUDE) break;
  }
  return out;
}

/** 去空白、去重、保持传入顺序。 */
function uniqueKeywords(list) {
  const out = [];
  const seen = new Set();
  for (const raw of list || []) {
    const name = String(raw == null ? '' : raw).trim();
    if (!name || seen.has(name)) continue;
    seen.add(name);
    out.push(name);
  }
  return out;
}

/** 缓存里没有 level2 时，用一级分类下的二级分类名补出关键词列表。 */
function keywordsFromLevel1(level1) {
  return uniqueKeywords(Object.values(level1).flat());
}

/** 内置兜底关键词：内置分类树的二级分类名。 */
function builtinKeywords() {
  return keywordsFromLevel1(BUILTIN_TAXONOMY);
}

/**
 * 分类缓存：同时保存 level1（一级 → 二级，供筛选项）与 level2（关键词，供 cpName 轮询）。
 * allowFetch 为 false 时只读缓存，不消耗供应商额度。
 */
async function loadTaxonomy(allowFetch) {
  let level1 = null;
  let level2 = [];
  try {
    const cached = await pool.getCachedTaxonomy();
    if (cached) {
      level1 = cached.level1;
      level2 = uniqueKeywords(cached.level2);
    }
  } catch (e) {
    logDish('分类树缓存读取失败', e);
  }
  if ((!level1 || !Object.keys(level1).length) && allowFetch) {
    try {
      const tree = await provider.fetchTaxonomy();
      level1 = tree && tree.level1;
      level2 = uniqueKeywords(tree && tree.level2);
      if (level1 && Object.keys(level1).length) {
        await pool.putCachedTaxonomy({ level1, level2 }).catch((e) => logDish('分类树缓存写入失败', e));
      }
    } catch (e) {
      logDish('分类树拉取失败', e);
    }
  }
  if (!level1 || !Object.keys(level1).length) level1 = BUILTIN_TAXONOMY;
  if (!level2.length) level2 = keywordsFromLevel1(level1);
  if (!level2.length) level2 = builtinKeywords();
  return { level1, level2 };
}

/** 分类树：优先 7 天缓存；allowFetch 为 false 时只用缓存/内置兜底（不消耗供应商额度）。 */
async function loadCategories(allowFetch) {
  const { level1 } = await loadTaxonomy(allowFetch);
  const merged = groupsFromTaxonomy(Object.keys(level1).length ? { level1 } : { level1: BUILTIN_TAXONOMY });
  const usable = GROUP_ORDER.some((group) => merged[group] && merged[group].length);
  if (!usable) return { ...BUILTIN_GROUPS };
  for (const group of GROUP_ORDER) {
    if (!merged[group] || !merged[group].length) merged[group] = BUILTIN_GROUPS[group].slice();
  }
  return merged;
}

/**
 * 抓取游标：kwIndex 指向 level2 关键词顺序，page 是当前关键词下一次要抓的页码，
 * pages 是「关键词 → allPage」的有界映射（最多 200 个键）。
 */
function normalizeCursor(value) {
  const source = value && typeof value === 'object' ? value : {};
  const pages = {};
  const rawPages = source.pages && typeof source.pages === 'object' ? source.pages : {};
  for (const key of Object.keys(rawPages).slice(-MAX_KEYWORD_PAGES)) {
    const allPage = Math.floor(Number(rawPages[key]) || 0);
    if (!key || allPage < 1) continue;
    pages[key] = Math.min(MAX_PAGE_CEILING, allPage);
  }
  return {
    kwIndex: Math.max(0, Math.floor(Number(source.kwIndex) || 0)),
    page: Math.max(1, Math.floor(Number(source.page) || 1)),
    keyword: typeof source.keyword === 'string' ? source.keyword : '',
    pages,
    allPage: Math.min(MAX_PAGE_CEILING, Math.max(1, Math.floor(Number(source.allPage) || 1))),
    allNum: Math.max(0, Math.floor(Number(source.allNum) || 0)),
  };
}

async function loadCursor() {
  try {
    const meta = await pool.getMeta(pool.SYNC_CURSOR_KEY);
    return normalizeCursor(meta && meta.value);
  } catch (e) {
    logDish('同步游标读取失败', e);
  }
  return normalizeCursor(null);
}

async function saveCursor(cursor) {
  await pool.putMeta(pool.SYNC_CURSOR_KEY, {
    kwIndex: cursor.kwIndex,
    page: cursor.page,
    keyword: cursor.keyword,
    pages: cursor.pages,
    allPage: cursor.allPage,
    allNum: cursor.allNum,
    at: now(),
  }).catch((e) => logDish('同步游标写入失败', e));
}

/** 记录「关键词 → allPage」；满 200 个键后按插入顺序淘汰最旧的，保持映射有界。 */
function rememberKeywordPage(cursor, keyword, allPage) {
  if (!keyword) return;
  const pages = cursor.pages;
  const known = Object.prototype.hasOwnProperty.call(pages, keyword);
  if (!known && Object.keys(pages).length >= MAX_KEYWORD_PAGES) {
    delete pages[Object.keys(pages)[0]];
  }
  pages[keyword] = Math.min(MAX_PAGE_CEILING, Math.max(1, Math.floor(Number(allPage) || 1)));
}

/** 随机取一个本次还没用过的关键词；优先命中指定分组，全部用过时纯随机，尽量不连续重复同一分类。 */
function pickKeyword(keywords, used, group) {
  if (!keywords.length) return '';
  const fresh = keywords.filter((keyword) => !used.has(keyword));
  const source = fresh.length ? fresh : keywords;
  if (group) {
    const targeted = source.filter((keyword) => classifyDish([keyword]).group === group);
    if (targeted.length) return targeted[Math.floor(Math.random() * targeted.length)];
  }
  return source[Math.floor(Math.random() * source.length)];
}

/** 供应商响应里的分页信息，缺省按 1 页处理。 */
function pageStats(data) {
  return {
    allPage: Math.min(MAX_PAGE_CEILING, Math.max(1, Math.floor(Number(data && data.allPage) || 1))),
    allNum: Math.max(0, Math.floor(Number(data && data.allNum) || 0)),
  };
}

/** 一页里能入库的菜：有菜名且能落到四组之一。 */
function pageRows(data) {
  const items = data && Array.isArray(data.items) ? data.items : [];
  return items.filter((item) => item && item.name && GROUP_ORDER.includes(item.group));
}

/** 实时抽页前的配额检查：剩余不足保底额度时不再实时。 */
async function acquireSlot(state) {
  if (state.liveCalls >= MAX_LIVE_CALLS) return false;
  if (state.remaining <= state.reserve) return false;
  let consumed;
  try {
    consumed = await pool.tryConsume(1, state.limit);
  } catch (e) {
    logDish('配额占用失败', e);
    return false;
  }
  state.remaining = consumed.remaining;
  return consumed.ok && state.remaining >= state.reserve;
}

/**
 * 实时抓一页：随机取一个二级分类关键词 → 随机页（1..min(allPage,20)）→ 整页入库。
 * 该关键词的 allPage 来自 meta 的分页映射（缺省 1）；抓完把最新 allPage 写回，但不动同步游标。
 */
async function drawLive(cursor, state, keywords, group) {
  state.liveCalls += 1;
  const keyword = pickKeyword(keywords, state.liveKeywords, group);
  if (keyword) state.liveKeywords.add(keyword);
  const known = cursor.pages[keyword] || 1;
  const page = randomPage(Math.min(known, MAX_LIVE_PAGE));
  const data = await provider.fetchPage(keyword, page, PAGE_SIZE);
  const stats = pageStats(data);
  rememberKeywordPage(cursor, keyword, stats.allPage);
  cursor.allPage = stats.allPage;
  cursor.allNum = stats.allNum;
  cursor.keyword = keyword;
  // 只写回分页信息：kwIndex / page 是定时任务的游标，点菜不得推进。
  await saveCursor(cursor);

  const rows = pageRows(data);
  if (rows.length) {
    // 实时整页入库：字段精简、按「菜名＋分类路径」去重，重复只更新最后出现时间。
    const stored = await pool.upsertDishes(rows.map((item) => ({ ...item, source: 'live' })))
      .catch((e) => { logDish('整页入库失败', e); return null; });
    state.upserted += (stored && stored.upserted) || 0;
  }
  for (const item of shuffle(rows)) {
    state.candidates.push({ ...item, source: 'live' });
  }
  return rows.length;
}

/** 候选里本组可用的菜名数（遵守二级分类过滤与排除项）。 */
function liveAvailable(group, state) {
  const sub = state.subs[group] || '';
  const names = new Set();
  for (const item of state.candidates) {
    if (item.group !== group) continue;
    if (sub && item.level2 !== sub) continue;
    if (state.excluded.has(item.name) || state.used.has(item.name)) continue;
    names.add(item.name);
  }
  return names.size;
}

/** 是否还有分组没凑够实时候选；凑够就不再消耗额度。 */
function liveNeeds(state) {
  return GROUP_ORDER.some((group) => state.counts[group] > liveAvailable(group, state));
}

/** 缺口最大的分组：实时抽页时优先对准它，避免抽到一堆用不上的分类。 */
function neediestGroup(state) {
  let best = '';
  let bestGap = 0;
  for (const group of GROUP_ORDER) {
    const gap = (state.counts[group] || 0) - liveAvailable(group, state);
    if (gap > bestGap) {
      best = group;
      bestGap = gap;
    }
  }
  return best;
}

function takeFromCandidates(group, need, state) {
  const sub = state.subs[group] || '';
  const out = [];
  for (const item of state.candidates) {
    if (out.length >= need) break;
    if (item.group !== group) continue;
    if (sub && item.level2 !== sub) continue;
    if (state.used.has(item.name) || state.excluded.has(item.name)) continue;
    if (out.some((picked) => picked.name === item.name)) continue;
    out.push({ name: item.name, level1: item.level1 || '', level2: item.level2 || '', group, source: 'live' });
  }
  for (const item of out) state.used.add(item.name);
  return out;
}

function exclusions(state, includeRecent, extra) {
  const names = new Set(state.excluded);
  for (const name of state.used) names.add(name);
  if (includeRecent) for (const name of state.recent) names.add(name);
  for (const name of extra || []) names.add(name);
  return [...names].slice(0, MAX_EXCLUDE);
}

/** 池子补菜：先避开最近点过的菜，不足时允许重复最近菜，但不突破用户排除项。 */
async function fillFromPool(group, level2, need, state) {
  const out = [];
  if (need <= 0) return out;
  const query = async (includeRecent) => {
    try {
      return await pool.randomFromPool(group, level2, need - out.length, exclusions(state, includeRecent, out.map((item) => item.name)));
    } catch (e) {
      logDish('池子查询失败', e);
      return [];
    }
  };

  const first = await query(true);
  for (const row of first) {
    if (out.length >= need) break;
    if (!row || !row.name || out.some((item) => item.name === row.name)) continue;
    out.push({
      name: row.name,
      level1: row.level1 || '',
      level2: row.level2 || '',
      group: row.group || group,
      source: 'pool',
    });
  }
  if (out.length < need) {
    const second = await query(false);
    for (const row of second) {
      if (out.length >= need) break;
      if (!row || !row.name || out.some((item) => item.name === row.name)) continue;
      if (state.used.has(row.name)) continue;
      out.push({
        name: row.name,
        level1: row.level1 || '',
        level2: row.level2 || '',
        group: row.group || group,
        source: 'pool',
      });
    }
  }
  for (const item of out) state.used.add(item.name);
  return out;
}

/** 自建库补菜：先按用户排除项过滤，仍不足时只保证不重复。 */
function fillFromBuiltin(group, level2, need, state) {
  const out = [];
  if (need <= 0) return [];
  const sub = String(level2 || '全部');
  const matched = DISHES.filter((dish) => dish.group === group && (sub === '全部' || dish.level2 === sub));
  const source = matched.length ? matched : DISHES.filter((dish) => dish.group === group);
  const shuffled = shuffle(source);

  for (const dish of shuffled) {
    if (out.length >= need) break;
    if (state.used.has(dish.name) || state.excluded.has(dish.name)) continue;
    out.push({ name: dish.name, level1: dish.level1, level2: dish.level2, group: dish.group, source: 'builtin' });
  }
  // 排除项过多时允许抽出被排除的菜，避免凑不齐一桌。
  if (out.length < need) {
    for (const dish of shuffled) {
      if (out.length >= need) break;
      if (state.used.has(dish.name)) continue;
      if (out.some((item) => item.name === dish.name)) continue;
      out.push({ name: dish.name, level1: dish.level1, level2: dish.level2, group: dish.group, source: 'builtin' });
    }
  }
  for (const item of out) state.used.add(item.name);
  return out;
}

async function dishList(ctx, payload = {}) {
  const limit = Math.min(Math.max(1, Math.floor(Number(payload.limit) || 200)), pool.SNAPSHOT_LIMIT);
  const cursor = payload.cursor;
  const firstPage = cursor == null || cursor === '' || String(cursor) === '0';
  const page = await pool.snapshot(firstPage ? null : cursor, limit);
  const total = await pool.count();
  const data = { items: page.items, nextCursor: page.nextCursor, total };
  if (firstPage) {
    data.categories = await loadCategories(false);
    data.builtin = DISHES.map(({ name, level1, level2, group }) => ({ name, level1, level2, group }));
  }
  return data;
}

async function dishPick(ctx, payload = {}) {
  const { counts, subs } = parseGroups(payload.groups);
  const excluded = new Set(parseNames(payload.exclude));
  const recent = new Set(parseNames(payload.recent).filter((name) => !excluded.has(name)));

  const limit = dailyLimit();
  const reserve = reserveCount();
  let remaining = 0;
  try {
    remaining = await pool.remaining(pool.todayKey(), limit);
  } catch (e) {
    logDish('配额读取失败', e);
  }

  const state = {
    limit,
    reserve,
    remaining,
    liveCalls: 0,
    liveKeywords: new Set(),
    upserted: 0,
    candidates: [],
    used: new Set(),
    excluded,
    recent,
    counts,
    subs,
  };

  // 取菜顺序：①实时按二级分类关键词抽页（按本次请求分组过滤，整页入库）
  //          ②云端菜品池补齐 ③内置 90 道兜底。
  // 只有「剩余额度高于保底」且「已配置 AppKey」才发起实时抽页，单次最多 3 次调用。
  if (showapiConfigured()) {
    const { level2 } = await loadTaxonomy(false);
    const cursor = await loadCursor();
    while (state.liveCalls < MAX_LIVE_CALLS && liveNeeds(state)) {
      if (!(await acquireSlot(state))) break;
      try {
        await drawLive(cursor, state, level2, neediestGroup(state));
      } catch (e) {
        logDish('实时抽页失败', e);
        if (e && e.status === 503 && /未配置/.test(e.message || '')) break;
      }
    }
    // 实时写入后按池子上限（10 万道，最久未出现优先淘汰）收敛，规则与定时同步一致。
    if (state.upserted) {
      await pool.trimPool().catch((e) => logDish('池子淘汰失败', e));
    }
  }

  const results = { meat: [], veg: [], soup: [], staple: [] };
  for (const group of GROUP_ORDER) {
    if (!counts[group]) continue;
    results[group] = takeFromCandidates(group, counts[group], state);
  }
  // ② 实时未覆盖（未抽到的分组或抽到的页不足）时用云端池补齐。
  for (const group of GROUP_ORDER) {
    const need = counts[group] - results[group].length;
    if (need <= 0) continue;
    const filled = await fillFromPool(group, subs[group] || '全部', need, state);
    results[group] = results[group].concat(filled);
  }
  // ③ 池子也不足时才用内置 90 道兜底。
  for (const group of GROUP_ORDER) {
    const need = counts[group] - results[group].length;
    if (need <= 0) continue;
    results[group] = results[group].concat(fillFromBuiltin(group, subs[group] || '全部', need, state));
  }

  const items = [];
  for (const group of GROUP_ORDER) {
    for (const item of results[group]) items.push(item);
  }

  const requested = GROUP_ORDER.reduce((sum, group) => sum + counts[group], 0);
  const short = requested - items.length;
  const degraded = items.some((item) => item.source !== 'live');
  let note;
  if (state.remaining <= reserve) note = '今日菜谱额度已用完，先用云端池与内置菜品';
  else if (short > 0) note = '云端池还在补齐，部分菜品来自内置';
  else if (degraded) note = '部分菜品来自云端池或内置';

  const result = { items, quotaRemaining: state.remaining, degraded };
  if (note) result.note = note;
  return result;
}

/**
 * 定时抓取：按 level2 关键词稳定顺序轮询（每页 50 道、串行 ≥1s），整页 upsert。
 * 游标是 { kwIndex, page }：同一关键词内页码递增，抓完最后一页或遇到空页就换下一个
 * 关键词并把 page 复位为 1；每页都把「关键词 → allPage」写回 meta（最多保留 200 个键）。
 * 连续 3 页空结果提前结束；单页失败只记失败页数并跳过，不整轮失败。
 * 只使用扣除保底后的剩余配额，任何异常都不向调度器抛错。
 */
async function dishSync(payload = {}) {
  try {
    const limit = dailyLimit();
    const reserve = reserveCount();
    const pages = Math.min(
      Math.max(1, Math.floor(Number(payload.pages) || MAX_PAGES_PER_RUN)),
      MAX_PAGES_PER_RUN
    );

    let remaining = await pool.remaining(pool.todayKey(), limit);
    if (remaining <= reserve) {
      return {
        ok: true, pages: 0, fetched: 0, inserted: 0, upserted: 0, failed: 0,
        keyword: '', allPage: 1, allNum: 0, remaining, skipped: true,
      };
    }
    if (!showapiConfigured()) {
      logDish('菜谱服务未配置', null);
      return { ok: false };
    }

    // 分类缓存既供筛选项，也提供 cpName 关键词列表（缓存缺失时才消耗一次 1164-2）。
    const { level2: keywords } = await loadTaxonomy(true);
    if (!keywords.length) {
      logDish('菜谱分类为空', null);
      return { ok: false };
    }

    const cursor = await loadCursor();
    let fetched = 0;
    let upserted = 0;
    let failed = 0;
    let processed = 0;
    let emptyStreak = 0;
    let keyword = cursor.keyword;

    for (let index = 0; index < pages; index += 1) {
      if (remaining <= reserve) break;
      let consumed;
      try {
        consumed = await pool.tryConsume(1, limit);
      } catch (e) {
        logDish('配额占用失败', e);
        return { ok: false };
      }
      remaining = consumed.remaining;
      if (!consumed.ok) break;

      keyword = keywords[cursor.kwIndex % keywords.length];
      const page = cursor.page;
      try {
        const data = await provider.fetchPage(keyword, page, PAGE_SIZE);
        const stats = pageStats(data);
        rememberKeywordPage(cursor, keyword, stats.allPage);
        cursor.allPage = stats.allPage;
        cursor.allNum = stats.allNum;
        const rows = pageRows(data);
        fetched += rows.length;
        emptyStreak = rows.length ? 0 : emptyStreak + 1;
        if (rows.length) {
          const stored = await pool.upsertDishes(rows.map((item) => ({ ...item, source: 'sync' })))
            .catch((e) => { logDish('整页入库失败', e); return { upserted: 0 }; });
          upserted += stored.upserted || 0;
        }
        processed += 1;
        // 空页（含「未找到相关菜谱」）或该关键词最后一页：换下一个关键词并复位页码。
        if (!rows.length || page >= stats.allPage) {
          cursor.kwIndex = (cursor.kwIndex + 1) % keywords.length;
          cursor.page = 1;
        } else {
          cursor.page = page + 1;
        }
      } catch (e) {
        failed += 1;
        // 失败页同样计入「无效页」，连续失败时提前收手，避免白耗额度。
        emptyStreak += 1;
        logDish('定时抽页失败', e);
        // 失败页跳过但不重复刷：没有该关键词的 allPage 记录时直接换关键词。
        const known = cursor.pages[keyword];
        if (!known || page >= known) {
          cursor.kwIndex = (cursor.kwIndex + 1) % keywords.length;
          cursor.page = 1;
        } else {
          cursor.page = page + 1;
        }
        // 配置类错误继续抓只会白耗额度，直接结束本轮（游标停在失败页之后）。
        if (e && e.status === 503 && /未配置/.test(e.message || '')) {
          cursor.keyword = keyword;
          await saveCursor(cursor);
          break;
        }
      }

      cursor.keyword = keyword;
      await saveCursor(cursor);
      if (emptyStreak >= SYNC_EMPTY_PAGE_LIMIT) break;
    }

    if (upserted) await pool.trimPool().catch((e) => logDish('池子淘汰失败', e));
    await pool.putMeta(pool.LAST_SYNC_KEY, {
      at: now(), fetched, upserted, pages: processed, failed, keyword, allPage: cursor.allPage,
    }).catch((e) => logDish('同步时间写入失败', e));
    remaining = await pool.remaining(pool.todayKey(), limit);
    return {
      ok: true,
      pages: processed,
      fetched,
      upserted,
      inserted: upserted,
      failed,
      keyword,
      allPage: cursor.allPage,
      allNum: cursor.allNum,
      remaining,
    };
  } catch (e) {
    logDish('定时同步失败', e);
    return { ok: false };
  }
}

/** 定时任务入口：直接跑增量，不经过 action 路由，也不要求登录。 */
async function dishSyncInternal(event) {
  return dishSync({ pages: event && event.pages });
}

/** 云函数 action 入口（需登录），只读剩余额度，不占用用户保底。 */
async function dishSyncHandler(ctx, payload) {
  return dishSync(payload || {});
}

module.exports = {
  dishList,
  dishPick,
  dishSync,
  dishSyncInternal,
  dishSyncHandler,
  parseGroups,
  parseNames,
  randomPage,
  dailyLimit,
  reserveCount,
};
