/**
 * 云端点菜规则测试（不联网）：
 * 分类树兜底、二级分类映射与优先级、菜品解析、池子 upsert/分页/配额、抽页降级与密钥不泄露。
 *
 * 运行：node --test cloudbase/dish-rules.test.js
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const crypto = require('node:crypto');

const TEST_KEY = 'test-showapi-key-should-not-leak';

// ------------------------------------------------------------ 内存库 + fake q
const store = {
  dishes: new Map(),   // 菜名\0路径 → 行
  quota: new Map(),    // 北京日期 → 已用次数
  meta: new Map(),     // key → { value, updated_at }
  failQuotaSelect: false,
};

let sqlLog = [];
let clock = 1_700_000_000_000;

function resetStore() {
  store.dishes.clear();
  store.quota.clear();
  store.meta.clear();
  store.failQuotaSelect = false;
  sqlLog = [];
  clock = 1_700_000_000_000;
}

const normalize = (text) => String(text).replace(/\s+/g, ' ').trim();

function dishKey(name, path) {
  return `${name}\u0000${path}`;
}

async function query(text, params = []) {
  const sql = normalize(text);
  sqlLog.push({ sql, params });

  if (sql.startsWith('INSERT INTO dish_quota')) {
    const [day, amount, limit] = params;
    const used = store.quota.get(day) || 0;
    if (used + amount <= limit) {
      store.quota.set(day, used + amount);
      return { rows: [{ used: used + amount }] };
    }
    return { rows: [] };
  }
  if (sql.startsWith('SELECT used FROM dish_quota')) {
    if (store.failQuotaSelect) throw new Error('模拟数据库不可用');
    const used = store.quota.get(params[0]);
    return { rows: used == null ? [] : [{ used }] };
  }

  if (sql.startsWith('INSERT INTO dish_meta')) {
    const [key, value, updatedAt] = params;
    store.meta.set(key, { value: JSON.parse(value), updated_at: updatedAt });
    return { rows: [] };
  }
  if (sql.startsWith('SELECT value, updated_at FROM dish_meta')) {
    const row = store.meta.get(params[0]);
    return { rows: row ? [{ value: row.value, updated_at: row.updated_at }] : [] };
  }

  if (sql.startsWith('INSERT INTO dishes (')) {
    const tupleCount = Math.floor(params.length / 9);
    for (let i = 0; i < tupleCount; i += 1) {
      const base = i * 9;
      const [id, name, level1, level2, group, path, source, firstSeen, lastSeen] = params.slice(base, base + 9);
      const key = dishKey(name, path);
      const existing = store.dishes.get(key);
      if (existing) {
        existing.last_seen_at = lastSeen;
      } else {
        store.dishes.set(key, {
          id, name, level1, level2, group_name: group, path, source,
          first_seen_at: firstSeen, last_seen_at: lastSeen,
        });
      }
    }
    return { rows: [] };
  }
  if (sql.startsWith('SELECT name, level1, level2, group_name FROM dishes')) {
    const [group, level2, excluded, take] = params;
    const rows = [...store.dishes.values()]
      .filter((row) => row.group_name === group)
      .filter((row) => level2 === '全部' || row.level2 === level2)
      .filter((row) => !excluded.includes(row.name))
      .slice(0, take);
    return { rows };
  }
  if (sql.startsWith('DELETE FROM dishes WHERE id IN')) {
    const offset = params[0];
    const rows = [...store.dishes.values()]
      .sort((a, b) => b.last_seen_at - a.last_seen_at || String(b.id).localeCompare(String(a.id)));
    for (const row of rows.slice(offset)) store.dishes.delete(dishKey(row.name, row.path));
    return { rows: [] };
  }
  if (sql.startsWith('SELECT id, name, level1, level2, group_name, last_seen_at FROM dishes')) {
    const [cursorTs, cursorId, take] = params;
    const rows = [...store.dishes.values()]
      .filter((row) => cursorTs == null
        || row.last_seen_at < cursorTs
        || (row.last_seen_at === cursorTs && String(row.id) < String(cursorId)))
      .sort((a, b) => b.last_seen_at - a.last_seen_at || String(b.id).localeCompare(String(a.id)))
      .slice(0, take);
    return { rows };
  }
  if (sql.startsWith('SELECT COUNT(*)::int AS n FROM dishes')) {
    return { rows: [{ n: store.dishes.size }] };
  }
  throw new Error(`未模拟的 SQL：${sql}`);
}

const corePath = require.resolve('./functions/_shared/core');
const coreStub = new Module(corePath);
coreStub.filename = corePath;
coreStub.loaded = true;
coreStub.exports = {
  q: query,
  withTx: async (fn) => fn({ query }),
  ApiError: class ApiError extends Error {
    constructor(status, message) { super(message); this.status = status; }
  },
  badRequest: (message) => Object.assign(new Error(message), { status: 400 }),
  unauthorized: (message = '登录已失效') => Object.assign(new Error(message), { status: 401 }),
  forbidden: (message = '没有权限') => Object.assign(new Error(message), { status: 403 }),
  uuid: () => `generated-${crypto.randomUUID()}`,
  sha256: (value) => crypto.createHash('sha256').update(String(value)).digest('hex'),
  randomToken: () => 'token',
  hashPassword: () => 'password',
  requireAccountId: (value) => value,
  requirePassword: (value) => value,
  requireNickname: (value) => value,
  now: () => clock,
  RECYCLE_TTL: 30 * 24 * 60 * 60 * 1000,
  publicUser: (value) => value,
  requireSession: async () => { throw new Error('测试直接调用 handler'); },
  issueSession: async () => ({}),
  assertMember: async () => ({}),
  spaceOfUser: async () => null,
  canRead: async () => true,
};
require.cache[corePath] = coreStub;

const taxonomy = require('./functions/_shared/dishTaxonomy');
const { DISHES } = require('./functions/_shared/dishes');
const provider = require('./functions/_shared/dishProvider');
const pool = require('./functions/_shared/dishPool');

function seedDish(name, group, level2, lastSeen) {
  const path = `seed/${level2}/${name}`;
  store.dishes.set(dishKey(name, path), {
    id: pool.dishId(name, path),
    name,
    level1: taxonomy.LEVEL2_LEVEL1.get(level2) || '',
    level2,
    group_name: group,
    path,
    source: 'live',
    first_seen_at: lastSeen,
    last_seen_at: lastSeen,
  });
}

// ------------------------------------------------------------ 分类与自建库
test('内置分类树兜底，二级分类到四组映射正确且不做菜名猜测', () => {
  const groups = taxonomy.BUILTIN_GROUPS;
  assert.deepEqual(groups.soup, ['汤', '汤羹']);
  assert.equal(groups.meat.includes('猪'), true);
  assert.equal(groups.meat.includes('鱼'), true);
  assert.equal(groups.veg.includes('蛋类'), true);
  assert.equal(groups.staple.includes('米面类'), true);
  assert.equal(groups.staple.includes('三明治'), true);

  assert.equal(taxonomy.level2ToGroup('猪'), 'meat');
  assert.equal(taxonomy.level2ToGroup('其他水产'), 'meat');
  assert.equal(taxonomy.level2ToGroup('奶制品'), 'veg');
  assert.equal(taxonomy.level2ToGroup('汤羹'), 'soup');
  assert.equal(taxonomy.level2ToGroup('馄饨'), 'staple');
  // 烘焙甜品饮料、干果类、腌咸制品、热门专题/口味类不参与随机
  for (const excluded of ['烘焙', '甜品', '饮品', '干果类', '腌咸制品', '特色食品', '菜式', '口味', '器具']) {
    assert.equal(taxonomy.level2ToGroup(excluded), null, `${excluded} 不应映射到四组`);
    assert.equal(
      ['meat', 'veg', 'soup', 'staple'].some((group) => groups[group].includes(excluded)),
      false,
      `${excluded} 不应出现在筛选项`
    );
  }

  // 官方样例：多级标签空格拼接
  assert.equal(taxonomy.groupForDish({ type: '热门专题 蛋奶豆制品 菜式 蛋类 创意菜 鸡蛋' }), 'veg');
  // 冲突优先级：汤 ＞ 荤 ＞ 素 ＞ 主食
  assert.equal(taxonomy.groupForDish({ type: '汤 猪 叶类蔬菜' }), 'soup');
  assert.equal(taxonomy.groupForDish({ type: '猪 叶类蔬菜' }), 'meat');
  assert.equal(taxonomy.groupForDish({ type: '叶类蔬菜 饭' }), 'veg');
  assert.equal(taxonomy.groupForDish({ type_v1: '饭', type_v2: '饼' }), 'staple');
  // 只看标签，绝不按菜名猜
  assert.equal(taxonomy.groupForDish({ type: '热门专题 菜式', type_v1: '口味' }), null);
  assert.equal(taxonomy.groupForDish({ cpName: '红烧肉' }), null);

  // 自定义分类树过滤空分类
  const custom = taxonomy.groupsFromTaxonomy({
    level1: { 肉类: ['猪'], 烘焙甜品饮料: ['烘焙', '甜品'], 水产: [], 蔬菜水果: ['水果'] },
  });
  assert.deepEqual(custom, { meat: ['猪'], veg: ['水果'] });
});

test('自建库覆盖四组常用二级分类且标签自洽', () => {
  const counts = { meat: 0, veg: 0, soup: 0, staple: 0 };
  const names = new Set();
  for (const dish of DISHES) {
    assert.equal(names.has(dish.name), false, `菜名重复：${dish.name}`);
    names.add(dish.name);
    assert.equal(taxonomy.GROUP_ORDER.includes(dish.group), true, `${dish.name} 分组非法`);
    assert.equal(taxonomy.level2ToGroup(dish.level2), dish.group, `${dish.name} 二级分类与分组不一致`);
    assert.equal(taxonomy.LEVEL2_LEVEL1.get(dish.level2), dish.level1, `${dish.name} 一级分类不一致`);
    counts[dish.group] += 1;
  }
  assert.equal(counts.meat >= 40, true, `荤菜不足：${counts.meat}`);
  assert.equal(counts.veg >= 25, true, `素菜不足：${counts.veg}`);
  assert.equal(counts.soup >= 10, true, `汤不足：${counts.soup}`);
  assert.equal(counts.staple >= 15, true, `主食不足：${counts.staple}`);

  for (const group of taxonomy.GROUP_ORDER) {
    const level2s = new Set(DISHES.filter((dish) => dish.group === group).map((dish) => dish.level2));
    assert.equal(level2s.size >= 2, true, `${group} 的二级分类过少`);
    assert.equal(DISHES.filter((dish) => dish.group === group).length >= 4, true, `${group} 无法凑出一桌`);
  }
});

// ------------------------------------------------------------ 供应商解析
test('ShowAPI 1164-2 映射分类树解析出一级/二级与扁平关键词', () => {
  // 实测返回：一级 → { 二级: [三级...] }
  const mapped = provider.parseTaxonomy({
    蛋奶豆制品: { 蛋类: ['鸡蛋', '咸蛋', '皮蛋'], 豆制品: ['豆腐', '腐竹'] },
    热门专题: { 菜式: ['创意菜', '家常菜'] },
    空分类: {},
  });
  assert.deepEqual(mapped.level1, { 蛋奶豆制品: ['蛋类', '豆制品'], 热门专题: ['菜式'] });
  assert.deepEqual(
    mapped.level2,
    ['鸡蛋', '咸蛋', '皮蛋', '豆腐', '腐竹', '创意菜', '家常菜'].sort(),
    'level2 应是最深一层名字的扁平去重稳定排序'
  );
  assert.equal(mapped.level2.includes('鸡蛋'), true);
  assert.equal(mapped.level2.includes('豆腐'), true);

  // { 一级: [二级...] } 映射同样兼容
  const flatMap = provider.parseTaxonomy({ 肉类: [{ name: '猪' }, '牛'], 汤粥主食: ['汤', '饭'] });
  assert.deepEqual(flatMap.level1, { 肉类: ['猪', '牛'], 汤粥主食: ['汤', '饭'] });
  assert.deepEqual(flatMap.level2, ['猪', '牛', '汤', '饭'].sort());

  // 旧数组结构仍可用
  const tree = provider.parseTaxonomy({
    list: [
      { name: '肉类', list: [{ name: '猪' }, { name: '牛' }] },
      { name: '水产', childList: ['鱼', '虾'] },
      { name: '空分类', list: [] },
    ],
  });
  assert.deepEqual(tree.level1, { 肉类: ['猪', '牛'], 水产: ['鱼', '虾'] });
  assert.deepEqual(tree.level2, ['猪', '牛', '鱼', '虾'].sort());

  // 空结构仍返回 {}
  assert.deepEqual(provider.parseTaxonomy({}), {});
  assert.deepEqual(provider.parseTaxonomy(null), {});
  assert.deepEqual(provider.parseTaxonomy('not-json'), {});
});

test('ShowAPI 响应解析：扁平/嵌套 datas 与标签归一化', () => {
  const flat = provider.parseDishPage({
    datas: [
      { cpName: '番茄炒蛋', type: '热门专题 蛋奶豆制品 菜式 蛋类 创意菜 鸡蛋' },
      { cpName: '番茄炒蛋', type: '蛋奶豆制品 蛋类' },
      { cpName: '蛋糕', type: '烘焙甜品饮料 烘焙' },
      { cpName: '', type: '肉类 猪' },
    ],
    allPage: 37,
    allNum: 1850,
  });
  assert.equal(flat.allPage, 37);
  assert.equal(flat.allNum, 1850);
  assert.equal(flat.items.length, 3);
  assert.equal(flat.items[0].group, 'veg');
  assert.equal(flat.items[0].level1, '蛋奶豆制品');
  assert.equal(flat.items[0].level2, '蛋类');
  assert.equal(flat.items[0].path, '热门专题/蛋奶豆制品/菜式/蛋类/创意菜/鸡蛋');
  assert.equal(flat.items[1].group, 'veg');
  assert.equal(flat.items[2].group, null, '烘焙不进入四组');

  const nested = provider.parseDishPage({ data: { datas: [{ cpName: '水煮牛肉', type: '肉类 牛' }] } });
  assert.equal(nested.items.length, 1);
  assert.equal(nested.items[0].group, 'meat');
  assert.equal(nested.allPage, 1);

  const priority = provider.normalizeDish({ cpName: '混标签菜', type: '汤 猪 叶类蔬菜' });
  assert.equal(priority.group, 'soup');
  assert.equal(priority.level2, '汤');
});

test('ShowAPI 客户端：1164-1 必带 cpName、串行间隔与错误归一化（不泄露密钥）', async () => {
  const originalFetch = global.fetch;
  const requests = [];
  global.fetch = async (url, options) => {
    requests.push({ url, options, at: Date.now() });
    return {
      ok: true,
      status: 200,
      json: async () => ({
        showapi_res_code: 0,
        showapi_res_body: {
          list: [{ name: '肉类', list: [{ name: '猪' }] }],
          datas: [{ cpName: '青椒炒肉', type: '肉类 猪' }],
          allPage: 12,
          allNum: 600,
        },
      }),
      text: async () => '',
    };
  };

  try {
    process.env.SHOWAPI_APP_KEY = TEST_KEY;
    provider.__resetThrottle();

    const tree = await provider.fetchTaxonomy();
    assert.deepEqual(tree.level1, { 肉类: ['猪'] });
    assert.deepEqual(tree.level2, ['猪']);
    assert.match(requests[0].url, /1164-2\?appKey=/);
    assert.equal(requests[0].options.method, 'POST');
    assert.equal(requests[0].options.body.includes(TEST_KEY), false, 'appKey 不应出现在表单体');

    const page = await provider.fetchPage('鸡蛋', 3, 200);
    assert.equal(page.allPage, 12);
    assert.equal(page.allNum, 600);
    assert.equal(page.items[0].name, '青椒炒肉');
    const form = new URLSearchParams(requests[1].options.body);
    // 1164-1 必须带 cpName 关键词；不传分类参数 type。
    assert.deepEqual([...form.keys()].sort(), ['cpName', 'maxResults', 'page']);
    assert.equal(form.get('cpName'), '鸡蛋');
    assert.equal(form.has('type'), false, '不得向 1164-1 传 type');
    assert.equal(form.get('page'), '3');
    assert.equal(form.get('maxResults'), '50', 'maxResults 上限为 50');

    // 空关键词直接 400，不发请求
    const before = requests.length;
    await assert.rejects(() => provider.fetchPage('   ', 1), (error) => error.status === 400);
    assert.equal(requests.length, before, '空关键词不得发出请求');

    // 相邻请求间隔 ≥1 秒
    const gap = requests[1].at - requests[0].at;
    assert.equal(gap >= 990, true, `请求间隔不足：${gap}ms`);

    // 业务错误归一化为 503，不暴露响应体
    global.fetch = async () => ({
      ok: true, status: 200,
      json: async () => ({ showapi_res_code: 1001, showapi_res_body: { datas: [] } }),
    });
    await assert.rejects(() => provider.fetchPage('鸡蛋', 1), (error) => error.status === 503);

    global.fetch = async () => ({
      ok: true, status: 200,
      json: async () => ({ showapi_res_code: 0, showapi_res_body: { flag: false, datas: [] } }),
    });
    await assert.rejects(() => provider.fetchPage('鸡蛋', 1), (error) => error.status === 503);

    global.fetch = async () => ({
      ok: true, status: 200,
      json: async () => ({ showapi_res_code: 0, showapi_res_body: { ret_code: '1', datas: [] } }),
    });
    await assert.rejects(() => provider.fetchPage('鸡蛋', 1), (error) => error.status === 503);

    global.fetch = async () => { throw new Error('network down'); };
    await assert.rejects(() => provider.fetchPage('鸡蛋', 1), (error) => error.status === 503);

    delete process.env.SHOWAPI_APP_KEY;
    await assert.rejects(() => provider.fetchTaxonomy(), (error) => error.status === 503 && /未配置/.test(error.message));
  } finally {
    global.fetch = originalFetch;
    delete process.env.SHOWAPI_APP_KEY;
  }
});

test('ShowAPI 1164-1：未找到相关菜谱返回空页而非报错，也不消耗失败计数', async () => {
  const originalFetch = global.fetch;
  let response = { flag: false, ret_code: -1, remark: '未找到相关菜谱！' };
  global.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => ({ showapi_res_code: 0, showapi_res_body: response }),
  });

  try {
    process.env.SHOWAPI_APP_KEY = TEST_KEY;
    provider.__resetThrottle();

    const empty = await provider.fetchPage('咸蛋', 4);
    assert.deepEqual(empty, { items: [], allPage: 1, allNum: 0, empty: true });

    // 文案在 msg 上、ret_code 非 0 同样算空页
    response = { ret_code: '1', msg: '未找到相关菜谱' };
    const viaMsg = await provider.fetchPage('皮蛋', 1);
    assert.equal(viaMsg.empty, true);
    assert.equal(viaMsg.items.length, 0);

    // 明确失败（没有「未找到」文案）仍然抛 503
    response = { flag: false, remark: '接口调用超过限制' };
    await assert.rejects(() => provider.fetchPage('皮蛋', 1), (error) => error.status === 503);
  } finally {
    global.fetch = originalFetch;
    delete process.env.SHOWAPI_APP_KEY;
  }
});

// ------------------------------------------------------------ 池子与配额
test('菜谱池 upsert 按菜名+路径去重并分批写入', async () => {
  resetStore();
  const duplicate = [
    { name: '红烧肉', level1: '肉类', level2: '猪', group: 'meat', path: '肉类/猪' },
    { name: '红烧肉', level1: '肉类', level2: '猪', group: 'meat', path: '肉类/猪', source: 'sync' },
  ];
  const first = await pool.upsertDishes(duplicate);
  assert.equal(first.upserted, 1);
  assert.equal(store.dishes.size, 1);

  clock += 5_000;
  await pool.upsertDishes(duplicate);
  assert.equal(store.dishes.size, 1, '重复菜不得新增记录');
  assert.equal([...store.dishes.values()][0].last_seen_at, clock, '已存在只更新最后出现时间');

  resetStore();
  const many = Array.from({ length: 501 }, (_, index) => ({
    name: `测试菜${index}`, level1: '肉类', level2: '猪', group: 'meat', path: `肉类/猪/${index}`,
  }));
  const bulk = await pool.upsertDishes(many);
  assert.equal(bulk.upserted, 501);
  assert.equal(store.dishes.size, 501);
  const insertCount = sqlLog.filter((entry) => entry.sql.startsWith('INSERT INTO dishes (')).length;
  assert.equal(insertCount, 2, '501 条应拆成两批');

  // 分组为 null / 非法分组的记录直接跳过
  const skipped = await pool.upsertDishes([
    { name: '蛋糕', group: null, path: '烘焙' },
    { name: '腌菜', group: 'other', path: '腌咸' },
  ]);
  assert.equal(skipped.upserted, 0);
});

test('池子随机抽样、快照游标分页与超限淘汰', async () => {
  resetStore();
  seedDish('红烧肉', 'meat', '猪', 10);
  seedDish('清蒸鱼', 'meat', '鱼', 11);
  seedDish('番茄炒蛋', 'veg', '蛋类', 12);
  seedDish('番茄蛋汤', 'soup', '汤', 13);
  seedDish('蛋炒饭', 'staple', '饭', 14);

  const meat = await pool.randomFromPool('meat', '全部', 5, []);
  assert.equal(meat.length, 2);
  assert.equal(meat.every((item) => item.group === 'meat'), true);

  const fish = await pool.randomFromPool('meat', '鱼', 5, []);
  assert.deepEqual(fish.map((item) => item.name), ['清蒸鱼']);

  const excluded = await pool.randomFromPool('meat', '全部', 5, ['红烧肉', '清蒸鱼']);
  assert.equal(excluded.length, 0);

  const first = await pool.snapshot(null, 2);
  assert.deepEqual(first.items.map((item) => item.name), ['蛋炒饭', '番茄蛋汤']);
  assert.equal(typeof first.nextCursor, 'string');

  const second = await pool.snapshot(first.nextCursor, 2);
  assert.deepEqual(second.items.map((item) => item.name), ['番茄炒蛋', '清蒸鱼']);

  const third = await pool.snapshot(second.nextCursor, 2);
  assert.deepEqual(third.items.map((item) => item.name), ['红烧肉']);
  assert.equal(third.nextCursor, null);
  assert.equal(await pool.count(), 5);

  // 缩小阈值验证淘汰逻辑：只保留最后出现的 2 条
  await pool.trimPool(2);
  assert.equal(store.dishes.size, 2);
  const survivors = [...store.dishes.values()].map((row) => row.name).sort();
  assert.deepEqual(survivors, ['番茄蛋汤', '蛋炒饭'].sort());
});

test('每日配额原子自增并返回剩余，保底由调用方控制', async () => {
  resetStore();
  const day = pool.todayKey();
  assert.equal(await pool.remaining(day, 100), 100);

  const first = await pool.tryConsume(1, 100);
  assert.deepEqual(first, { ok: true, remaining: 99 });
  const bulk = await pool.tryConsume(79, 100);
  assert.deepEqual(bulk, { ok: true, remaining: 20 });
  assert.equal(await pool.remaining(day, 100), 20);

  const over = await pool.tryConsume(30, 100);
  assert.equal(over.ok, false);
  assert.equal(over.remaining, 20);
  assert.equal(store.quota.get(day), 80, '超额请求不得写入');

  const zero = await pool.tryConsume(0, 100);
  assert.equal(zero.ok, true);
  assert.equal(zero.remaining, 20);
});

// ------------------------------------------------------------ 接口层（mock 供应商全量混排页）
const GROUPS = ['meat', 'veg', 'soup', 'staple'];
const GROUP_LEVEL2 = { meat: '猪', veg: '蛋类', soup: '汤', staple: '饭' };
// 分类缓存里的 level2 关键词顺序固定，便于断言游标推进。
const KEYWORDS = ['猪', '蛋类', '汤', '饭'];

function resetState() {
  resetStore();
  live.calls = [];
  live.taxonomyCalls = 0;
  live.taxonomy = () => ({ level1: taxonomy.BUILTIN_TAXONOMY, level2: KEYWORDS.slice() });
  live.page = () => ({ items: [], allPage: 1, allNum: 0 });
  seedTaxonomy(KEYWORDS);
  process.env.DISH_DAILY_LIMIT = '100';
  process.env.DISH_RESERVE = '20';
  process.env.SHOWAPI_APP_KEY = TEST_KEY;
}

const live = {
  calls: [],
  taxonomyCalls: 0,
  taxonomy: () => ({ level1: taxonomy.BUILTIN_TAXONOMY, level2: KEYWORDS.slice() }),
  page: () => ({ items: [], allPage: 1, allNum: 0 }),
};

const providerPath = require.resolve('./functions/_shared/dishProvider');
const providerStub = new Module(providerPath);
providerStub.filename = providerPath;
providerStub.loaded = true;
providerStub.exports = {
  fetchTaxonomy: async () => {
    live.taxonomyCalls += 1;
    return live.taxonomy();
  },
  fetchPage: async (...args) => {
    const [keyword, page, size] = args;
    live.calls.push({ args, keyword, page, size });
    return live.page(keyword, page, size);
  },
};
require.cache[providerPath] = providerStub;

const handlersPath = require.resolve('./functions/_shared/handlers');
delete require.cache[handlersPath];
const { actions } = require('./functions/_shared/handlers');

const ctx = { user: { id: 'user-a', nickname: '小明' }, sessionId: 'session-a' };
const handler = (action) => actions[action].handler;

/** 一道菜：level2 与分组自洽，符合 classifyDish 的判定结果。 */
function dishOf(group, page, index, level2) {
  const sub = level2 || GROUP_LEVEL2[group];
  return {
    name: `${sub}-${page}-${index}`,
    level1: taxonomy.LEVEL2_LEVEL1.get(sub) || '',
    level2: sub,
    group,
    path: `${sub}/${page}/${index}`,
  };
}

/** 模拟供应商关键词整页结果：50 道按页码错开四个分组。 */
function mixedPage(keyword, page, size) {
  const pageNo = Math.max(1, Math.floor(Number(page) || 1));
  const total = Math.min(50, Math.max(1, Math.floor(Number(size) || 50)));
  return {
    items: Array.from({ length: total }, (_, index) => dishOf(GROUPS[(index + pageNo) % GROUPS.length], pageNo, index)),
    allPage: 20,
    allNum: 60,
  };
}

/** 预置分类缓存（level1 供筛选项、level2 供关键词轮询）。 */
function seedTaxonomy(level2 = KEYWORDS, level1 = taxonomy.BUILTIN_TAXONOMY) {
  store.meta.set(pool.TAXONOMY_KEY, { value: { level1, level2: level2.slice() }, updated_at: clock });
}

/** 预置抓取游标：kwIndex 指向关键词顺序，page 是当前关键词下一次要抓的页码。 */
function seedCursor({ kwIndex = 0, page = 1, pages = {}, allPage = 1, allNum = 0, keyword = '' } = {}) {
  store.meta.set(pool.SYNC_CURSOR_KEY, {
    value: { kwIndex, page, keyword, pages, allPage, allNum },
    updated_at: clock,
  });
}

test('dish 接口已注册且默认需要登录', () => {
  for (const action of ['dish.list', 'dish.pick', 'dish.sync']) {
    assert.equal(Boolean(actions[action]), true, `${action} 未注册`);
    assert.equal(actions[action].public, undefined, `${action} 不应公开`);
  }
});

test('dish.list 首页下发分类与自建库，后续页只返回池子快照', async () => {
  resetState();
  seedDish('红烧肉', 'meat', '猪', 10);
  seedDish('番茄炒蛋', 'veg', '蛋类', 20);
  seedDish('蛋炒饭', 'staple', '饭', 30);

  const first = await handler('dish.list')(ctx, { limit: 2 });
  assert.equal(first.items.length, 2);
  assert.equal(first.total, 3);
  assert.equal(typeof first.nextCursor, 'string');
  assert.equal(first.builtin.length, DISHES.length);
  assert.equal(first.categories.meat.includes('猪'), true);
  assert.equal(first.categories.soup.length, 2);

  const second = await handler('dish.list')(ctx, { cursor: first.nextCursor, limit: 2 });
  assert.equal(second.items.length, 1);
  assert.equal(second.nextCursor, null);
  assert.equal(second.categories, undefined, '非首页不下发分类');
  assert.equal(second.builtin, undefined, '非首页不下发自建库');
});

test('dish.pick 实时随机页整页入库，命中本次分组的结果标记 live', async () => {
  resetState();
  live.page = mixedPage;

  const result = await handler('dish.pick')(ctx, {
    groups: [
      { group: 'meat', count: 2 },
      { group: 'veg', count: 1 },
      { group: 'soup', count: 1 },
      { group: 'staple', count: 2 },
    ],
  });

  assert.equal(live.calls.length, 1, '一页混排已覆盖四组，不再多耗额度');
  assert.equal(live.calls[0].args.length, 3, '实时调用传关键词、页码与条数');
  assert.equal(KEYWORDS.includes(live.calls[0].keyword), true, '关键词应来自分类缓存的 level2');
  assert.equal(live.calls[0].size, 50);
  assert.equal(result.items.length, 6);
  assert.deepEqual(result.items.map((item) => item.group), ['meat', 'meat', 'veg', 'soup', 'staple', 'staple']);
  assert.equal(result.items.every((item) => item.source === 'live'), true);
  assert.equal(result.quotaRemaining, 99);
  assert.equal(result.degraded, false);
  // 整页（不只被抽中的 6 道）入库：50 道全部落到四组并标记 live
  assert.equal(store.dishes.size, 50);
  assert.equal([...store.dishes.values()].every((row) => row.source === 'live' && row.group_name), true);
  assert.equal(JSON.stringify(result).includes(TEST_KEY), false, '返回值不得包含密钥');
});

test('dish.pick 实时空页也受 3 次调用上限约束并降级到内置库', async () => {
  resetState();
  live.page = () => ({ items: [], allPage: 5, allNum: 0 });

  const result = await handler('dish.pick')(ctx, {
    groups: [
      { group: 'meat', count: 2 },
      { group: 'veg', count: 1 },
      { group: 'soup', count: 1 },
      { group: 'staple', count: 2 },
    ],
  });

  assert.equal(live.calls.length, 3, '空页最多烧 3 次额度');
  assert.equal(result.quotaRemaining, 97);
  assert.equal(result.items.length, 6);
  assert.equal(result.items.every((item) => item.source === 'builtin'), true, '空页只能降级到自建库');
  assert.equal(result.degraded, true);
  assert.equal(store.dishes.size, 0, '空页不得写入池子');
});

test('dish.pick 实时页没有目标分组时用云端池补齐并标记 pool', async () => {
  resetState();
  seedDish('菌菇汤', 'soup', '汤', 5);
  // 供应商页里只有荤菜，汤始终抽不到
  live.page = (keyword, page, size) => ({
    items: Array.from({ length: size || 50 }, (_, index) => dishOf('meat', page, index)),
    allPage: 1,
    allNum: 50,
  });

  const result = await handler('dish.pick')(ctx, {
    groups: [{ group: 'meat', count: 1 }, { group: 'soup', count: 1 }],
  });

  assert.equal(live.calls.length, 3, '目标分组缺失时仍只花 3 次额度');
  const meat = result.items.find((item) => item.group === 'meat');
  const soup = result.items.find((item) => item.group === 'soup');
  assert.equal(meat.source, 'live', '实时页命中的分组用实时结果');
  assert.equal(soup.source, 'pool', '实时页没有的分组用云端池补齐');
  assert.equal(soup.name, '菌菇汤');
  assert.equal(result.degraded, true);
  assert.equal(result.items.length, 2);
});

test('dish.pick 剩余额度低于保底时按池子→自建库降级', async () => {
  resetState();
  seedDish('菌菇汤', 'soup', '汤', 5);
  store.quota.set(pool.todayKey(), 81); // 剩余 19，低于保底 20
  live.page = mixedPage;

  const result = await handler('dish.pick')(ctx, {
    groups: [{ group: 'meat', count: 1 }, { group: 'soup', count: 1 }],
  });

  assert.equal(live.calls.length, 0, '剩余不足保底时不得实时抽页');
  assert.equal(result.items.length, 2);
  assert.equal(result.quotaRemaining, 19);
  assert.equal(result.degraded, true);
  assert.match(result.note, /额度/);
  const soup = result.items.find((item) => item.group === 'soup');
  const meat = result.items.find((item) => item.group === 'meat');
  assert.equal(soup.source, 'pool', '有池子时优先用池子');
  assert.equal(meat.source, 'builtin', '池子没有该组时用自建库');
  assert.equal(store.quota.get(pool.todayKey()), 81, '不得占用保底额度');
});

test('dish.pick 实时优先：额度充足时先实时抽页，实时结果优先于云端池', async () => {
  resetState();
  // 池子里本来就有这两个分组的菜，额度充足时不得抢在实时结果之前
  seedDish('池子红烧肉', 'meat', '猪', 10);
  seedDish('池子青菜', 'veg', '叶类蔬菜', 11);
  live.page = mixedPage;

  const result = await handler('dish.pick')(ctx, {
    groups: [{ group: 'meat', count: 2 }, { group: 'veg', count: 1 }],
  });

  assert.equal(live.calls.length, 1, '一页即可覆盖两个分组');
  assert.equal(result.items.length, 3);
  assert.equal(result.items.every((item) => item.source === 'live'), true, '实时结果优先返回');
  const names = result.items.map((item) => item.name);
  assert.equal(names.includes('池子红烧肉') || names.includes('池子青菜'), false, '实时够用时不得用池子');
  assert.equal(result.degraded, false);
  assert.equal(result.note, undefined, '全部实时时不带降级文案');
  assert.equal(result.quotaRemaining, 99, '只扣实时抽页用掉的 1 次额度');
  // 整页入库：1 页 × 50 道 + 池子里原有 2 道
  assert.equal(store.dishes.size, 52);
});

test('dish.pick 实时失败时用云端池补齐并标注 source=pool', async () => {
  resetState();
  seedDish('红烧肉', 'meat', '猪', 10);
  seedDish('清蒸鱼', 'meat', '鱼', 11);
  seedDish('番茄蛋汤', 'soup', '汤', 12);
  live.page = () => { throw new Error('模拟接口异常'); };

  const result = await handler('dish.pick')(ctx, {
    groups: [{ group: 'meat', count: 2 }, { group: 'soup', count: 1 }],
  });

  assert.equal(result.items.length, 3);
  assert.equal(result.items.every((item) => item.source === 'pool'), true, '实时失败后由池子补齐');
  const meat = result.items.filter((item) => item.group === 'meat').map((item) => item.name);
  assert.equal(meat.length, 2);
  assert.equal(meat.every((name) => ['红烧肉', '清蒸鱼'].includes(name)), true);
  assert.equal(result.degraded, true);
  assert.match(result.note, /缓存|内置/);
  assert.equal(store.dishes.size, 3, '实时失败不得写入池子');
});

test('dish.pick 实时整页 upsert：重复抽到同一页不新增记录，只更新最后出现时间', async () => {
  resetState();
  // 固定返回「第 1 页」内容（allPage=1），验证整页入库与去重
  live.page = (keyword, page, size) => ({ ...mixedPage(keyword, 1, size), allPage: 1 });
  const pick = handler('dish.pick');
  const request = { groups: [{ group: 'meat', count: 2 }] };

  const first = await pick(ctx, request);
  assert.equal(first.items.length, 2);
  assert.equal(first.items.every((item) => item.source === 'live'), true);
  assert.equal(store.dishes.size, 50, '整页 50 道全部入库，而非只写被选中的 2 道');
  const key = dishKey('猪-1-3', '猪/1/3');
  const firstSeen = store.dishes.get(key).first_seen_at;

  clock += 5_000;
  const second = await pick(ctx, request);
  assert.equal(second.items.length, 2);
  assert.equal(store.dishes.size, 50, '同一页重复入库不产生新记录');
  assert.equal(store.dishes.get(key).first_seen_at, firstSeen, 'first_seen_at 保持首次时间');
  assert.equal(store.dishes.get(key).last_seen_at, clock, '已存在只更新最后出现时间');
  const insertCount = sqlLog.filter((entry) => entry.sql.startsWith('INSERT INTO dishes (')).length;
  assert.equal(insertCount, 2, '两次点菜各整批写入一次');
  assert.equal(
    sqlLog.some((entry) => entry.sql.startsWith('DELETE FROM dishes WHERE id IN')),
    true,
    '实时写入后按 10 万上限淘汰'
  );
});

test('dish.pick 校验份数与分组，实时结果按请求的二级分类过滤', async () => {
  resetState();
  // 整页只有荤菜，且一级分类下混排「猪」「牛」两种二级分类
  live.page = (keyword, page, size) => ({
    items: Array.from({ length: size || 50 }, (_, index) => dishOf(
      'meat', 1, index, index % 2 ? '牛' : '猪'
    )),
    allPage: 1,
    allNum: 50,
  });
  const pick = handler('dish.pick');

  await assert.rejects(() => pick(ctx, { groups: [] }), (error) => error.status === 400);
  await assert.rejects(() => pick(ctx, { groups: [{ group: 'meat', count: 5 }] }), (error) => error.status === 400);
  await assert.rejects(() => pick(ctx, { groups: [{ group: 'meat', count: 0 }] }), (error) => error.status === 400);
  await assert.rejects(() => pick(ctx, { groups: [{ group: 'dessert', count: 1 }] }), (error) => error.status === 400);

  const result = await pick(ctx, { groups: [{ group: 'meat', count: 2, subCategory: '牛' }] });
  assert.equal(live.calls.length, 1);
  assert.equal(live.calls[0].args.length, 3, '实时调用是关键词 + 页码 + 条数');
  assert.equal(KEYWORDS.includes(live.calls[0].keyword), true);
  assert.equal(live.calls[0].size, 50);
  assert.equal(live.calls[0].page, 1, '随机页落在 meta 的 allPage 内');
  assert.equal(result.items.length, 2);
  assert.equal(result.items.every((item) => item.level2 === '牛' && item.source === 'live'), true);
  assert.equal(store.dishes.size, 50, '整页 50 道仍全部入库');

  // 随机页码边界：始终落在 [1, allPage] 内
  const { randomPage } = require('./functions/_shared/dishHandlers');
  assert.equal(randomPage(1), 1);
  assert.equal(randomPage(0), 1);
  for (let i = 0; i < 200; i += 1) {
    const page = randomPage(5);
    assert.equal(Number.isInteger(page) && page >= 1 && page <= 5, true, `页码越界：${page}`);
  }
});

test('dish.pick 实时阶段随机取 level2 关键词，最多 3 次且一次请求内不重复', async () => {
  resetState();
  seedTaxonomy(['猪', '蛋类', '汤', '饭']);
  // 始终空页：逼出 3 次调用上限
  live.page = () => ({ items: [], allPage: 3, allNum: 0 });

  const result = await handler('dish.pick')(ctx, { groups: [{ group: 'meat', count: 1 }] });

  assert.equal(live.calls.length, 3, '最多 3 次调用');
  assert.equal(live.calls.every((call) => KEYWORDS.includes(call.keyword)), true, '关键词必须来自 level2 缓存');
  assert.equal(new Set(live.calls.map((call) => call.keyword)).size, 3, '一次请求内不重复同一关键词');
  assert.equal(live.calls.every((call) => call.size === 50), true);
  assert.equal(result.items.length, 1);
});

test('dish.pick 随机页上限取 min(allPage, 20)', async () => {
  resetState();
  seedTaxonomy(['猪']);
  seedCursor({ pages: { 猪: 500 }, allPage: 500 });
  live.page = mixedPage;

  await handler('dish.pick')(ctx, { groups: [{ group: 'meat', count: 1 }] });

  const call = live.calls[0];
  assert.equal(call.keyword, '猪');
  assert.equal(call.page >= 1 && call.page <= 20, true, `随机页应落在 [1,20]：${call.page}`);
});

test('dish.pick 抓完把该关键词最新 allPage 写回 meta，且不动同步游标', async () => {
  resetState();
  seedTaxonomy(['猪']);
  seedCursor({ kwIndex: 2, page: 5, pages: { 猪: 12 }, allPage: 12, allNum: 600 });
  live.page = (keyword, page, size) => ({ ...mixedPage(keyword, page, size), allPage: 37, allNum: 1850 });

  const result = await handler('dish.pick')(ctx, { groups: [{ group: 'meat', count: 1 }] });

  assert.equal(result.items[0].source, 'live');
  const call = live.calls[0];
  assert.equal(call.page >= 1 && call.page <= 12, true, `随机页应落在 [1,12]：${call.page}`);
  const meta = store.meta.get(pool.SYNC_CURSOR_KEY).value;
  assert.equal(meta.pages['猪'], 37, '写回该关键词最新 allPage');
  assert.equal(meta.allPage, 37, '写回供应商最新 allPage');
  assert.equal(meta.allNum, 1850, '写回供应商最新 allNum');
  assert.equal(meta.kwIndex, 2, '点菜不得推进关键词游标');
  assert.equal(meta.page, 5, '点菜不得推进定时抓取页码');
});

test('dish.sync 只用剩余配额、页数与汇总写回 meta，异常返回 ok:false 不抛错', async () => {
  resetState();
  live.page = mixedPage;
  store.quota.set(pool.todayKey(), 85); // 剩余 15，低于保底

  const skipped = await handler('dish.sync')(ctx, { pages: 4 });
  assert.equal(skipped.ok, true);
  assert.equal(skipped.fetched, 0);
  assert.equal(skipped.remaining, 15);
  assert.equal(live.calls.length, 0, '定时任务不得侵占保底额度');

  resetState();
  live.page = mixedPage;
  store.quota.set(pool.todayKey(), 70); // 剩余 30，可用 10 次
  const synced = await handler('dish.sync')(ctx, { pages: 4 });
  assert.equal(synced.ok, true);
  assert.equal(live.calls.length, 4);
  assert.equal(synced.pages, 4);
  assert.equal(synced.fetched, 200);
  assert.equal(synced.upserted, 200);
  assert.equal(synced.inserted, 200);
  assert.equal(synced.allPage, 20);
  assert.equal(synced.allNum, 60);
  assert.equal(synced.remaining, 26);
  assert.equal(synced.keyword, '猪', '一轮内页码未越界时始终用同一个关键词');
  assert.equal(store.dishes.size, 200);
  assert.equal(store.meta.has(pool.LAST_SYNC_KEY), true);
  assert.equal(store.meta.has(pool.SYNC_CURSOR_KEY), true);
  assert.equal(live.calls.every((call) => call.size === 50), true);
  assert.equal(live.calls.every((call) => call.keyword === '猪'), true);

  resetState();
  store.failQuotaSelect = true;
  const failed = await handler('dish.sync')(ctx, { pages: 1 });
  assert.deepEqual(failed, { ok: false }, '定时任务异常不得抛 500');

  // 定时入口同样不抛错
  const { dishSyncInternal } = require('./functions/_shared/dishHandlers');
  const internal = await dishSyncInternal({ pages: 1 });
  assert.equal(internal.ok, false);

  delete process.env.DISH_DAILY_LIMIT;
  delete process.env.DISH_RESERVE;
  delete process.env.SHOWAPI_APP_KEY;
});

test('dishSync 同一关键词内页码递增，抓完最后一页换下一个关键词且页码复位 1', async () => {
  resetState();
  live.page = (keyword, page, size) => ({ ...mixedPage(keyword, page, size), allPage: 2, allNum: 100 });
  seedCursor({ kwIndex: 0, page: 1, pages: {} });

  const first = await handler('dish.sync')(ctx, { pages: 3 });
  assert.deepEqual(
    live.calls.map((call) => `${call.keyword}:${call.page}`),
    ['猪:1', '猪:2', '蛋类:1'],
    '同一关键词内递增，越界后换下一个关键词并复位'
  );
  assert.equal(first.ok, true);
  assert.equal(first.pages, 3);
  assert.equal(first.fetched, 150);
  assert.equal(first.upserted, 150);
  assert.equal(first.keyword, '蛋类');
  assert.equal(first.allPage, 2, 'allPage 是当前关键词的页数');
  let meta = store.meta.get(pool.SYNC_CURSOR_KEY).value;
  assert.equal(meta.kwIndex, 1, '抓完「猪」的最后一页后切到下一个关键词');
  assert.equal(meta.page, 2, '新关键词已抓第 1 页，下一次抓第 2 页');
  assert.deepEqual(meta.pages, { 猪: 2, 蛋类: 2 }, '维护「关键词 → allPage」映射');

  // 第二轮从新关键词的第 2 页继续，再越界就切到第三个关键词
  const second = await handler('dish.sync')(ctx, { pages: 1 });
  assert.deepEqual(
    live.calls.map((call) => `${call.keyword}:${call.page}`),
    ['猪:1', '猪:2', '蛋类:1', '蛋类:2']
  );
  meta = store.meta.get(pool.SYNC_CURSOR_KEY).value;
  assert.equal(second.pages, 1);
  assert.equal(meta.kwIndex, 2);
  assert.equal(meta.page, 1);
});

test('dishSync 空页（未找到相关菜谱）立即换关键词，连续 3 页空结果提前结束', async () => {
  resetState();
  live.page = () => ({ items: [], allPage: 1, allNum: 0, empty: true });
  seedCursor({ kwIndex: 0, page: 3, pages: {}, allPage: 5, allNum: 0 });

  const result = await handler('dish.sync')(ctx, { pages: 5 });

  assert.deepEqual(
    live.calls.map((call) => `${call.keyword}:${call.page}`),
    ['猪:3', '蛋类:1', '汤:1'],
    '空页立即切到下一个关键词并复位页码'
  );
  assert.equal(result.ok, true);
  assert.equal(result.pages, 3);
  assert.equal(result.failed, 0, '空页不是失败，不计失败数');
  assert.equal(result.fetched, 0);
  assert.equal(result.upserted, 0);
  assert.equal(store.quota.get(pool.todayKey()), 3, '只消耗 3 次额度');
  const meta = store.meta.get(pool.SYNC_CURSOR_KEY).value;
  assert.equal(meta.kwIndex, 3);
  assert.equal(meta.page, 1);
});

test('dishSync 单页失败不中断整轮，记录失败页数并跳过该页', async () => {
  resetState();
  live.page = (keyword, page, size) => {
    if (page === 2 && keyword === '猪') throw new Error('模拟单页异常');
    return mixedPage(keyword, page, size);
  };
  seedCursor({ kwIndex: 0, page: 1, pages: { 猪: 20 }, allPage: 20 });

  const result = await handler('dish.sync')(ctx, { pages: 3 });

  assert.equal(result.ok, true);
  assert.deepEqual(live.calls.map((call) => call.page), [1, 2, 3], '失败页不重复刷');
  assert.equal(result.failed, 1);
  assert.equal(result.pages, 2);
  assert.equal(result.fetched, 100);
  assert.equal(result.upserted, 100);
  assert.equal(result.keyword, '猪');
  const meta = store.meta.get(pool.SYNC_CURSOR_KEY).value;
  assert.equal(meta.kwIndex, 0, '未抓完该关键词时不换关键词');
  assert.equal(meta.page, 4, '失败页同样跳过，游标继续前进');
});

test('dishSync 游标兼容旧 { page, allPage } 格式，且关键词分页表最多保留 200 个键', async () => {
  resetState();
  const pages = {};
  for (let i = 0; i < 260; i += 1) pages[`分类${i}`] = 3;
  // 旧格式没有 kwIndex / pages，只有 page / allPage / allNum
  store.meta.set(pool.SYNC_CURSOR_KEY, {
    value: { page: 2, allPage: 6, allNum: 300, pages },
    updated_at: clock,
  });
  live.page = mixedPage;

  const result = await handler('dish.sync')(ctx, { pages: 1 });

  assert.equal(result.ok, true);
  assert.equal(live.calls[0].page, 2, '旧格式的 page 仍被沿用');
  assert.equal(live.calls[0].keyword, '猪', '缺省从第一个关键词开始');
  const meta = store.meta.get(pool.SYNC_CURSOR_KEY).value;
  assert.equal(meta.kwIndex, 0);
  assert.equal(meta.page, 3);
  assert.equal(meta.pages['猪'], 20, '记录本次关键词的 allPage');
  assert.equal(Object.keys(meta.pages).length <= 200, true, '分页映射不得无限膨胀');
});
