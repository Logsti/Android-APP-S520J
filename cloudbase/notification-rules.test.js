const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');

let active;
let sequence = 0;
let clock = 1_700_000_000_000;

function normalize(text) {
  return String(text).replace(/\s+/g, ' ').trim();
}

function rowMembers(spaceId) {
  return active.members.filter((m) => m.space_id === spaceId && m.deleted_at == null);
}

async function query(text, params = []) {
  const sql = normalize(text);

  if (sql.startsWith('SELECT s.* FROM space_members m JOIN spaces s ON s.id = m.space_id')) {
    const [userId] = params;
    const member = active.members.find((m) => m.user_id === userId && m.deleted_at == null);
    const space = member && active.spaces.get(member.space_id);
    return { rows: space && space.deleted_at == null ? [space] : [] };
  }
  if (sql.startsWith('SELECT m.id, m.role, s.status FROM space_members')) {
    const [spaceId, userId] = params;
    const member = rowMembers(spaceId).find((m) => m.user_id === userId);
    const space = active.spaces.get(spaceId);
    return { rows: member && space ? [{ id: member.id, role: member.role, status: space.status }] : [] };
  }
  if (sql.startsWith('SELECT user_id FROM space_members WHERE space_id = $1 AND user_id <> $2')) {
    const [spaceId, actorId] = params;
    return { rows: rowMembers(spaceId).filter((m) => m.user_id !== actorId).map((m) => ({ user_id: m.user_id })) };
  }
  if (sql.startsWith('SELECT COUNT(*)::int AS n FROM space_members')) {
    return { rows: [{ n: rowMembers(params[0]).length }] };
  }

  if (sql.startsWith('SELECT * FROM contents WHERE id = $1')) {
    const row = active.contents.get(params[0]);
    return { rows: row ? [structuredClone(row)] : [] };
  }
  if (sql.startsWith('INSERT INTO contents')) {
    const row = {
      id: params[0], module: params[1], space_id: params[2], owner_id: params[3], owner_name: params[4],
      visibility: params[5], title: params[6], content: params[7], event_date: params[8],
      media_ids: JSON.parse(params[9]), media_refs: JSON.parse(params[10]), extra: JSON.parse(params[11]),
      created_at: params[12], updated_at: params[12], deleted_at: null, version: 1,
    };
    active.contents.set(row.id, row);
    return { rows: [row] };
  }
  if (sql.startsWith('UPDATE contents SET')) {
    const row = active.contents.get(params[0]);
    const setPart = sql.slice('UPDATE contents SET '.length, sql.indexOf(' WHERE id = $1'));
    for (const assignment of setPart.split(', ')) {
      const match = assignment.match(/^(\w+) = \$(\d+)$/);
      if (!match) continue;
      const [, column, rawIndex] = match;
      const value = params[Number(rawIndex) - 1];
      if (column === 'extra' || column === 'media_ids' || column === 'media_refs') row[column] = JSON.parse(value);
      else row[column] = value;
    }
    row.version += 1;
    return { rows: [row] };
  }
  if (sql.startsWith('INSERT INTO conflict_snapshots')) return { rows: [] };

  if (sql.startsWith('SELECT c.*, s.created_by, s.name AS space_name')) {
    const code = active.bindCode;
    const space = active.spaces.get(code.space_id);
    return { rows: [{ ...code, created_by: space.created_by, space_name: space.name, space_intro: space.intro, space_status: space.status }] };
  }
  if (sql.startsWith('SELECT * FROM bind_requests WHERE space_id = $1 AND from_user = $2 AND status =')) {
    const [spaceId, fromUser, status] = params;
    return { rows: [...active.requests.values()].filter((r) => r.space_id === spaceId && r.from_user === fromUser && r.status === status) };
  }
  if (sql.startsWith('SELECT * FROM bind_requests WHERE id = $1')) {
    const row = active.requests.get(params[0]);
    return { rows: row ? [row] : [] };
  }
  if (sql.startsWith('SELECT * FROM bind_requests WHERE space_id = $1 AND status =')) {
    const [spaceId] = params;
    return { rows: [...active.requests.values()].filter((r) => r.space_id === spaceId && r.status === 'unbind_pending') };
  }
  if (sql.startsWith('INSERT INTO bind_requests')) {
    const row = {
      id: params[0], space_id: params[1], from_user: params[2], to_user: params[3],
      status: sql.includes("'unbind_pending'") ? 'unbind_pending' : 'pending',
      confirmed_by_a: null, confirmed_by_b: null, created_at: params[4], updated_at: params[4], version: 1,
    };
    active.requests.set(row.id, row);
    return { rows: [row] };
  }
  if (sql.startsWith('UPDATE bind_requests SET confirmed_by_a')) {
    const row = active.requests.get(params[0]);
    row.confirmed_by_a = params[1];
    row.confirmed_by_b = params[2];
    if (sql.includes("status = $4")) {
      row.status = params[3];
      row.updated_at = params[4];
    } else {
      if (sql.includes("status = 'confirmed'")) row.status = 'confirmed';
      row.updated_at = params[3];
    }
    return { rows: [row] };
  }
  if (sql.startsWith('INSERT INTO space_members')) {
    const [id, spaceId, userId, , joinedAt] = params;
    if (!rowMembers(spaceId).some((m) => m.user_id === userId)) {
      active.members.push({ id, space_id: spaceId, user_id: userId, role: 'partner', joined_at: joinedAt, deleted_at: null });
    }
    return { rows: [] };
  }
  if (sql.startsWith('UPDATE bind_codes SET')) return { rows: [] };
  if (sql.startsWith('UPDATE spaces SET name =')) {
    const row = active.spaces.get(params[0]);
    row.name = params[1];
    row.intro = params[2];
    row.updated_at = params[3];
    row.version += 1;
    return { rows: [row] };
  }
  if (sql.startsWith("UPDATE spaces SET status = 'frozen'")) {
    const row = active.spaces.get(params[0]);
    row.status = 'frozen';
    row.updated_at = params[1];
    row.version += 1;
    return { rows: [row] };
  }

  if (sql.startsWith('INSERT INTO notifications')) {
    const [id, userId, spaceId, type, title, body, createdAt] = params;
    if (!active.notifications.has(id)) {
      active.notifications.set(id, {
        id, user_id: userId, space_id: spaceId, type, title, body,
        read: false, created_at: createdAt, updated_at: createdAt, deleted_at: null, version: 1,
      });
    }
    return { rows: [] };
  }
  if (sql.startsWith('SELECT * FROM notifications WHERE user_id = $1')) {
    return { rows: [...active.notifications.values()].filter((n) => n.user_id === params[0] && n.deleted_at == null) };
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
  uuid: () => `generated-${++sequence}`,
  sha256: (value) => `hash-${value}`,
  randomToken: () => 'token',
  hashPassword: () => 'password',
  requireAccountId: (value) => value,
  requirePassword: (value) => value,
  requireNickname: (value) => value,
  now: () => ++clock,
  RECYCLE_TTL: 30 * 24 * 60 * 60 * 1000,
  publicUser: (value) => value,
  requireSession: async () => { throw new Error('测试直接调用 handler'); },
  issueSession: async () => ({}),
  assertMember: async (userId, spaceId, options = {}) => {
    const member = rowMembers(spaceId).find((m) => m.user_id === userId);
    if (!member) throw Object.assign(new Error('只有共同空间成员可访问'), { status: 403 });
    if (options.requireActive && active.spaces.get(spaceId).status !== 'active') {
      throw Object.assign(new Error('共同空间已冻结归档，仅可查看与导出'), { status: 403 });
    }
    return member;
  },
  spaceOfUser: async (userId) => {
    const member = active.members.find((m) => m.user_id === userId && m.deleted_at == null);
    return member ? active.spaces.get(member.space_id) : null;
  },
  canRead: async (row, userId) => row.owner_id === userId || row.visibility === 'couple',
};
require.cache[corePath] = coreStub;
const { actions } = require('./functions/_shared/handlers');

function fixture({ partnered = false } = {}) {
  active = {
    contents: new Map(), notifications: new Map(), requests: new Map(),
    spaces: new Map([['space-1', {
      id: 'space-1', name: '我们的空间', intro: '', status: 'active', created_by: 'user-a',
      created_at: 1, updated_at: 1, deleted_at: null, version: 1,
    }]]),
    members: [
      { id: 'member-a', space_id: 'space-1', user_id: 'user-a', role: 'creator', joined_at: 1, deleted_at: null },
      ...(partnered ? [{ id: 'member-b', space_id: 'space-1', user_id: 'user-b', role: 'partner', joined_at: 1, deleted_at: null }] : []),
    ],
    bindCode: {
      id: 'code-1', space_id: 'space-1', code_hash: 'hash-ABC123', created_by: 'user-a',
      expires_at: clock + 100_000, status: 'active', deleted_at: null,
    },
  };
}

const alice = { user: { id: 'user-a', nickname: '小明' }, sessionId: 'session-a' };
const bob = { user: { id: 'user-b', nickname: '小红' }, sessionId: 'session-b' };
const handler = (action) => actions[action].handler;

function wish(id = 'wish-1', title = '一起看海') {
  return {
    id, module: 'WISH', spaceId: 'space-1', visibility: 'couple', title,
    content: '找个周末出发', mediaIds: [], mediaRefs: [], extra: { 状态: '待回应' },
  };
}

function notifications() {
  return [...active.notifications.values()];
}

test('心愿四类动态只通知另一方，非状态编辑与重试保持幂等', async () => {
  fixture({ partnered: true });
  const create = handler('content.create');
  const update = handler('content.update');

  const created = await create(alice, { record: wish() });
  assert.equal(created.record.version, 1);
  assert.equal(notifications().length, 1);
  assert.equal(notifications()[0].user_id, 'user-b');
  assert.equal(notifications()[0].type, 'wish.proposed');
  assert.match(notifications()[0].body, /小明/);
  assert.match(notifications()[0].body, /一起看海/);
  assert.equal(notifications().some((n) => n.user_id === 'user-a'), false);

  await create(alice, { record: wish() });
  assert.equal(notifications().length, 1, '重复创建不得新增通知');

  const edited = await update(alice, {
    id: 'wish-1', baseVersion: 1,
    patch: { title: '一起去看海', content: '只改文案', extra: { 状态: '待回应' } },
  });
  assert.equal(edited.record.version, 2);
  assert.equal(notifications().length, 1, '非状态编辑不得通知');

  const accepted = await update(bob, {
    id: 'wish-1', baseVersion: 2, patch: { extra: { 状态: '已答应', 答应人: 'user-b' } },
  });
  assert.equal(accepted.record.version, 3);
  assert.equal(notifications().length, 2);
  assert.equal(notifications().at(-1).user_id, 'user-a');
  assert.equal(notifications().at(-1).type, 'wish.accepted');
  assert.match(notifications().at(-1).body, /小红/);

  await update(bob, {
    id: 'wish-1', baseVersion: 2, patch: { extra: { 状态: '已答应', 答应人: 'user-b' } },
  });
  assert.equal(notifications().length, 2, '状态请求重试不得新增通知');

  await update(alice, {
    id: 'wish-1', baseVersion: 3, patch: { extra: { 状态: '已实现', 答应人: 'user-b' } },
  });
  assert.equal(notifications().length, 3);
  assert.equal(notifications().at(-1).type, 'wish.realized');
  assert.equal(notifications().at(-1).user_id, 'user-b');

  await create(alice, { record: wish('wish-2', '一起做饭') });
  await update(bob, {
    id: 'wish-2', baseVersion: 1, patch: { extra: { 状态: '被拒绝' } },
  });
  assert.equal(notifications().at(-1).type, 'wish.rejected');
  assert.equal(notifications().at(-1).user_id, 'user-a');

  assert.deepEqual(
    new Set(notifications().map((n) => n.type)),
    new Set(['wish.proposed', 'wish.accepted', 'wish.realized', 'wish.rejected'])
  );
  assert.equal(notifications().every((n) => n.id.startsWith('ntf-')), true);
});

test('空间绑定申请、绑定成功、解除申请和冻结通知正确接收者', async () => {
  fixture();
  const joined = await handler('space.joinByCode')(bob, { code: 'ABC123' });
  const requestId = joined.request.id;
  assert.equal(notifications().length, 1);
  assert.equal(notifications()[0].type, 'space.bind_requested');
  assert.equal(notifications()[0].user_id, 'user-a');
  assert.match(notifications()[0].body, /处理/);

  const retried = await handler('space.joinByCode')(bob, { code: 'ABC123' });
  assert.equal(retried.request.id, requestId, '绑定申请重试应复用同一事件对象');
  assert.equal(notifications().length, 1);

  const waiting = await handler('space.confirmBind')(bob, { requestId });
  assert.equal(waiting.bound, false);
  assert.equal(notifications().length, 1, '单方确认不是绑定成功事件');
  const bound = await handler('space.confirmBind')(alice, { requestId });
  assert.equal(bound.bound, true);
  assert.equal(notifications().length, 2);
  assert.equal(notifications().at(-1).type, 'space.bound');
  assert.equal(notifications().at(-1).user_id, 'user-b');

  await assert.rejects(() => handler('space.confirmBind')(alice, { requestId }));
  assert.equal(notifications().length, 2);

  await handler('space.update')(alice, { name: '新名字', intro: '只改资料' });
  assert.equal(notifications().length, 2, '空间资料编辑不得通知');

  const unbind = await handler('space.unbindRequest')(alice, {});
  assert.equal(notifications().length, 3);
  assert.equal(notifications().at(-1).type, 'space.unbind_requested');
  assert.equal(notifications().at(-1).user_id, 'user-b');
  assert.match(notifications().at(-1).body, /处理/);

  await handler('space.unbindRequest')(alice, {});
  assert.equal(notifications().length, 3, '重复解除申请不得新增通知');

  const frozen = await handler('space.confirmUnbind')(bob, { requestId: unbind.request.id });
  assert.equal(frozen.frozen, true);
  assert.equal(notifications().length, 4);
  assert.equal(notifications().at(-1).type, 'space.frozen');
  assert.equal(notifications().at(-1).user_id, 'user-a');
  assert.match(notifications().at(-1).body, /冻结/);

  await assert.rejects(() => handler('space.confirmUnbind')(bob, { requestId: unbind.request.id }));
  assert.equal(notifications().length, 4);
});

test('情书、照片和普通内容不生成新增云端通知', async () => {
  fixture();
  for (const module of ['LETTER', 'PHOTO', 'JOT', 'NOTE']) {
    await handler('content.create')(alice, {
      record: { ...wish(`record-${module}`), module, title: module },
    });
  }
  assert.equal(notifications().length, 0);
});
