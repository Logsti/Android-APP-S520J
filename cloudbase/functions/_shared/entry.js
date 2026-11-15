/**
 * S520J 云函数统一入口。
 * 支持三种调用形态：
 *   1) 云函数直接调用：event = { action, payload, token }
 *   2) HTTP 触发（JSON body）：event.body = '{"action":...}'
 *   3) HTTP 触发（Authorization 头携带 access token）
 */
const { dispatch } = require('./handlers');

function parseBody(event) {
  if (!event) return {};
  if (typeof event === 'string') {
    try { return JSON.parse(event); } catch (_) { return { action: event }; }
  }
  let raw = event.body;
  if (typeof raw === 'string') {
    if (event.isBase64Encoded) raw = Buffer.from(raw, 'base64').toString('utf8');
    try { return JSON.parse(raw); } catch (_) { return {}; }
  }
  if (raw && typeof raw === 'object') return raw;
  return event;
}

function readToken(event, body) {
  if (body.token) return String(body.token);
  if (event && event.token) return String(event.token);
  const headers = (event && event.headers) || {};
  const auth = headers.Authorization || headers.authorization || '';
  return auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
}

/**
 * @param {string|string[]} group 允许的接口前缀；传多个时任一匹配即可
 *   （auth 函数同时承载 auth.* 与 users.* 账号资料类接口）。
 */
function createEntry(group) {
  const groups = (Array.isArray(group) ? group : [group]).filter(Boolean);
  return async function main(event, context) {
    const body = parseBody(event);
    const action = String(body.action || '');
    if (groups.length && !groups.some((g) => action.startsWith(g + '.'))) {
      return { code: 404, message: '接口不存在' };
    }
    try {
      const data = await dispatch(action, body.payload || {}, readToken(event, body));
      return { code: 0, message: 'ok', data };
    } catch (e) {
      if (e && e.status) return { code: e.status, message: e.message };
      console.error('[s520j] unhandled', action, e);
      return { code: 500, message: '服务暂时不可用，请稍后重试' };
    }
  };
}

module.exports = { createEntry };
