/**
 * ShowAPI「菜谱大全」客户端（1164-1 菜谱查询 / 1164-2 分类树）。
 *
 * 约定：
 *  - 1164-1 必须带 cpName 关键词（只传 page / maxResults 会返回「未找到相关菜谱」），
 *    分类只能靠记录自带的标签推断，因此按二级分类名当关键词轮询；
 *  - 1164-2 返回 `一级 -> {二级: [三级...]}` 映射，也可能包在 list/children 数组里；
 *  - AppKey 只从环境变量 SHOWAPI_APP_KEY 读取，绝不下发客户端、不写入日志；
 *  - 串行请求且相邻调用间隔 ≥1 秒，避免触发供应商限流；
 *  - 供应商错误统一折算成受控 ApiError，不把原始响应或堆栈抛给上层。
 */
const { ApiError } = require('./core');
const { classifyDish } = require('./dishTaxonomy');

const BASE_URL = 'https://route.showapi.com';
const TIMEOUT_MS = 8_000;
const MIN_GAP_MS = 1_000;
const MAX_RESULTS = 50;

let lastCallAt = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const normalize = (value) => String(value == null ? '' : value).replace(/\s+/g, ' ').trim();

function apiKey() {
  const key = String(process.env.SHOWAPI_APP_KEY || '').trim();
  if (!key) throw new ApiError(503, '菜谱服务未配置');
  return key;
}

function firstArray(...values) {
  for (const value of values) {
    if (Array.isArray(value) && value.length) return value;
  }
  return null;
}

function nodeName(node) {
  if (typeof node === 'string') return normalize(node);
  if (!node || typeof node !== 'object') return '';
  return normalize(node.name || node.className || node.cname || node.title || node.type || '');
}

function nodeChildren(node) {
  return firstArray(
    node.list, node.childList, node.children, node.subList,
    node.childs, node.sub, node.data, node.datas, node.typeList
  );
}

// 映射结构里可能混入的业务元字段，不是分类名。
const MAPPING_META_KEYS = new Set(['flag', 'ret_code', 'remark', 'msg', 'data', 'datas', 'list', 'tree', 'categories', 'result']);

/**
 * 解析 1164-2 分类树。
 *
 * 实测返回是映射：`{ 一级: { 二级: [三级...] } }`；同时兼容
 * `{ 一级: [二级...] }` 与旧的 `{ list: [{ name, list/childList }] }` 数组结构。
 *
 * @returns {{ level1: Object<string, string[]>, level2: string[] } | {}}
 *   level1：一级 → 二级列表；level2：扁平关键词（映射结构下取最深一层名字），
 *   去重并按稳定顺序排序，供 1164-1 的 cpName 轮询使用。结构不符时返回 {}。
 */
function parseTaxonomy(raw) {
  let root = raw;
  if (typeof root === 'string') {
    try { root = JSON.parse(root); } catch (_) { return {}; }
  }
  if (!root || typeof root !== 'object') return {};

  const level1 = {};
  const flat = [];
  const addFlat = (value) => {
    const name = normalize(value);
    if (name && !flat.includes(name)) flat.push(name);
  };

  const list = Array.isArray(root)
    ? root
    : firstArray(root.list, root.data, root.datas, root.tree, root.categories, root.result);

  if (list) {
    // 数组结构：[{ name, list/childList: [...] }]
    for (const node of list) {
      if (!node || typeof node !== 'object') continue;
      const name = nodeName(node);
      if (!name) continue;
      const children = nodeChildren(node);
      if (!children) continue;
      const level2List = [];
      for (const child of children) {
        const level2 = nodeName(child);
        if (level2 && !level2List.includes(level2)) level2List.push(level2);
      }
      if (!level2List.length) continue;
      level1[name] = level2List;
      for (const child of children) {
        const grandchildren = child && typeof child === 'object' ? nodeChildren(child) : null;
        if (grandchildren) {
          for (const leaf of grandchildren) addFlat(nodeName(leaf));
        } else {
          addFlat(nodeName(child));
        }
      }
    }
  } else {
    // 映射结构：{ 一级: { 二级: [三级...] } } 或 { 一级: [二级...] }
    for (const [rawName, value] of Object.entries(root)) {
      if (MAPPING_META_KEYS.has(rawName)) continue;
      const name = normalize(rawName);
      if (!name) continue;
      const level2List = [];
      if (Array.isArray(value)) {
        for (const item of value) {
          const level2 = nodeName(item);
          if (!level2) continue;
          if (!level2List.includes(level2)) level2List.push(level2);
          addFlat(level2);
        }
      } else if (value && typeof value === 'object') {
        for (const [rawSub, leaves] of Object.entries(value)) {
          const level2 = normalize(rawSub);
          if (!level2) continue;
          if (!level2List.includes(level2)) level2List.push(level2);
          const nested = Array.isArray(leaves)
            ? leaves
            : firstArray(leaves && leaves.list, leaves && leaves.childList, leaves && leaves.children);
          if (nested) {
            for (const leaf of nested) addFlat(nodeName(leaf));
          } else {
            addFlat(level2);
          }
        }
      }
      if (level2List.length) level1[name] = level2List;
    }
  }

  if (!Object.keys(level1).length) return {};
  flat.sort();
  return { level1, level2: flat };
}

/** 菜记录列表：showapi_res_body 可能直接是 datas，也可能嵌在 data 里。 */
function dishRecords(body) {
  if (!body || typeof body !== 'object') return [];
  if (Array.isArray(body)) return body;
  if (Array.isArray(body.datas)) return body.datas;
  if (body.data) {
    if (Array.isArray(body.data)) return body.data;
    if (Array.isArray(body.data.datas)) return body.data.datas;
    if (Array.isArray(body.data.list)) return body.data.list;
  }
  if (Array.isArray(body.list)) return body.list;
  return [];
}

/** 分类路径：type/type_v1/type_v2/type_v3 依次拆标签，保序去重后用 / 连接。 */
function classifyPath(typeFields) {
  const fields = Array.isArray(typeFields)
    ? typeFields
    : [typeFields.type, typeFields.type_v1, typeFields.type_v2, typeFields.type_v3];
  const tags = [];
  for (const field of fields) {
    for (const raw of normalize(field).split(' ')) {
      const tag = raw.trim();
      if (tag && !tags.includes(tag)) tags.push(tag);
    }
  }
  return tags.join('/');
}

/** 供应商菜记录 → { name, level1, level2, group, path }；无菜名返回 null。 */
function normalizeDish(record) {
  if (!record || typeof record !== 'object') return null;
  const name = normalize(record.cpName || record.name || '');
  if (!name) return null;
  const typeFields = [record.type, record.type_v1, record.type_v2, record.type_v3];
  const classified = classifyDish(typeFields);
  return {
    name,
    level1: classified.level1 || '',
    level2: classified.level2 || '',
    group: classified.group,
    path: classifyPath(typeFields),
  };
}

/**
 * 解析 1164-1 响应体，返回 { items, allPage, allNum }。
 * 关键词查询的每条记录自带 type_v1/type_v2/type_v3，分类仍由 normalizeDish 推断。
 */
function parseDishPage(body) {
  const items = [];
  const seen = new Set();
  for (const record of dishRecords(body)) {
    const dish = normalizeDish(record);
    if (!dish) continue;
    const key = `${dish.name}\u0000${dish.path}`;
    if (seen.has(key)) continue;
    seen.add(key);
    items.push(dish);
  }
  const source = body && typeof body === 'object' ? body : {};
  const allPage = Math.max(1, Math.floor(Number(source.allPage ?? source.all_page ?? source.pages) || 1));
  const allNum = Math.max(0, Math.floor(Number(source.allNum ?? source.all_num ?? source.total) || 0));
  return { items, allPage, allNum };
}

/**
 * 供应商的「没有这条数据」文案：1164-1 在关键词搜不到时返回
 * `{ flag: false, ret_code: -1, remark: '未找到相关菜谱！' }`，属于空结果而非故障。
 */
function isNotFound(source) {
  const text = `${normalize(source && source.remark)} ${normalize(source && source.msg)}`.trim();
  return Boolean(text) && /未找到|没有找到|无相关|暂无/.test(text);
}

/** 统一请求：返回 { body, notFound }；notFound 表示业务层「查无数据」，由调用方当空页处理。 */
async function post(endpoint, params) {
  const key = apiKey();
  const gap = lastCallAt + MIN_GAP_MS - Date.now();
  if (gap > 0) await sleep(gap);
  lastCallAt = Date.now();

  let res;
  try {
    res = await fetch(`${BASE_URL}/${endpoint}?appKey=${encodeURIComponent(key)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(params).toString(),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (e) {
    // 只记录错误类型，不输出请求 URL（内含 AppKey）或供应商响应。
    console.error('[s520j][dish] 菜谱接口请求失败', endpoint, e && e.name);
    throw new ApiError(503, '菜谱服务暂时不可用');
  }
  if (!res.ok) {
    console.error('[s520j][dish] 菜谱接口状态异常', endpoint, res.status);
    throw new ApiError(503, '菜谱服务暂时不可用');
  }

  let data;
  try {
    data = await res.json();
  } catch (_) {
    console.error('[s520j][dish] 菜谱接口响应无法解析', endpoint);
    throw new ApiError(503, '菜谱服务暂时不可用');
  }
  const code = Number(data && data.showapi_res_code);
  if (!data || code !== 0) {
    // 只记录供应商错误码与错误文案（不含请求 URL / AppKey），便于定位未开通、超额等问题。
    const reason = String((data && data.showapi_res_error) || '').slice(0, 160);
    console.error('[s520j][dish] 菜谱接口返回失败', endpoint, Number.isFinite(code) ? code : 'unknown', reason);
    throw new ApiError(503, '菜谱服务暂时不可用');
  }
  let body = data.showapi_res_body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (_) { body = null; }
  }
  if (!body || typeof body !== 'object') {
    console.error('[s520j][dish] 菜谱接口响应结构异常', endpoint);
    throw new ApiError(503, '菜谱服务暂时不可用');
  }
  // 部分接口不返回 flag / ret_code，缺失视为成功，明确失败才降级。
  if (body.flag === false || (body.ret_code != null && String(body.ret_code) !== '0')) {
    // 「未找到相关菜谱」是空结果而非错误：交给调用方按空页处理，不计失败、不打 error 日志。
    if (isNotFound(body)) return { body, notFound: true };
    console.error(
      '[s520j][dish] 菜谱业务失败', endpoint,
      'ret_code=' + String(body.ret_code),
      'remark=' + String(body.remark || body.msg || '').slice(0, 160)
    );
    throw new ApiError(503, '菜谱服务暂时不可用');
  }
  return { body, notFound: false };
}

/** 拉取分类树；失败抛受控错误，由上层决定沿用旧缓存或用内置兜底表。 */
async function fetchTaxonomy() {
  const result = await post('1164-2', {});
  const parsed = parseTaxonomy(result.body);
  if (!Object.keys(parsed.level1 || {}).length) {
    console.error('[s520j][dish] 分类树为空', JSON.stringify(result.body).slice(0, 240));
    throw new ApiError(503, '菜谱分类暂时不可用');
  }
  return { level1: parsed.level1, level2: parsed.level2 };
}

/**
 * 按关键词抽一页菜谱（1164-1）。
 * 供应商必须带 cpName，只传 page / maxResults 会直接返回「未找到相关菜谱」。
 * @param {string} keyword 二级分类名（cpName）
 * @param {number} page 页码
 * @param {number} maxResults 每页条数，上限 50
 */
async function fetchPage(keyword, page, maxResults = MAX_RESULTS) {
  const term = normalize(keyword);
  if (!term) throw new ApiError(400, '缺少菜谱关键词');
  const pageNo = Math.max(1, Math.floor(Number(page) || 1));
  const size = Math.min(Math.max(1, Math.floor(Number(maxResults) || MAX_RESULTS)), MAX_RESULTS);
  const result = await post('1164-1', { cpName: term, page: pageNo, maxResults: size });
  if (result.notFound) {
    console.log('[s520j][dish] 关键词无菜谱', term.slice(0, 20), 'page=' + pageNo);
    return { items: [], allPage: 1, allNum: 0, empty: true };
  }
  return parseDishPage(result.body);
}

/** 仅供测试：重置串行节流时间戳。 */
function __resetThrottle() {
  lastCallAt = 0;
}

module.exports = {
  fetchTaxonomy,
  fetchPage,
  parseTaxonomy,
  parseDishPage,
  dishRecords,
  normalizeDish,
  classifyPath,
  __resetThrottle,
  MIN_GAP_MS,
  TIMEOUT_MS,
  MAX_RESULTS,
};
