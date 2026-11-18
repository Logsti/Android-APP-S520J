/**
 * 菜谱分类：ShowAPI「1164-2」官方一级/二级分类树，以及二级分类 → 四组的确定性映射。
 *
 * 映射只依赖接口返回的分类标签，不按菜名、口味或食材猜测；
 * 同一道菜带多组标签时按「汤 ＞ 荤菜 ＞ 素菜 ＞ 主食」只取一组。
 */

// 官方分类树兜底表：接口不可用时保证点菜仍可用。
const BUILTIN_TAXONOMY = {
  肉类: ['猪', '牛', '羊', '鸡', '鸭', '鹅', '鸽子', '鹌鹑', '火鸡', '兔肉', '驴肉', '鹿肉', '牛蛙', '田鸡', '雪蛤'],
  水产: ['鱼', '虾', '螃蟹', '贝', '其他水产'],
  蔬菜水果: ['叶类蔬菜', '根茎蔬菜', '果实类蔬菜', '菌菇类', '花朵植物类', '水果'],
  蛋奶豆制品: ['蛋类', '豆制品', '奶制品'],
  汤粥主食: ['饼', '馒头', '馄饨', '粥', '面条', '饭', '汤羹', '饺子', '汤', '包子', '三明治'],
  米面干果腌咸: ['干果类', '米面类', '腌咸制品'],
  烘焙甜品饮料: ['甜品', '饮品', '烘焙'],
  热门专题: ['特色食品', '菜式', '特殊场合', '视频专题', '功效', '人群'],
  口味特色: ['异国风味', '口味', '器具', '烹调方法'],
};

// 输出顺序即客户端展示顺序。
const GROUP_ORDER = ['meat', 'veg', 'soup', 'staple'];
// 冲突时的取组优先级：汤 ＞ 荤 ＞ 素 ＞ 主食。
const GROUP_PRIORITY = ['soup', 'meat', 'veg', 'staple'];

const SOUP_TAGS = new Set(['汤', '汤羹']);
const MEAT_LEVEL1 = new Set(['肉类', '水产']);
const VEG_LEVEL1 = new Set(['蔬菜水果', '蛋奶豆制品']);
const STAPLE_TAGS = new Set(['米面类', '饼', '馒头', '馄饨', '粥', '面条', '饭', '饺子', '包子', '三明治']);

const LEVEL1_NAMES = new Set(Object.keys(BUILTIN_TAXONOMY));
const LEVEL2_LEVEL1 = new Map();
for (const [level1, level2List] of Object.entries(BUILTIN_TAXONOMY)) {
  for (const level2 of level2List) LEVEL2_LEVEL1.set(level2, level1);
}

/**
 * 二级分类 → 四组；不属于四组（烘焙甜品饮料、干果类、腌咸制品、热门专题等）返回 null。
 * @param {string} level2 官方二级分类名
 * @returns {'meat'|'veg'|'soup'|'staple'|null}
 */
function level2ToGroup(level2) {
  const name = String(level2 == null ? '' : level2).trim();
  if (!name) return null;
  if (SOUP_TAGS.has(name)) return 'soup';
  if (STAPLE_TAGS.has(name)) return 'staple';
  const level1 = LEVEL2_LEVEL1.get(name);
  if (!level1) return null;
  if (MEAT_LEVEL1.has(level1)) return 'meat';
  if (VEG_LEVEL1.has(level1)) return 'veg';
  // 「汤粥主食」下未列入主食清单的二级（如包子已在清单内）不参与随机。
  return null;
}

/** 把 type / type_v1 / type_v2 / type_v3 拆成去空白后的标签数组。 */
function splitTags(typeFields) {
  if (typeFields == null) return [];
  const fields = Array.isArray(typeFields)
    ? typeFields
    : [typeFields.type, typeFields.type_v1, typeFields.type_v2, typeFields.type_v3];
  const tags = [];
  for (const field of fields) {
    if (field == null) continue;
    for (const raw of String(field).split(/[\s\u3000]+/)) {
      const tag = raw.trim();
      if (tag) tags.push(tag);
    }
  }
  return tags;
}

/**
 * 判断一道菜属于哪一组：逐标签查表，按优先级取一组，全部不命中返回 null。
 * @param {object|string[]} typeFields 供应商菜记录，或已拆好的标签数组
 */
function groupForDish(typeFields) {
  const hit = new Set();
  for (const tag of splitTags(typeFields)) {
    const group = level2ToGroup(tag);
    if (group) hit.add(group);
  }
  for (const group of GROUP_PRIORITY) {
    if (hit.has(group)) return group;
  }
  return null;
}

/**
 * 归一化一道菜的分类：返回命中优先级最高的一组及其二级分类与所属一级分类。
 * 一组都没命中时 group 为 null，调用方应跳过入库/随机。
 */
function classifyDish(typeFields) {
  const tags = splitTags(typeFields);
  const candidates = [];
  for (const tag of tags) {
    const group = level2ToGroup(tag);
    if (!group) continue;
    candidates.push({ group, level2: tag, level1: LEVEL2_LEVEL1.get(tag) || '' });
  }
  for (const group of GROUP_PRIORITY) {
    const hit = candidates.find((item) => item.group === group);
    if (hit) return hit;
  }
  const level1 = tags.find((tag) => LEVEL1_NAMES.has(tag)) || '';
  return { group: null, level1, level2: '' };
}

/** 分类树（或内置表）→ 四组可用二级分类列表，过滤空数组。 */
function groupsFromTaxonomy(tree) {
  const level1Map = tree && tree.level1 && typeof tree.level1 === 'object'
    ? tree.level1
    : BUILTIN_TAXONOMY;
  const result = {};
  for (const level2List of Object.values(level1Map)) {
    if (!Array.isArray(level2List)) continue;
    for (const raw of level2List) {
      const level2 = String(raw == null ? '' : raw).trim();
      const group = level2ToGroup(level2);
      if (!group) continue;
      if (!result[group]) result[group] = [];
      if (!result[group].includes(level2)) result[group].push(level2);
    }
  }
  // 汤的筛选项固定「汤」在前、「汤羹」在后，与界面文案一致。
  if (result.soup && result.soup.length > 1) {
    result.soup.sort((a, b) => (a === '汤' ? 0 : 1) - (b === '汤' ? 0 : 1));
  }
  return result;
}

const BUILTIN_GROUPS = groupsFromTaxonomy({ level1: BUILTIN_TAXONOMY });

module.exports = {
  BUILTIN_TAXONOMY,
  BUILTIN_GROUPS,
  GROUP_ORDER,
  GROUP_PRIORITY,
  LEVEL2_LEVEL1,
  level2ToGroup,
  groupForDish,
  classifyDish,
  splitTags,
  groupsFromTaxonomy,
};
