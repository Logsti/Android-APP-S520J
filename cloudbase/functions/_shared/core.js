/**
 * S520J 云函数公共核心：数据库访问、错误、加密与会话校验。
 *
 * 数据访问方式：CloudBase PG 模式的云函数运行时不会注入 PGHOST/PGPASSWORD，
 * 实例端口也不对函数开放，因此统一通过 CloudBase Gateway 的 exec-pgsql 接口执行 SQL。
 * 该接口要求携带服务端 API Key（仅存在于云函数环境变量 CB_API_KEY，绝不下发客户端），
 * 且 DDL/DML 必须显式指定管理角色，否则会落到只读事务。
 */
const crypto = require('crypto');

const ENV_ID = process.env.CB_ENV_ID || 'lin-run-forward-d3fjemwrbf5f4738';
const API_KEY = process.env.CB_API_KEY || '';
const PG_ROLE = process.env.CB_PG_ROLE || 'cloudbase_postgres';
const GATEWAY_URL = `https://${ENV_ID}.api.tcloudbasegateway.com/v1/rdb/exec-pgsql`;
const TIMEOUT_MS = 8_000;

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const badRequest = (msg) => new ApiError(400, msg);
const unauthorized = (msg = '登录已失效，请重新登录') => new ApiError(401, msg);
const forbidden = (msg = '没有权限执行该操作') => new ApiError(403, msg);

/** 执行单条 SQL（exec-pgsql 不支持多语句），返回 { rows }。 */
async function q(text, params) {
  if (!API_KEY) {
    console.error('[s520j] CB_API_KEY 未配置');
    throw new ApiError(500, '服务暂时不可用，请稍后重试');
  }
  let res;
  try {
    res = await fetch(GATEWAY_URL, {
      method: 'POST',
      headers: { Authorization: `Bearer ${API_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ sql: text, parameters: params || [], role: PG_ROLE }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (e) {
    console.error('[s520j] exec-pgsql 请求失败', e && e.message);
    throw new ApiError(503, '服务暂时不可用，请稍后重试');
  }
  const raw = await res.text();
  if (!res.ok) {
    let code = '';
    let message = '';
    try {
      const parsed = JSON.parse(raw);
      code = parsed.code || '';
      message = parsed.message || '';
    } catch (_) { /* ignore */ }
    console.error('[s520j] exec-pgsql 执行失败', res.status, code, message);
    // 唯一键冲突属于可预期的业务分支，交由上层按 400 处理。
    if (code === 'DATABASE_23505') throw new ApiError(409, '数据已存在，请刷新后重试');
    throw new ApiError(500, '服务暂时不可用，请稍后重试');
  }
  if (!raw) return { rows: [] };
  let data;
  try {
    data = JSON.parse(raw);
  } catch (_) {
    return { rows: [] };
  }
  return { rows: Array.isArray(data) ? data : [] };
}

/**
 * 事务兼容层：exec-pgsql 每次只接受一条语句，无法提供 BEGIN/COMMIT。
 * 这里保持与原调用相同的 `c.query` 形态，语句按顺序执行；失败时记录日志供补偿。
 */
async function withTx(fn) {
  const client = { query: (text, params) => q(text, params) };
  try {
    return await fn(client);
  } catch (e) {
    console.error('[s520j] 顺序执行中途失败，可能留下部分写入', e && e.message);
    throw e;
  }
}

const uuid = () => crypto.randomUUID();
const sha256 = (s) => crypto.createHash('sha256').update(String(s)).digest('hex');
const randomToken = () => crypto.randomBytes(32).toString('hex');

function hashPassword(password, salt) {
  return crypto.pbkdf2Sync(String(password), salt, 10_000, 32, 'sha256').toString('hex');
}

const ACCOUNT_RULE = /^[A-Za-z0-9]{8,20}$/;
const PASSWORD_RULE = /^[A-Za-z0-9]{8,32}$/;

function requireAccountId(v) {
  const s = String(v || '').trim().toUpperCase();
  if (!ACCOUNT_RULE.test(s)) throw badRequest('账号 ID 需为 8–20 位字母或数字');
  return s;
}

function requirePassword(v) {
  const s = String(v || '');
  if (!PASSWORD_RULE.test(s)) throw badRequest('密码只能包含字母和数字，长度 8–32 位');
  return s;
}

function requireNickname(v) {
  const s = String(v || '').trim();
  if (!s || s.length > 20) throw badRequest('昵称不能为空且不超过 20 个字');
  return s;
}

const now = () => Date.now();

const ACCESS_TTL = 2 * 60 * 60 * 1000;          // 2 小时
const REFRESH_TTL = 30 * 24 * 60 * 60 * 1000;   // 30 天
const RECYCLE_TTL = 30 * 24 * 60 * 60 * 1000;   // 30 天

function publicUser(row) {
  return {
    id: row.id,
    nickname: row.nickname,
    accountId: row.account_id,
    avatar: row.avatar,
    coupleStartAt: Number(row.couple_start_at || 0),
    createdAt: Number(row.created_at || 0),
  };
}

/** 校验 access token，返回 { session, user }。 */
async function requireSession(token) {
  if (!token) throw unauthorized('缺少登录凭证');
  const hash = sha256(token);
  const { rows } = await q(
    `SELECT s.id AS session_id, s.expires_at, s.revoked, u.*
       FROM auth_sessions s JOIN users u ON u.id = s.user_id
      WHERE s.access_token_hash = $1
      LIMIT 1`,
    [hash]
  );
  if (!rows.length) throw unauthorized();
  const row = rows[0];
  if (row.revoked) throw unauthorized();
  if (Number(row.expires_at) <= now()) throw unauthorized('登录已过期，请重新登录');
  return { sessionId: row.session_id, user: row };
}

async function issueSession(user, deviceInfo) {
  const accessToken = randomToken();
  const refreshToken = randomToken();
  const ts = now();
  await q(
    `INSERT INTO auth_sessions
       (id, user_id, access_token_hash, refresh_token_hash, device_info, expires_at, refresh_expires_at, revoked, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,false,$8,$8)`,
    [uuid(), user.id, sha256(accessToken), sha256(refreshToken), deviceInfo || '', ts + ACCESS_TTL, ts + REFRESH_TTL, ts]
  );
  return { accessToken, refreshToken };
}

async function assertMember(userId, spaceId, opts = {}) {
  if (!spaceId) throw forbidden('该记录不属于任何空间');
  const { rows } = await q(
    `SELECT m.id, m.role, s.status FROM space_members m JOIN spaces s ON s.id = m.space_id
      WHERE m.space_id = $1 AND m.user_id = $2 AND m.deleted_at IS NULL AND s.deleted_at IS NULL
      LIMIT 1`,
    [spaceId, userId]
  );
  if (!rows.length) throw forbidden('只有共同空间成员可访问');
  if (opts.requireActive && rows[0].status !== 'active') throw forbidden('共同空间已冻结归档，仅可查看与导出');
  return rows[0];
}

async function spaceOfUser(userId) {
  const { rows } = await q(
    `SELECT s.* FROM space_members m JOIN spaces s ON s.id = m.space_id
      WHERE m.user_id = $1 AND m.deleted_at IS NULL AND s.deleted_at IS NULL
      ORDER BY s.created_at ASC LIMIT 1`,
    [userId]
  );
  return rows[0] || null;
}

/** 可见性判定：private 仅本人；couple 需空间成员。 */
async function canRead(row, userId) {
  if (row.owner_id === userId) return true;
  if (row.visibility !== 'couple' || !row.space_id) return false;
  try { await assertMember(userId, row.space_id); return true; } catch (_) { return false; }
}

module.exports = {
  q, withTx,
  ApiError, badRequest, unauthorized, forbidden,
  uuid, sha256, randomToken, hashPassword,
  requireAccountId, requirePassword, requireNickname,
  now, ACCESS_TTL, REFRESH_TTL, RECYCLE_TTL,
  publicUser, requireSession, issueSession, assertMember, spaceOfUser, canRead,
};
