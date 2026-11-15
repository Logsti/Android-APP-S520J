/**
 * S520J 云函数业务处理器。
 * 所有写操作在服务端二次校验 owner_id / space_id / visibility / 冻结状态，
 * 不信任客户端传入的身份或权限字段。
 */
const crypto = require('crypto');
const {
  q, withTx, ApiError, badRequest, unauthorized, forbidden,
  uuid, sha256, randomToken, hashPassword,
  requireAccountId, requirePassword, requireNickname,
  now, RECYCLE_TTL, publicUser, requireSession, issueSession, assertMember, spaceOfUser, canRead,
} = require('./core');

// ---------------------------------------------------------------- auth
async function register(payload) {
  const nickname = requireNickname(payload.nickname);
  const accountId = requireAccountId(payload.accountId);
  const password = requirePassword(payload.password);

  const dup = await q('SELECT 1 FROM users WHERE account_id = $1 AND deleted_at IS NULL', [accountId]);
  if (dup.rows.length) throw badRequest('该账号 ID 已被占用，请换一个');

  const salt = randomToken().slice(0, 32);
  const hash = hashPassword(password, salt);
  const id = uuid();
  const ts = now();
  const { rows } = await q(
    `INSERT INTO users (id, nickname, account_id, recovery_salt, recovery_hash, couple_start_at, created_at, updated_at, version)
     VALUES ($1,$2,$3,$4,$5,0,$6,$6,1) RETURNING *`,
    [id, nickname, accountId, salt, hash, ts]
  );
  const session = await issueSession(rows[0], payload.deviceInfo);
  return { user: publicUser(rows[0]), ...session };
}

async function login(payload) {
  const accountId = requireAccountId(payload.accountId);
  const password = requirePassword(payload.password);
  const { rows } = await q('SELECT * FROM users WHERE account_id = $1 AND deleted_at IS NULL LIMIT 1', [accountId]);
  if (!rows.length) throw badRequest('账号不存在');
  const user = rows[0];
  if (hashPassword(password, user.recovery_salt) !== user.recovery_hash) throw badRequest('密码不正确');
  const session = await issueSession(user, payload.deviceInfo);
  return { user: publicUser(user), ...session };
}

async function refresh(payload) {
  const refreshToken = String(payload.refreshToken || '');
  if (!refreshToken) throw unauthorized();
  const { rows } = await q(
    `SELECT s.id, s.refresh_expires_at, s.revoked, u.*
       FROM auth_sessions s JOIN users u ON u.id = s.user_id
      WHERE s.refresh_token_hash = $1 LIMIT 1`,
    [sha256(refreshToken)]
  );
  if (!rows.length || rows[0].revoked) throw unauthorized('登录已失效，请重新登录');
  if (Number(rows[0].refresh_expires_at) <= now()) throw unauthorized('登录已过期，请重新登录');
  await q('UPDATE auth_sessions SET revoked = true, updated_at = $2 WHERE id = $1', [rows[0].id, now()]);
  const session = await issueSession(rows[0], payload.deviceInfo);
  return { user: publicUser(rows[0]), ...session };
}

async function logout(ctx) {
  await q('UPDATE auth_sessions SET revoked = true, updated_at = $2 WHERE id = $1', [ctx.sessionId, now()]);
  return { ok: true };
}

async function me(ctx) {
  const space = await spaceOfUser(ctx.user.id);
  return { user: publicUser(ctx.user), space: space ? mapSpace(space) : null };
}

async function changePassword(ctx, payload) {
  const oldPassword = requirePassword(payload.oldPassword);
  const newPassword = requirePassword(payload.newPassword);
  if (oldPassword === newPassword) throw badRequest('新旧密码不能相同');
  if (hashPassword(oldPassword, ctx.user.recovery_salt) !== ctx.user.recovery_hash) {
    throw badRequest('旧密码不正确');
  }
  const salt = randomToken().slice(0, 32);
  await q(
    'UPDATE users SET recovery_salt = $2, recovery_hash = $3, updated_at = $4, version = version + 1 WHERE id = $1',
    [ctx.user.id, salt, hashPassword(newPassword, salt), now()]
  );
  return { ok: true };
}

const AVATAR_DATA_URL_PREFIX = /^data:image\/(png|jpeg|jpg|webp);base64,/;
const AVATAR_MAX_BYTES = 100 * 1024; // 100KB：客户端压到 256px 后约 15~40KB，留足余量

async function updateAvatar(ctx, payload) {
  const avatar = String(payload.avatar || '');
  if (!avatar) throw badRequest('头像不能为空');
  if (!AVATAR_DATA_URL_PREFIX.test(avatar)) throw badRequest('头像格式不支持，请上传图片');
  const base64 = avatar.replace(AVATAR_DATA_URL_PREFIX, '');
  const bytes = Math.ceil((base64.length * 3) / 4);
  if (bytes > AVATAR_MAX_BYTES) throw badRequest('头像过大，请压缩后重试');
  await q(
    'UPDATE users SET avatar = $2, updated_at = $3, version = version + 1 WHERE id = $1',
    [ctx.user.id, avatar, now()]
  );
  return { ok: true, avatar };
}

// ---------------------------------------------------------------- space
function mapSpace(row) {
  return {
    id: row.id,
    name: row.name,
    intro: row.intro,
    status: row.status,
    createdBy: row.created_by,
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
    version: row.version,
  };
}

async function spaceCreate(ctx, payload) {
  const name = String(payload.name || '').trim();
  if (!name || name.length > 20) throw badRequest('空间名称不能为空且不超过 20 个字');
  const intro = String(payload.intro || '').trim().slice(0, 80);
  const existing = await spaceOfUser(ctx.user.id);
  if (existing) throw badRequest('每个账号只能加入一个共同空间');
  const id = uuid();
  const ts = now();
  const space = await withTx(async (c) => {
    const { rows } = await c.query(
      `INSERT INTO spaces (id, name, intro, status, created_by, created_at, updated_at, version)
       VALUES ($1,$2,$3,'active',$4,$5,$5,1) RETURNING *`,
      [id, name, intro, ctx.user.id, ts]
    );
    await c.query(
      `INSERT INTO space_members (id, space_id, user_id, role, joined_at, created_at, updated_at, version)
       VALUES ($1,$2,$3,'creator',$4,$4,$4,1)`,
      [uuid(), id, ctx.user.id, ts]
    );
    return rows[0];
  });
  return { space: mapSpace(space) };
}

async function spaceUpdate(ctx, payload) {
  const space = await spaceOfUser(ctx.user.id);
  if (!space) throw badRequest('请先创建共同空间');
  await assertMember(ctx.user.id, space.id, { requireActive: true });
  const name = String(payload.name || '').trim();
  if (!name || name.length > 20) throw badRequest('空间名称不能为空且不超过 20 个字');
  const intro = String(payload.intro || '').trim().slice(0, 80);
  const ts = now();
  const { rows } = await q(
    `UPDATE spaces SET name = $2, intro = $3, updated_at = $4, version = version + 1
      WHERE id = $1 RETURNING *`,
    [space.id, name, intro, ts]
  );
  return { space: mapSpace(rows[0]) };
}

async function spaceGet(ctx) {
  const space = await spaceOfUser(ctx.user.id);
  if (!space) return { space: null };
  const members = await q(
    `SELECT m.user_id, m.role, m.joined_at, u.nickname, u.account_id, u.avatar
       FROM space_members m JOIN users u ON u.id = m.user_id
      WHERE m.space_id = $1 AND m.deleted_at IS NULL ORDER BY m.joined_at`,
    [space.id]
  );
  const code = await q(
    `SELECT id, expires_at FROM bind_codes
      WHERE space_id = $1 AND status = 'active' AND deleted_at IS NULL AND expires_at > $2
      ORDER BY created_at DESC LIMIT 1`,
    [space.id, now()]
  );
  const pending = await q(
    `SELECT * FROM bind_requests WHERE space_id = $1 AND status IN ('pending','unbind_pending','rebind_pending')
      ORDER BY created_at DESC LIMIT 1`,
    [space.id]
  );
  return {
    space: mapSpace(space),
    members: members.rows.map((m) => ({
      userId: m.user_id, role: m.role, nickname: m.nickname, accountId: m.account_id,
      avatar: m.avatar, joinedAt: Number(m.joined_at),
    })),
    bindCodeExpiresAt: code.rows.length ? Number(code.rows[0].expires_at) : null,
    pendingRequest: pending.rows.length ? mapBindRequest(pending.rows[0]) : null,
  };
}

function mapBindRequest(r) {
  return {
    id: r.id, spaceId: r.space_id, fromUser: r.from_user, toUser: r.to_user,
    status: r.status, confirmedByA: r.confirmed_by_a, confirmedByB: r.confirmed_by_b,
    createdAt: Number(r.created_at), updatedAt: Number(r.updated_at),
  };
}

const BIND_CHARSET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

// 通知沿用既有 notifications 表；ID 由事件对象、状态版本和接收者共同决定。
// 同一事件重试只执行 ON CONFLICT DO NOTHING，不依赖云端 read 状态判断客户端是否已读。
function notificationId(objectId, stateVersion, recipientId) {
  return `ntf-${sha256(`${objectId}\u0000${stateVersion}\u0000${recipientId}`)}`;
}

async function insertNotification({ objectId, stateVersion, recipientId, spaceId, type, title, body, createdAt }) {
  if (!recipientId) return;
  const ts = createdAt || now();
  await q(
    `INSERT INTO notifications (id, user_id, space_id, type, title, body, read, created_at, updated_at, version)
     VALUES ($1,$2,$3,$4,$5,$6,false,$7,$7,1)
     ON CONFLICT (id) DO NOTHING`,
    [notificationId(objectId, stateVersion, recipientId), recipientId, spaceId || null, type, title, body, ts]
  );
}

function wishTitle(row) {
  return String(row.title || '').trim() || '无标题心愿';
}

async function notifyWishPartners(ctx, row, type, title, body, stateVersion) {
  if (row.module !== 'WISH' || String(row.visibility).toLowerCase() !== 'couple' || !row.space_id) return;
  const { rows } = await q(
    'SELECT user_id FROM space_members WHERE space_id = $1 AND user_id <> $2 AND deleted_at IS NULL',
    [row.space_id, ctx.user.id]
  );
  for (const recipient of rows) {
    await insertNotification({
      objectId: `wish:${row.id}`, stateVersion, recipientId: recipient.user_id, spaceId: row.space_id,
      type, title, body, createdAt: Number(row.updated_at || row.created_at || now()),
    });
  }
}

async function notifyWishProposed(ctx, row) {
  await notifyWishPartners(
    ctx, row, 'wish.proposed', 'TA 提出了新心愿',
    `${ctx.user.nickname} 提出了心愿「${wishTitle(row)}」，等你回应。`, Number(row.version || 1)
  );
}

async function notifyWishStatusChanged(ctx, before, after) {
  if (before.module !== 'WISH' || after.module !== 'WISH') return;
  const previous = (before.extra && before.extra['状态']) || '待回应';
  const current = (after.extra && after.extra['状态']) || '待回应';
  if (previous === current) return;
  const event = {
    '已答应': ['wish.accepted', '心愿已答应', `${ctx.user.nickname} 答应了心愿「${wishTitle(after)}」。`],
    '被拒绝': ['wish.rejected', '心愿被婉拒', `${ctx.user.nickname} 婉拒了心愿「${wishTitle(after)}」。`],
    '已实现': ['wish.realized', '心愿已实现', `${ctx.user.nickname} 将心愿「${wishTitle(after)}」标记为已实现。`],
  }[current];
  if (!event) return;
  await notifyWishPartners(ctx, after, event[0], event[1], event[2], Number(after.version));
}

async function notifyBindRequested(ctx, request) {
  await insertNotification({
    objectId: `bind-request:${request.id}`, stateVersion: 'pending', recipientId: request.to_user,
    spaceId: request.space_id, type: 'space.bind_requested', title: '收到共同空间绑定申请',
    body: `${ctx.user.nickname} 请求与你组成共同空间，请处理绑定申请。`, createdAt: Number(request.created_at || now()),
  });
}

async function spaceGenerateBindCode(ctx) {
  const space = await spaceOfUser(ctx.user.id);
  if (!space) throw badRequest('请先创建共同空间');
  await assertMember(ctx.user.id, space.id, { requireActive: true });
  const code = Array.from({ length: 6 }, () => BIND_CHARSET[crypto.randomInt(0, BIND_CHARSET.length)]).join('');
  const ts = now();
  const ttl = 24 * 60 * 60 * 1000;
  await withTx(async (c) => {
    await c.query(
      `UPDATE bind_codes SET status = 'expired', updated_at = $2
        WHERE space_id = $1 AND status = 'active'`,
      [space.id, ts]
    );
    await c.query(
      `INSERT INTO bind_codes (id, space_id, code_hash, created_by, expires_at, status, created_at, updated_at, version)
       VALUES ($1,$2,$3,$4,$5,'active',$6,$6,1)`,
      [uuid(), space.id, sha256(code), ctx.user.id, ts + ttl, ts]
    );
  });
  return { code, expiresAt: ts + ttl };
}

async function spaceJoinByCode(ctx, payload) {
  const code = String(payload.code || '').trim().toUpperCase();
  if (!code) throw badRequest('请输入绑定码');
  const found = await q(
    `SELECT c.*, s.created_by, s.name AS space_name, s.intro AS space_intro,
            s.status AS space_status
       FROM bind_codes c JOIN spaces s ON s.id = c.space_id
      WHERE c.code_hash = $1 AND c.status = 'active' AND c.deleted_at IS NULL LIMIT 1`,
    [sha256(code)]
  );
  if (!found.rows.length) throw badRequest('绑定码无效或已过期');
  const c = found.rows[0];
  if (Number(c.expires_at) <= now()) throw badRequest('绑定码已过期，请让对方重新生成');
  if (c.status !== 'active') throw badRequest('绑定码已被使用');
  if (c.space_status !== 'active') throw badRequest('该共同空间已冻结归档');
  if (c.created_by === ctx.user.id) throw badRequest('不能绑定自己创建的空间');
  const count = await q('SELECT COUNT(*)::int AS n FROM space_members WHERE space_id = $1 AND deleted_at IS NULL', [c.space_id]);
  if (count.rows[0].n >= 2) throw badRequest('该共同空间已满');

  const duplicated = await q(
    `SELECT * FROM bind_requests WHERE space_id = $1 AND from_user = $2 AND status = $3
     ORDER BY created_at DESC LIMIT 1`,
    [c.space_id, ctx.user.id, 'pending']
  );
  if (duplicated.rows.length) {
    const request = duplicated.rows[0];
    await notifyBindRequested(ctx, request);
    return {
      request: mapBindRequest(request),
      space: {
        id: c.space_id, name: c.space_name, intro: c.space_intro,
        status: c.space_status, createdBy: c.created_by,
      },
    };
  }

  const ts = now();
  // 账号首次同步会自动建一个空的个人空间；加入对方空间前先归档它，
  // 否则会一直提示“当前账号已加入共同空间”。
  const existing = await spaceOfUser(ctx.user.id);
  if (existing) {
    const own = await q(
      'SELECT COUNT(*)::int AS n FROM space_members WHERE space_id = $1 AND deleted_at IS NULL',
      [existing.id]
    );
    if (!(own.rows[0].n === 1 && existing.created_by === ctx.user.id)) {
      throw badRequest('当前账号已加入共同空间');
    }
    await withTx(async (c2) => {
      await c2.query(
        "UPDATE bind_codes SET status = $2, updated_at = $3 WHERE space_id = $1 AND status = $4",
        [existing.id, 'expired', ts, 'active']
      );
      await c2.query(
        'UPDATE space_members SET deleted_at = $3, updated_at = $3, version = version + 1 WHERE space_id = $1 AND user_id = $2',
        [existing.id, ctx.user.id, ts]
      );
      await c2.query(
        'UPDATE spaces SET deleted_at = $2, updated_at = $2, version = version + 1 WHERE id = $1',
        [existing.id, ts]
      );
    });
  }

  const { rows } = await q(
    `INSERT INTO bind_requests (id, space_id, from_user, to_user, status, created_at, updated_at, version)
     VALUES ($1,$2,$3,$4,'pending',$5,$5,1) RETURNING *`,
    [uuid(), c.space_id, ctx.user.id, c.created_by, ts]
  );
  await notifyBindRequested(ctx, rows[0]);
  return {
    request: mapBindRequest(rows[0]),
    space: {
      id: c.space_id,
      name: c.space_name,
      intro: c.space_intro,
      status: c.space_status,
      createdBy: c.created_by,
    },
  };
}

async function spaceConfirmBind(ctx, payload) {
  const id = String(payload.requestId || '');
  const { rows } = await q('SELECT * FROM bind_requests WHERE id = $1 LIMIT 1', [id]);
  if (!rows.length) throw badRequest('绑定申请不存在');
  const r = rows[0];
  if (r.status !== 'pending') throw badRequest('该绑定申请已处理');
  if (ctx.user.id !== r.from_user && ctx.user.id !== r.to_user) throw forbidden();
  const isJoiner = ctx.user.id === r.from_user;
  const ts = now();
  const confirmedA = isJoiner ? r.confirmed_by_a : ctx.user.id;
  const confirmedB = isJoiner ? ctx.user.id : r.confirmed_by_b;
  if (confirmedA && confirmedB) {
    await withTx(async (c) => {
      await c.query(
        'UPDATE bind_requests SET confirmed_by_a = $2, confirmed_by_b = $3, status = $4, updated_at = $5 WHERE id = $1',
        [id, confirmedA, confirmedB, 'confirmed', ts]
      );
      await c.query(
        `INSERT INTO space_members (id, space_id, user_id, role, joined_at, created_at, updated_at, version)
         VALUES ($1,$2,$3,'partner',$4,$4,$4,1)
         ON CONFLICT (space_id, user_id) DO NOTHING`,
        [uuid(), r.space_id, r.from_user, ts]
      );
      await c.query(
        `UPDATE bind_codes SET status = 'used', updated_at = $2 WHERE space_id = $1 AND status = 'active'`,
        [r.space_id, ts]
      );
    });
    await insertNotification({
      objectId: `bind-request:${id}`, stateVersion: 'confirmed',
      recipientId: isJoiner ? r.to_user : r.from_user, spaceId: r.space_id,
      type: 'space.bound', title: '共同空间绑定成功',
      body: `${ctx.user.nickname} 已确认绑定，你们现在可以共同记录了。`, createdAt: ts,
    });
    return { bound: true };
  }
  await q(
    'UPDATE bind_requests SET confirmed_by_a = $2, confirmed_by_b = $3, updated_at = $4 WHERE id = $1',
    [id, confirmedA, confirmedB, ts]
  );
  return { bound: false, waitingForPartner: true };
}

async function spaceUnbindRequest(ctx) {
  const space = await spaceOfUser(ctx.user.id);
  if (!space) throw badRequest('尚未加入共同空间');
  await assertMember(ctx.user.id, space.id, { requireActive: true });
  // 同一空间只允许存在一条待处理解绑申请，重复发起直接复用，避免双方各自确认不同请求。
  const pending = await q(
    `SELECT * FROM bind_requests WHERE space_id = $1 AND status = 'unbind_pending'
      ORDER BY created_at DESC LIMIT 1`,
    [space.id]
  );
  if (pending.rows.length) {
    if (pending.rows[0].from_user === ctx.user.id) {
      await insertNotification({
        objectId: `unbind-request:${pending.rows[0].id}`, stateVersion: 'unbind_pending',
        recipientId: pending.rows[0].to_user, spaceId: space.id,
        type: 'space.unbind_requested', title: '收到解除绑定申请',
        body: `${ctx.user.nickname} 请求解除共同空间绑定，请处理解除申请。`,
        createdAt: Number(pending.rows[0].created_at || now()),
      });
    }
    return { request: mapBindRequest(pending.rows[0]) };
  }
  const others = await q(
    'SELECT user_id FROM space_members WHERE space_id = $1 AND user_id <> $2 AND deleted_at IS NULL LIMIT 1',
    [space.id, ctx.user.id]
  );
  if (!others.rows.length) throw badRequest('空间内没有另一位成员');
  const ts = now();
  const { rows } = await q(
    `INSERT INTO bind_requests (id, space_id, from_user, to_user, status, created_at, updated_at, version)
     VALUES ($1,$2,$3,$4,'unbind_pending',$5,$5,1) RETURNING *`,
    [uuid(), space.id, ctx.user.id, others.rows[0].user_id, ts]
  );
  await insertNotification({
    objectId: `unbind-request:${rows[0].id}`, stateVersion: 'unbind_pending',
    recipientId: rows[0].to_user, spaceId: space.id,
    type: 'space.unbind_requested', title: '收到解除绑定申请',
    body: `${ctx.user.nickname} 请求解除共同空间绑定，请处理解除申请。`, createdAt: ts,
  });
  return { request: mapBindRequest(rows[0]) };
}

async function spaceConfirmUnbind(ctx, payload) {
  const id = String(payload.requestId || '');
  const { rows } = await q('SELECT * FROM bind_requests WHERE id = $1 LIMIT 1', [id]);
  if (!rows.length) throw badRequest('解绑申请不存在');
  const r = rows[0];
  if (r.status !== 'unbind_pending') throw badRequest('该解绑申请已处理');
  if (ctx.user.id !== r.to_user) throw forbidden('需由对方确认解绑');
  const ts = now();
  let frozenSpace;
  await withTx(async (c) => {
    await c.query(
      `UPDATE bind_requests SET confirmed_by_a = $2, confirmed_by_b = $3, status = 'confirmed', updated_at = $4 WHERE id = $1`,
      [id, r.from_user, ctx.user.id, ts]
    );
    const updated = await c.query(
      `UPDATE spaces SET status = 'frozen', updated_at = $2, version = version + 1 WHERE id = $1 RETURNING *`,
      [r.space_id, ts]
    );
    frozenSpace = updated.rows[0];
  });
  await insertNotification({
    objectId: `space:${r.space_id}`, stateVersion: `frozen:${frozenSpace.version}`,
    recipientId: r.from_user, spaceId: r.space_id,
    type: 'space.frozen', title: '共同空间已冻结',
    body: `${ctx.user.nickname} 已确认解除绑定，共同空间已冻结归档，历史内容仅可查看与导出。`, createdAt: ts,
  });
  return { frozen: true };
}

async function spaceRejectUnbind(ctx, payload) {
  const id = String(payload.requestId || '');
  const { rows } = await q('SELECT * FROM bind_requests WHERE id = $1 LIMIT 1', [id]);
  if (!rows.length) throw badRequest('解绑申请不存在');
  const r = rows[0];
  if (r.status !== 'unbind_pending') throw badRequest('该解绑申请已处理');
  if (ctx.user.id !== r.to_user) throw forbidden('需由被申请方拒绝解绑');
  await q(
    "UPDATE bind_requests SET status = $2, updated_at = $3 WHERE id = $1",
    [id, 'rejected', now()]
  );
  return { rejected: true };
}

async function spaceCancelUnbind(ctx, payload) {
  const id = String(payload.requestId || '');
  const { rows } = await q('SELECT * FROM bind_requests WHERE id = $1 LIMIT 1', [id]);
  if (!rows.length) throw badRequest('解绑申请不存在');
  const r = rows[0];
  if (r.status !== 'unbind_pending') throw badRequest('该解绑申请已处理');
  if (ctx.user.id !== r.from_user) throw forbidden('只有发起方可以撤回解绑');
  await q(
    "UPDATE bind_requests SET status = $2, updated_at = $3 WHERE id = $1",
    [id, 'cancelled', now()]
  );
  return { cancelled: true };
}

async function spaceRebind(ctx) {
  const space = await spaceOfUser(ctx.user.id);
  if (!space) throw badRequest('尚未加入共同空间');
  if (space.status !== 'frozen') throw badRequest('共同空间未处于冻结状态');
  const ts = now();
  const pending = await q(
    `SELECT * FROM bind_requests WHERE space_id = $1 AND status = 'rebind_pending' ORDER BY created_at DESC LIMIT 1`,
    [space.id]
  );
  if (pending.rows.length && pending.rows[0].from_user !== ctx.user.id) {
    await withTx(async (c) => {
      await c.query(
        'UPDATE bind_requests SET confirmed_by_a = $2, confirmed_by_b = $3, status = $4, updated_at = $5 WHERE id = $1',
        [pending.rows[0].id, pending.rows[0].from_user, ctx.user.id, 'confirmed', ts]
      );
      await c.query('UPDATE spaces SET status = $2, updated_at = $3, version = version + 1 WHERE id = $1', ['active', ts, space.id]);
    });
    return { reactivated: true };
  }
  const others = await q(
    'SELECT user_id FROM space_members WHERE space_id = $1 AND user_id <> $2 AND deleted_at IS NULL LIMIT 1',
    [space.id, ctx.user.id]
  );
  if (!others.rows.length) throw badRequest('空间内没有另一位成员');
  const { rows } = await q(
    `INSERT INTO bind_requests (id, space_id, from_user, to_user, status, created_at, updated_at, version)
     VALUES ($1,$2,$3,$4,'rebind_pending',$5,$5,1) RETURNING *`,
    [uuid(), space.id, ctx.user.id, others.rows[0].user_id, ts]
  );
  return { reactivated: false, waitingForPartner: true, request: mapBindRequest(rows[0]) };
}

// ---------------------------------------------------------------- content
function mapContent(r) {
  return {
    id: r.id,
    module: r.module,
    spaceId: r.space_id,
    ownerId: r.owner_id,
    ownerName: r.owner_name,
    visibility: r.visibility,
    title: r.title,
    content: r.content,
    eventDate: r.event_date == null ? null : Number(r.event_date),
    mediaIds: r.media_ids || [],
    mediaRefs: r.media_refs || [],
    extra: r.extra || {},
    createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at),
    deletedAt: r.deleted_at == null ? null : Number(r.deleted_at),
    version: r.version,
  };
}

async function assertWriteAllowed(userId, row, targetVisibility, targetSpaceId) {
  const vis = String(targetVisibility || row.visibility || '').toLowerCase();
  const rowVis = String(row.visibility || '').toLowerCase();
  if (row.owner_id !== userId) {
    // 共同内容：双方均可编辑删除；但不得把共同内容改为仅自己可见。
    if (rowVis !== 'couple' || !row.space_id) throw forbidden('只能修改自己创建的内容');
    await assertMember(userId, row.space_id, { requireActive: true });
    if (String(targetVisibility || '').toLowerCase() === 'private') throw forbidden('共同内容不能改为仅自己可见');
  }
  if (vis === 'couple') {
    const sid = targetSpaceId || row.space_id;
    if (!sid) throw badRequest('共同可见内容必须属于共同空间');
    await assertMember(userId, sid, { requireActive: true });
  }
}

async function contentCreate(ctx, payload) {
  const rec = payload.record || {};
  const id = String(rec.id || uuid());
  if (!rec.module) throw badRequest('缺少内容模块');
  const visibility = String(rec.visibility || '').toLowerCase() === 'couple' ? 'couple' : 'private';
  if (visibility === 'couple') {
    if (!rec.spaceId) throw badRequest('共同可见内容必须属于共同空间');
    await assertMember(ctx.user.id, rec.spaceId, { requireActive: true });
  }
  const ts = now();
  const existing = await q('SELECT * FROM contents WHERE id = $1 LIMIT 1', [id]);
  if (existing.rows.length) {
    if (!(await canRead(existing.rows[0], ctx.user.id))) throw forbidden();
    if (existing.rows[0].owner_id === ctx.user.id) await notifyWishProposed(ctx, existing.rows[0]);
    return { record: mapContent(existing.rows[0]), duplicated: true };
  }
  const { rows } = await q(
    `INSERT INTO contents (id, module, space_id, owner_id, owner_name, visibility, title, content, event_date, media_ids, media_refs, extra, created_at, updated_at, deleted_at, version)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11::jsonb,$12::jsonb,$13,$13,NULL,1) RETURNING *`,
    [id, rec.module, rec.spaceId || null, ctx.user.id, ctx.user.nickname, visibility,
      rec.title || '', rec.content || '', rec.eventDate == null ? null : Number(rec.eventDate),
      JSON.stringify(rec.mediaIds || []), JSON.stringify(rec.mediaRefs || []),
      JSON.stringify(rec.extra || {}), ts]
  );
  await notifyWishProposed(ctx, rows[0]);
  return { record: mapContent(rows[0]) };
}

async function contentUpdate(ctx, payload) {
  const id = String(payload.id || '');
  const patch = payload.patch || {};
  const baseVersion = payload.baseVersion == null ? null : Number(payload.baseVersion);
  const { rows } = await q('SELECT * FROM contents WHERE id = $1 LIMIT 1', [id]);
  if (!rows.length) throw badRequest('内容不存在或已被彻底删除');
  const current = rows[0];
  await assertWriteAllowed(ctx.user.id, current, patch.visibility, patch.spaceId);

  if (baseVersion != null && Number(current.version) !== baseVersion) {
    const ts = now();
    await q(
      `INSERT INTO conflict_snapshots (id, record_type, record_id, field_json, source_user, created_at, expire_at)
       VALUES ($1,'content',$2,$3::jsonb,$4,$5,$6)`,
      [uuid(), id, JSON.stringify(patch), ctx.user.id, ts, ts + RECYCLE_TTL]
    );
    return { conflict: true, server: mapContent(current) };
  }

  const sets = [];
  const vals = [id];
  const put = (col, val) => { vals.push(val); sets.push(`${col} = $${vals.length}`); };
  if (patch.module != null) put('module', String(patch.module));
  if (patch.title != null) put('title', String(patch.title));
  if (patch.content != null) put('content', String(patch.content));
  if (patch.visibility != null) put('visibility', String(patch.visibility).toLowerCase() === 'couple' ? 'couple' : 'private');
  if (patch.spaceId !== undefined) put('space_id', patch.spaceId || null);
  if (patch.eventDate !== undefined) put('event_date', patch.eventDate == null ? null : Number(patch.eventDate));
  if (patch.mediaIds != null) put('media_ids', JSON.stringify(patch.mediaIds));
  if (patch.mediaRefs != null) put('media_refs', JSON.stringify(patch.mediaRefs));
  if (patch.extra != null) put('extra', JSON.stringify(patch.extra));
  put('updated_at', now());
  sets.push('version = version + 1');

  const updated = await q(`UPDATE contents SET ${sets.join(', ')} WHERE id = $1 RETURNING *`, vals);
  await notifyWishStatusChanged(ctx, current, updated.rows[0]);
  return { record: mapContent(updated.rows[0]) };
}

async function contentSoftDelete(ctx, payload) {
  const id = String(payload.id || '');
  const { rows } = await q('SELECT * FROM contents WHERE id = $1 LIMIT 1', [id]);
  if (!rows.length) return { ok: true, missing: true };
  const row = rows[0];
  await assertWriteAllowed(ctx.user.id, row);
  const ts = now();
  await withTx(async (c) => {
    await c.query('UPDATE contents SET deleted_at = $2, updated_at = $2, version = version + 1 WHERE id = $1', [id, ts]);
    await c.query(
      `INSERT INTO recycle_bin (id, record_type, record_id, space_id, deleted_by, deleted_at, expire_at, created_at, updated_at, version)
       VALUES ($1,'content',$2,$3,$4,$5,$6,$5,$5,1)
       ON CONFLICT (record_type, record_id) DO UPDATE SET deleted_by = $4, deleted_at = $5, expire_at = $6, updated_at = $5`,
      [uuid(), id, row.space_id, ctx.user.id, ts, ts + RECYCLE_TTL]
    );
  });
  return { ok: true, deletedAt: ts };
}

async function contentRestore(ctx, payload) {
  const id = String(payload.id || '');
  const { rows } = await q('SELECT * FROM contents WHERE id = $1 LIMIT 1', [id]);
  if (!rows.length) throw badRequest('内容不存在');
  await assertWriteAllowed(ctx.user.id, rows[0]);
  const ts = now();
  await withTx(async (c) => {
    await c.query('UPDATE contents SET deleted_at = NULL, updated_at = $2, version = version + 1 WHERE id = $1', [id, ts]);
    await c.query('DELETE FROM recycle_bin WHERE record_type = $1 AND record_id = $2', ['content', id]);
  });
  return { ok: true };
}

async function contentList(ctx, payload) {
  const modules = Array.isArray(payload.modules) ? payload.modules : null;
  const { rows } = await q(
    `SELECT * FROM contents
      WHERE deleted_at IS NULL
        AND (owner_id = $1 OR (LOWER(visibility) = 'couple' AND space_id IN
             (SELECT space_id FROM space_members WHERE user_id = $1 AND deleted_at IS NULL)))
        ${modules ? 'AND module = ANY($2)' : ''}
      ORDER BY updated_at DESC LIMIT $${modules ? 3 : 2}`,
    modules ? [ctx.user.id, modules, Number(payload.limit || 300)] : [ctx.user.id, Number(payload.limit || 300)]
  );
  return { records: rows.map(mapContent) };
}

// ---------------------------------------------------------------- sync
async function syncPull(ctx, payload) {
  const cursor = Number(payload.cursor || 0);
  const limit = Math.min(Number(payload.limit || 200), 500);
  const { rows } = await q(
    `SELECT * FROM contents
      WHERE updated_at > $2
        AND (owner_id = $1 OR (LOWER(visibility) = 'couple' AND space_id IN
             (SELECT space_id FROM space_members WHERE user_id = $1 AND deleted_at IS NULL)))
      ORDER BY updated_at ASC LIMIT $3`,
    [ctx.user.id, cursor, limit]
  );
  // 「共同可见 → 仅自己」后对方已无权访问，但本地仍留着旧副本。
  // 这里只回传 id（不回传内容，避免泄露隐私），由客户端删除本地副本。
  const revoked = await q(
    `SELECT id FROM contents
      WHERE updated_at > $2
        AND owner_id <> $1
        AND LOWER(visibility) <> 'couple'
        AND space_id IN (SELECT space_id FROM space_members WHERE user_id = $1 AND deleted_at IS NULL)
      ORDER BY updated_at ASC LIMIT 500`,
    [ctx.user.id, cursor]
  );
  const nextCursor = rows.length ? Number(rows[rows.length - 1].updated_at) : cursor;
  return {
    records: rows.map(mapContent),
    revokedIds: revoked.rows.map((r) => r.id),
    cursor: nextCursor,
    hasMore: rows.length === limit,
  };
}

async function syncPush(ctx, payload) {
  const ops = Array.isArray(payload.ops) ? payload.ops : [];
  const results = [];
  for (const op of ops) {
    try {
      let data = { ok: true };
      if (op.op === 'UPSERT') {
        const existing = await q('SELECT * FROM contents WHERE id = $1 LIMIT 1', [op.entityId]);
        data = existing.rows.length
          ? await contentUpdate(ctx, { id: op.entityId, patch: (op.record && op.record.patch) || {}, baseVersion: op.baseVersion })
          : await contentCreate(ctx, { record: op.record });
      } else if (op.op === 'SOFT_DELETE') {
        data = await contentSoftDelete(ctx, { id: op.entityId });
      } else if (op.op === 'RESTORE') {
        data = await contentRestore(ctx, { id: op.entityId });
      } else if (op.op === 'PURGE') {
        data = await recyclePurgeRequest(ctx, { recordId: op.entityId, recordType: 'content' });
      } else {
        throw badRequest('未知操作：' + op.op);
      }
      results.push({ seq: op.seq, ok: true, data });
    } catch (e) {
      results.push({ seq: op.seq, ok: false, code: e.status || 500, message: e.message });
      if (!(e instanceof ApiError) || e.status >= 500) throw e;
    }
  }
  return { results, syncedAt: now() };
}

// ---------------------------------------------------------------- recycle
async function recycleList(ctx) {
  const { rows } = await q(
    `SELECT r.*, c.title, c.module, c.owner_id FROM recycle_bin r
       LEFT JOIN contents c ON c.id = r.record_id
      WHERE r.deleted_by = $1
         OR r.space_id IN (SELECT space_id FROM space_members WHERE user_id = $1 AND deleted_at IS NULL)
      ORDER BY r.deleted_at DESC`,
    [ctx.user.id]
  );
  return {
    items: rows.map((r) => ({
      id: r.id, recordType: r.record_type, recordId: r.record_id, spaceId: r.space_id,
      deletedBy: r.deleted_by, deletedAt: Number(r.deleted_at), expireAt: Number(r.expire_at),
      purgeRequestedBy: r.purge_requested_by, purgeConfirmedBy: r.purge_confirmed_by,
      title: r.title, module: r.module,
    })),
  };
}

async function recyclePurgeRequest(ctx, payload) {
  const recordId = String(payload.recordId || '');
  const { rows } = await q(
    'SELECT * FROM recycle_bin WHERE record_type = $1 AND record_id = $2 LIMIT 1',
    [payload.recordType || 'content', recordId]
  );
  if (!rows.length) throw badRequest('回收站中不存在该内容');
  const r = rows[0];
  const { rows: contentRows } = await q('SELECT * FROM contents WHERE id = $1 LIMIT 1', [recordId]);
  if (contentRows.length) {
    const c = contentRows[0];
    const isCouple = c.space_id && String(c.visibility).toLowerCase() === 'couple';
    if (isCouple) await assertMember(ctx.user.id, c.space_id, { requireActive: true });
    else if (c.owner_id !== ctx.user.id) throw forbidden();
    if (isCouple) {
      if (r.purge_requested_by && r.purge_requested_by !== ctx.user.id) {
        await withTx(async (tx) => {
          await tx.query('UPDATE recycle_bin SET purge_confirmed_by = $2, updated_at = $3 WHERE id = $1', [r.id, ctx.user.id, now()]);
          await tx.query('DELETE FROM contents WHERE id = $1', [recordId]);
          await tx.query('DELETE FROM recycle_bin WHERE record_type = $1 AND record_id = $2', ['content', recordId]);
        });
        return { purged: true };
      }
      await q('UPDATE recycle_bin SET purge_requested_by = $2, updated_at = $3 WHERE id = $1', [r.id, ctx.user.id, now()]);
      return { purged: false, waitingForPartner: true };
    }
  }
  await withTx(async (tx) => {
    await tx.query('DELETE FROM contents WHERE id = $1', [recordId]);
    await tx.query('DELETE FROM recycle_bin WHERE record_type = $1 AND record_id = $2', ['content', recordId]);
  });
  return { purged: true };
}

// ---------------------------------------------------------------- notification / settings
async function notificationList(ctx) {
  const { rows } = await q(
    'SELECT * FROM notifications WHERE user_id = $1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 200',
    [ctx.user.id]
  );
  return {
    items: rows.map((n) => ({
      id: n.id, title: n.title, body: n.body, read: n.read, createdAt: Number(n.created_at), type: n.type,
    })),
  };
}

async function notificationMarkRead(ctx, payload) {
  const ids = Array.isArray(payload.ids) ? payload.ids : [];
  if (!ids.length) throw badRequest('缺少通知 ID');
  await q('UPDATE notifications SET read = true, updated_at = $3 WHERE user_id = $1 AND id = ANY($2)', [ctx.user.id, ids, now()]);
  return { ok: true };
}

async function settingsGet(ctx, payload) {
  const scope = payload.scope === 'space' ? 'space' : 'user';
  if (scope === 'user') {
    const { rows } = await q(
      'SELECT key, value FROM settings WHERE scope = $1 AND owner_id = $2 AND deleted_at IS NULL',
      ['user', ctx.user.id]
    );
    return { values: Object.fromEntries(rows.map((r) => [r.key, r.value])) };
  }
  const space = await spaceOfUser(ctx.user.id);
  if (!space) return { values: {} };
  const { rows } = await q(
    'SELECT key, value FROM settings WHERE scope = $1 AND space_id = $2 AND deleted_at IS NULL',
    ['space', space.id]
  );
  return { values: Object.fromEntries(rows.map((r) => [r.key, r.value])) };
}

async function settingsPut(ctx, payload) {
  const scope = payload.scope === 'space' ? 'space' : 'user';
  const key = String(payload.key || '');
  if (!key) throw badRequest('缺少设置项名称');
  let spaceId = null;
  if (scope === 'space') {
    const space = await spaceOfUser(ctx.user.id);
    if (!space) throw badRequest('尚未加入共同空间');
    await assertMember(ctx.user.id, space.id, { requireActive: true });
    spaceId = space.id;
  }
  const ts = now();
  // PostgreSQL 唯一索引不把 NULL 视为相等，user scope 的 space_id 为 NULL，
  // 因此不能用 ON CONFLICT，改为先更新再按需插入。
  const updated = await q(
    `UPDATE settings SET value = $5::jsonb, updated_at = $6, version = version + 1
      WHERE scope = $1 AND key = $2 AND owner_id IS NOT DISTINCT FROM $3::text AND space_id IS NOT DISTINCT FROM $4::text
      RETURNING id`,
    [scope, key, scope === 'user' ? ctx.user.id : null, spaceId, JSON.stringify(payload.value ?? {}), ts]
  );
  if (!updated.rows.length) {
    await q(
      `INSERT INTO settings (id, scope, owner_id, space_id, key, value, created_at, updated_at, version)
       VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7,$7,1)`,
      [uuid(), scope, scope === 'user' ? ctx.user.id : null, spaceId, key, JSON.stringify(payload.value ?? {}), ts]
    );
  }
  return { ok: true };
}

// ---------------------------------------------------------------- media / backup（骨架，8C / 阶段 9 接入）
async function mediaList(ctx) {
  const { rows } = await q(
    'SELECT * FROM media_assets WHERE owner_id = $1 AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 200',
    [ctx.user.id]
  );
  return { items: rows };
}

async function mediaPrepare() {
  // 云存储上传通道在 8C.1 接入（分片 / 断点续传 / 预签名）。先明确拒绝，避免返回不可用地址。
  throw new ApiError(501, '媒体上传将在 8C.1 接入');
}

async function mediaComplete(ctx, payload) {
  await q(
    `UPDATE media_assets SET status = 'uploaded', size = $3, updated_at = $4, version = version + 1
      WHERE owner_id = $1 AND storage_key = $2`,
    [ctx.user.id, String(payload.storageKey || ''), Number(payload.size || 0), now()]
  );
  return { ok: true };
}

async function backupCreate(ctx) {
  const { rows } = await q(
    `SELECT * FROM contents WHERE deleted_at IS NULL AND owner_id = $1 ORDER BY updated_at`,
    [ctx.user.id]
  );
  const id = uuid();
  const ts = now();
  await q(
    `INSERT INTO backup_jobs (id, user_id, status, created_at, updated_at, version)
     VALUES ($1,$2,'done',$3,$3,1)`,
    [id, ctx.user.id, ts]
  );
  return {
    jobId: id,
    status: 'done',
    exportedAt: ts,
    payload: {
      user: publicUser(ctx.user),
      records: rows.map(mapContent),
    },
  };
}

// ---------------------------------------------------------------- 共同生活（挑战 / 事件）
function requireDay(payload) {
  const day = Number(payload.day);
  if (!Number.isFinite(day) || day <= 0) throw badRequest('缺少日期');
  return Math.floor(day);
}

function mapChallenge(row, userId) {
  const doneBy = Array.isArray(row.done_by) ? row.done_by : [];
  return {
    day: Number(row.day),
    promptIndex: Number(row.prompt_index || 0),
    selfDone: doneBy.includes(userId),
    partnerDone: doneBy.some((id) => id !== userId),
    doneAt: row.done_at ? Number(row.done_at) : null,
  };
}

async function challengeGet(ctx, payload) {
  const day = requireDay(payload);
  const space = await spaceOfUser(ctx.user.id);
  if (!space) return { challenge: mapChallenge({ day, done_by: [] }, ctx.user.id) };
  const { rows } = await q(
    'SELECT * FROM challenge_days WHERE space_id = $1 AND day = $2 LIMIT 1',
    [space.id, day]
  );
  const row = rows[0] || { day, prompt_index: 0, done_by: [] };
  return { challenge: mapChallenge(row, ctx.user.id) };
}

async function challengeMark(ctx, payload) {
  const day = requireDay(payload);
  const space = await spaceOfUser(ctx.user.id);
  if (!space) throw badRequest('请先绑定共同空间');
  await assertMember(ctx.user.id, space.id, { requireActive: true });
  const ts = now();
  const row = await withTx(async (c) => {
    const found = await c.query(
      'SELECT * FROM challenge_days WHERE space_id = $1 AND day = $2 FOR UPDATE',
      [space.id, day]
    );
    let current = found.rows[0];
    if (!current) {
      const inserted = await c.query(
        `INSERT INTO challenge_days (id, space_id, day, prompt_index, done_by, created_at, updated_at, version)
         VALUES ($1,$2,$3,0,'[]'::jsonb,$4,$4,1) RETURNING *`,
        [uuid(), space.id, day, ts]
      );
      current = inserted.rows[0];
    }
    const doneBy = Array.isArray(current.done_by) ? current.done_by.slice() : [];
    // 只标记自己且不可撤销：重复调用幂等。
    if (!doneBy.includes(ctx.user.id)) doneBy.push(ctx.user.id);
    const members = await c.query(
      'SELECT COUNT(*)::int AS n FROM space_members WHERE space_id = $1 AND deleted_at IS NULL',
      [space.id]
    );
    const bothDone = doneBy.length >= Math.max(2, Number(members.rows[0].n));
    const doneAt = bothDone ? Number(current.done_at || ts) : null;
    const updated = await c.query(
      `UPDATE challenge_days SET done_by = $2::jsonb, done_at = $3, updated_at = $4, version = version + 1
        WHERE id = $1 RETURNING *`,
      [current.id, JSON.stringify(doneBy), doneAt, ts]
    );
    if (bothDone && !current.done_at) {
      await c.query(
        `INSERT INTO couple_events (id, space_id, from_user, from_name, type, body, created_at, updated_at, version)
         VALUES ($1,$2,$3,$4,'challenge_done','',$5,$5,1)`,
        [uuid(), space.id, ctx.user.id, ctx.user.nickname || '', ts]
      );
    }
    return updated.rows[0];
  });
  return { challenge: mapChallenge(row, ctx.user.id) };
}

async function eventSend(ctx, payload) {
  const space = await spaceOfUser(ctx.user.id);
  if (!space) throw badRequest('请先绑定共同空间');
  await assertMember(ctx.user.id, space.id, { requireActive: true });
  const type = String(payload.type || 'poke').trim().slice(0, 32) || 'poke';
  const body = String(payload.body || '').trim().slice(0, 200);
  const id = uuid();
  const ts = now();
  await q(
    `INSERT INTO couple_events (id, space_id, from_user, from_name, type, body, created_at, updated_at, version)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$7,1)`,
    [id, space.id, ctx.user.id, ctx.user.nickname || '', type, body, ts]
  );
  return { id, createdAt: ts };
}

async function eventPull(ctx, payload) {
  const cursor = Number(payload.cursor || 0) || 0;
  const space = await spaceOfUser(ctx.user.id);
  if (!space) return { events: [], cursor, hasMore: false };
  const limit = 50;
  const { rows } = await q(
    `SELECT * FROM couple_events
      WHERE space_id = $1 AND created_at > $2 AND from_user <> $3
      ORDER BY created_at ASC LIMIT $4`,
    [space.id, cursor, ctx.user.id, limit + 1]
  );
  const hasMore = rows.length > limit;
  const page = rows.slice(0, limit);
  return {
    events: page.map((r) => ({
      id: r.id, type: r.type, body: r.body, fromUser: r.from_user,
      fromName: r.from_name, createdAt: Number(r.created_at),
    })),
    cursor: page.length ? Number(page[page.length - 1].created_at) : cursor,
    hasMore,
  };
}

const MEDIA_MAX_BYTES = 250 * 1024; // 单张图片压缩后上限，base64 约 340KB
const MEDIA_FETCH_BATCH = 4;

async function mediaUpload(ctx, payload) {
  const id = String(payload.id || '').trim();
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(id)) throw badRequest('图片标识不合法');
  const mime = String(payload.mime || 'image/jpeg');
  if (!/^image\//.test(mime)) throw badRequest('只支持图片');
  const data = String(payload.data || '').replace(/^data:image\/[a-z]+;base64,/, '');
  if (!data) throw badRequest('图片内容为空');
  const bytes = Math.ceil((data.length * 3) / 4);
  if (bytes > MEDIA_MAX_BYTES) throw badRequest('图片过大，请压缩后重试');
  const space = await spaceOfUser(ctx.user.id);
  const ts = now();
  await q(
    `INSERT INTO media_blobs (id, owner_id, space_id, mime, bytes, data, created_at, updated_at, version)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$7,1)
     ON CONFLICT (id) DO UPDATE SET mime = $4, bytes = $5, data = $6, updated_at = $7, version = media_blobs.version + 1
     WHERE media_blobs.owner_id = $2`,
    [id, ctx.user.id, space ? space.id : null, mime, bytes, data, ts]
  );
  return { id, bytes };
}

async function mediaFetch(ctx, payload) {
  const ids = Array.isArray(payload.ids) ? payload.ids.map(String).slice(0, MEDIA_FETCH_BATCH) : [];
  if (!ids.length) return { items: [] };
  const { rows } = await q(
    `SELECT b.id, b.mime, b.data FROM media_blobs b
      WHERE b.id = ANY($1::text[])
        AND (b.owner_id = $2 OR b.space_id IN
             (SELECT space_id FROM space_members WHERE user_id = $2 AND deleted_at IS NULL))`,
    [ids, ctx.user.id]
  );
  return { items: rows.map((r) => ({ id: r.id, mime: r.mime, data: r.data })) };
}

// ---------------------------------------------------------------- 路由
const actions = {
  'auth.register': { handler: register, public: true },
  'auth.login': { handler: login, public: true },
  'auth.refresh': { handler: refresh, public: true },
  'auth.logout': { handler: logout },
  'auth.me': { handler: me },
  'auth.changePassword': { handler: changePassword },
  'users.updateAvatar': { handler: updateAvatar },
  'profile.updateAvatar': { handler: updateAvatar },

  'space.create': { handler: spaceCreate },
  'space.update': { handler: spaceUpdate },
  'space.get': { handler: spaceGet },
  'space.generateBindCode': { handler: spaceGenerateBindCode },
  'space.joinByCode': { handler: spaceJoinByCode },
  'space.confirmBind': { handler: spaceConfirmBind },
  'space.unbindRequest': { handler: spaceUnbindRequest },
  'space.confirmUnbind': { handler: spaceConfirmUnbind },
  'space.rejectUnbind': { handler: spaceRejectUnbind },
  'space.cancelUnbind': { handler: spaceCancelUnbind },
  'space.rebind': { handler: spaceRebind },

  'challenge.get': { handler: challengeGet },
  'challenge.mark': { handler: challengeMark },
  'event.send': { handler: eventSend },
  'event.pull': { handler: eventPull },

  'content.list': { handler: contentList },
  'content.create': { handler: contentCreate },
  'content.update': { handler: contentUpdate },
  'content.delete': { handler: contentSoftDelete },
  'content.restore': { handler: contentRestore },

  'sync.pull': { handler: syncPull },
  'sync.push': { handler: syncPush },

  'recycle.list': { handler: recycleList },
  'recycle.purge': { handler: recyclePurgeRequest },

  'notification.list': { handler: notificationList },
  'notification.markRead': { handler: notificationMarkRead },

  'settings.get': { handler: settingsGet },
  'settings.put': { handler: settingsPut },

  'media.list': { handler: mediaList },
  'media.prepare': { handler: mediaPrepare },
  'media.complete': { handler: mediaComplete },
  'media.upload': { handler: mediaUpload },
  'media.fetch': { handler: mediaFetch },

  'backup.create': { handler: backupCreate },
};

async function dispatch(action, payload, token) {
  const entry = actions[action];
  if (!entry) throw badRequest('未知接口：' + action);
  if (entry.public) return entry.handler(payload || {});
  const ctx = await requireSession(token);
  return entry.handler(ctx, payload || {});
}

module.exports = { dispatch, actions };
