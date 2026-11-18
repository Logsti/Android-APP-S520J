# S520J CloudBase 后端与工程配置通读笔记

> 范围：`cloudbase/db/001~006`、`cloudbase/functions/**`、`cloudbase/scripts/*`、两个 `*.test.js`、根目录 `cloudbaserc*.json`、`gradle/libs.versions.toml`、`gradle.properties`、`settings.gradle.kts`、`app/build.gradle.kts`、`scripts/*.ps1`。
> 只读通读，未修改任何业务源码。行号均指当前工作区文件。

---

## 1. 运行形态

| 项 | 结论 |
|---|---|
| 运行时 | `Nodejs20.19`，handler `index.main`，云函数根 `cloudbase/functions`（`cloudbaserc.json:5-10`） |
| 超时 | 普通函数 `20s`；`dishSync` 生成配置里为 `60s`（`gen-cloudbaserc.js:34`） |
| PG 访问 | 不走直连驱动。统一 POST `https://{ENV_ID}.api.tcloudbasegateway.com/v1/rdb/exec-pgsql`，body `{sql, parameters, role}`，Bearer 为服务端 API Key（`_shared/core.js:11-15,29-68`） |
| 角色 | `CB_PG_ROLE` 默认 `cloudbase_postgres`（`core.js:13`）；DDL/DML 必须显式传管理角色，否则落只读事务（`core.js:6-7`） |
| 语句限制 | exec-pgsql 单次仅一条语句，无 BEGIN/COMMIT；`withTx()` 只是顺序执行同名兼容层（`core.js:70-82`） |
| 单请求超时 | 网关调用 `AbortSignal.timeout(8000)`（`core.js:15`），即每次 SQL ≤8s，与函数 20s 超时叠加 |
| 结果形态 | 仅接受 JSON 数组为 rows，否则视为空（`core.js:60-67`）；唯一键冲突 `DATABASE_23505` → 409（`core.js:57`） |

### 入口三形态（`_shared/entry.js`）

1. 云函数直调：`event = { action, payload, token }`；
2. HTTP JSON body：`event.body`（支持 `isBase64Encoded`）；
3. HTTP `Authorization: Bearer <accessToken>`。

`createEntry(groups)` 先按 `action.startsWith(group + '.')` 做前缀门禁，不匹配直接返回 `{code:404}`，不进入 dispatch（`entry.js:41-43`）。命中后 `dispatch(action, payload, token)`，成功 `{code:0,message:'ok',data}`，业务异常按 `e.status` 返回 `code`，未知异常 500 且只打日志（`entry.js:44-51`）。

### action 前缀路由

所有 action 集中注册在 `_shared/handlers.js:1076-1132` 的 `actions` 表；`dispatch()` 查表，`public: true` 免鉴权，其余先 `requireSession(token)`（`handlers.js:1134-1140`）。函数与允许前缀：

| 函数 | 前缀 |
|---|---|
| auth | `auth` `users` `profile` |
| space | `space` `challenge` `event` |
| content | `content` `dish` |
| sync / media / recycle / notification / settings / backup | 各自同名 |
| dishSync | 无 action，`main` 直调 `dishSyncInternal` |

注意：前缀门禁是字符串前缀，不是“该函数只实现这些 action”的强隔离；`actions` 是全量表，任何函数只要前缀匹配就能执行对应处理器。

### 鉴权（token hash → auth_sessions）

- 登录/注册发放 `accessToken` + `refreshToken`（各 32 字节 hex），库里只存 sha256（`handlers.js:148-159`）。
- 校验：`SELECT ... FROM auth_sessions s JOIN users u ON u.id=s.user_id WHERE s.access_token_hash=$1 LIMIT 1`，再判 `revoked`、`expires_at`（`core.js:131-146`）。
- TTL：access 2h、refresh 30d（`core.js:115-116`）；`auth.refresh` 吊销旧会话后新发一对（`handlers.js:48-62`）。
- 空间权限：`assertMember()` JOIN `spaces` 判 `deleted_at`/`status`（冻结只能读），`canRead()` 按 owner / visibility=couple 判可见（`core.js:161-189`）。
- 内容写权限二次校验集中在 `assertWriteAllowed()`：非本人写 couple 内容必须仍是空间成员，且不能把 couple 改成 private（`handlers.js:565-579`）。

---

## 2. 数据库（001~006）

约定：text 主键、时间 `bigint` 毫秒、内容表统一 `space_id/owner_id/visibility/deleted_at/version`，全部 `IF NOT EXISTS`，可重复执行（`001_schema.sql:1-6`）。

### 001_schema.sql

| 表 | 关键字段 | 索引 |
|---|---|---|
| users | `account_id`(唯一)、`recovery_salt/hash`、`couple_start_at`、`avatar` | `ux_users_account_id`、`ix_users_updated_at` |
| auth_sessions | `access_token_hash`、`refresh_token_hash`、`expires_at`、`refresh_expires_at`、`revoked` | user / access / refresh / expires 四个索引（均非唯一） |
| spaces | `status`(active/frozen)、`created_by` | `ix_spaces_status`、`ix_spaces_updated_at` |
| space_members | `role`(creator/partner)、`joined_at` | `ux_space_members_pair(space_id,user_id)`、`ix_space_members_user` |
| bind_codes | `code_hash`、`expires_at`、`status`(active/used/expired) | hash、space |
| bind_requests | `status`(pending/confirmed/failed，实际还写 unbind_pending/rebind_pending/rejected/cancelled)、`confirmed_by_a/b` | space、status |
| contents | `module`、`visibility`、`title/content`、`event_date`、`media_ids jsonb`、`extra jsonb` | module、space+updated、owner+updated、updated_at、deleted_at |
| media_assets | `storage_key/thumb_key`、`status`(pending/uploaded/failed)、`record_id` | record / space / owner |
| recycle_bin | `record_type+record_id`(唯一)、`deleted_by`、`purge_requested_by/confirmed_by`、`expire_at` | expire、space、唯一记录对 |
| conflict_snapshots | `field_json`、`source_user`、`expire_at` | record、expire |
| sync_logs | `user_id+device_id`(唯一)、`cursor`、`last_sync_at` | 唯一对 |
| backup_jobs | `status`、`file_key` | user |
| settings | `scope`(user/space)、`owner_id`、`space_id`、`key`、`value jsonb`；唯一索引 `(scope,owner_id,space_id,key)` | 唯一组合 |
| notifications | `user_id`、`type`、`read`、`space_id` | user+created、user+read |

`recycle_bin` 用 `deleted_at_col` 而非 `deleted_at`（`001:162`），与其他表命名不一致。

### 002_rls.sql（权限模型）

1. `DROP TABLE IF EXISTS s520j_probe` 清理探测表。
2. 收回 `PUBLIC/anon/authenticated` 对 public schema 全部表权限，并改默认权限（`002:16-24`）。
3. 授予 `service_role` USAGE + ALL + 默认权限（`002:27-29`）。
4. 14 张表 `ENABLE ROW LEVEL SECURITY`。
5. 每表一条 `AS RESTRICTIVE FOR ALL TO anon, authenticated USING(false) WITH CHECK(false)` 的显式拒绝策略（`002:63-76`）——兜底证明“直连必失败”，service_role 靠 BYPASSRLS 绕过。
6. 辅助函数 `s520j_is_space_member(text,text)`，注释说明“云函数为权威校验，此函数供运维排查”（`002:80-84`）。

结论：真正的权限边界在云函数里的业务校验，RLS 只是拒绝直连的兜底；`service_role`/管理角色本身不受策略约束。

### 003_couple_live.sql

- `challenge_days`：`(space_id, day)` 唯一（`day` 为本地 epochDay），`prompt_index`、`done_by jsonb`、`done_at`。
- `couple_events`：`space_id/from_user/from_name/type/body`，索引 `(space_id, created_at)`。

### 004_media_sync.sql

- `ALTER TABLE contents ADD COLUMN IF NOT EXISTS media_refs jsonb DEFAULT '[]'`。
- `media_blobs`：`id`(客户端 UUID)、`owner_id`、`space_id`、`mime`、`bytes`、`data`(base64，不含前缀)、`version`，索引 space。
- 明确说明“当前未开通对象存储，图片压到 ≤250KB 后 base64 存 PG，后续只换 media.upload/fetch 实现”（`004:2-3`）。

### 005_reset_accounts_once.sql

单条 `TRUNCATE ... CASCADE` 清 17 张表 + 17 行 `COUNT(*)` 验证（`005:10-47`）。不写 BEGIN/COMMIT，因为 run-sql 逐条提交（`005:6`）。文件头标注“危险、不可恢复、仅限核对 local.properties 环境后手动执行”（`005:1-8`）。

### 006_dish_pool.sql

| 表 | 说明 | 索引 |
|---|---|---|
| dishes | `name+level1/level2+group_name(meat/veg/soup/staple)+path+source(live/sync)+first/last_seen_at` | `ux_dishes_name_path(name,path)`、`(group_name,level2)`、`last_seen_at DESC` |
| dish_quota | `day text PK`（北京日期 YYYY-MM-DD）、`used int`，跨实例共享配额 | 主键即唯一 |
| dish_meta | `key PK`、`value jsonb`、`updated_at`：分类树缓存 / 页码上限 / 同步游标 / 最后同步时间 | 主键 |

三表“只服务点菜，不参与内容同步、回收站与备份”（`006:3`）。

---

## 3. 各云函数承载的 action

> 依据 `handlers.js:1076-1132` 全量表 + 各 `index.js` 前缀。

### auth（`functions/auth/index.js:1-2`，前缀 auth/users/profile）

| action | 行为 | 表 |
|---|---|---|
| auth.register | 校验昵称/账号/密码 → 查重 → PBKDF2 建用户 → 发会话 | users, auth_sessions |
| auth.login | 账号查用户 → 比对 hash → 发会话（错误信息区分“账号不存在/密码不正确”） | users, auth_sessions |
| auth.refresh | 按 refresh_token_hash 查会话 → 吊销旧的 → 新发一对 | auth_sessions, users |
| auth.logout | 按 sessionId 置 revoked | auth_sessions |
| auth.me | 返回 publicUser + 首个空间 | users, space_members, spaces |
| auth.changePassword | 校验旧密码 → 换 salt/hash，version+1 | users |
| users.updateAvatar / profile.updateAvatar | 校验 `data:image/(png/jpeg/webp);base64` 且 ≤100KB，整串存 avatar | users |

### space（前缀 space/challenge/event）

| action | 行为 | 表 |
|---|---|---|
| space.create | 每账号仅一个空间；顺序写 spaces + space_members(creator)（无事务） | spaces, space_members |
| space.update | 成员+active 校验后改 name/intro | spaces |
| space.get | 空间 + 成员列表 + 有效绑定码过期时间 + 最近待处理申请 | spaces, space_members, users, bind_codes, bind_requests |
| space.generateBindCode | 6 位（去易混字符）绑定码，hash 入库，24h；先过期旧码 | bind_codes |
| space.joinByCode | 按 code_hash 找码 → 有效期/状态/是否本人空间/成员数<2 → 归档自己的一人空间 → 建 pending 申请并发通知 | bind_codes, spaces, space_members, bind_requests, notifications |
| space.confirmBind | 双向确认：两人都确认后写 partner 成员、码置 used；单方确认只记 confirmed_by | bind_requests, space_members, bind_codes, notifications |
| space.unbindRequest | 复用已有 unbind_pending，否则新建并通知对方 | bind_requests, space_members, notifications |
| space.confirmUnbind | 仅 to_user 可确认；申请置 confirmed + spaces 置 frozen，通知发起方 | bind_requests, spaces, notifications |
| space.rejectUnbind / space.cancelUnbind | 仅 to_user / 仅 from_user，状态置 rejected / cancelled | bind_requests |
| space.rebind | 冻结空间复活：若对方已发起 rebind_pending 则直接置 confirmed+active，否则自己发起 | bind_requests, spaces, space_members |
| challenge.get | 取当天挑战（无空间则返回空态） | challenge_days, space_members, spaces |
| challenge.mark | `FOR UPDATE` 读/建行 → 追加自己到 done_by（幂等不可撤销）→ 全员完成写 done_at + couple_events | challenge_days, space_members, couple_events |
| event.send | 写 poke 等事件（type ≤32、body ≤200） | couple_events |
| event.pull | `created_at > cursor AND from_user <> me` 拉 50+1，返回 hasMore | couple_events |

### content（前缀 content/dish）

| action | 行为 | 表 |
|---|---|---|
| content.create | 幂等按 id：已存在且可读则原样返回 duplicated=true；否则插入（owner 取会话用户），WISH+couple 时通知对方 | contents, space_members, notifications |
| content.update | `baseVersion` 不一致 → 写 conflict_snapshots 并返回 server 版本（冲突合流）；否则白名单字段动态 SET，version+1；WISH 状态变化发 4 类通知 | contents, conflict_snapshots, notifications |
| content.delete | 软删 contents + upsert recycle_bin（30 天过期） | contents, recycle_bin |
| content.restore | 回滚 deleted_at + 删回收站行 | contents, recycle_bin |
| content.list | owner 本人 或 visibility=couple 且同空间；可选 modules 过滤，limit 默认 300 | contents, space_members |
| dish.list / dish.pick / dish.sync | 见第 5 节 | dishes, dish_quota, dish_meta |

### sync

| action | 行为 | 表 |
|---|---|---|
| sync.pull | 增量拉 `updated_at > cursor`（含 couple 同空间），另返回 `revokedIds`（原 couple 改 private 的他人记录，只回 id 不回内容） | contents, space_members |
| sync.push | 顺序执行 ops：UPSERT（存在则 update 否则 create）/SOFT_DELETE/RESTORE/PURGE，逐条返回 seq+ok；4xx 记录后继续，5xx 立即抛出 | contents, conflict_snapshots, recycle_bin, notifications |

### media

| action | 行为 | 表 |
|---|---|---|
| media.list | 本人 media_assets 最新 200 条，直接返回原始行 | media_assets |
| media.prepare | 固定抛 501「媒体上传将在 8C.1 接入」 | — |
| media.complete | 按 `owner_id+storage_key` 置 uploaded、写 size | media_assets |
| media.upload | id 白名单 `[A-Za-z0-9_-]{8,64}`、mime 必须 image/、base64 ≤250KB；upsert 且 WHERE owner_id 限本人 | media_blobs |
| media.fetch | 最多 4 个 id，本人或同空间可见 | media_blobs, space_members |

### recycle / notification / settings / backup

| action | 行为 | 表 |
|---|---|---|
| recycle.list | 我删的 或 我所在空间删的，LEFT JOIN contents 带 title/module | recycle_bin, contents, space_members |
| recycle.purge | couple 内容需双人确认（第一人记 requested，第二人确认后物理删）；private 直接物理删 | recycle_bin, contents, space_members |
| notification.list | 本人未删通知，按 created_at DESC 限 200 | notifications |
| notification.markRead | `WHERE user_id=$1 AND id=ANY($2)` 置 read | notifications |
| settings.get | scope=user 按 owner_id；scope=space 按当前空间 | settings, spaces, space_members |
| settings.put | user/space 两态；因 NULL 不参与唯一索引冲突，采用“先 UPDATE ... IS NOT DISTINCT FROM，再 INSERT” | settings, space_members |
| backup.create | 导出本人全部未删 contents 内联返回（不落文件、不传 file_key，job 直接 done） | contents, backup_jobs |

### dishSync（`functions/dishSync/index.js`）

`exports.main = main(event)` → `dishSyncInternal({pages})`，不经 action 路由、不鉴权；生成配置里定时器 `0 30 3 * * * *`（每天 03:30）。

---

## 4. `_shared` 复用机制

- 唯一真源：`cloudbase/functions/_shared/`，含 `core.js`(7280B)、`entry.js`、`handlers.js`(49060B)、`dishes.js`、`dishHandlers.js`、`dishPool.js`、`dishProvider.js`、`dishTaxonomy.js`。
- 各函数目录下的 `_shared/` 是**构建产物副本**，被 `.gitignore:22` 排除（`cloudbase/functions/*/_shared/`）。
- `prepare-functions.js` 做的事（每次部署前必跑）：
  1. 10 个函数目录 `mkdirSync`；
  2. **总是覆盖** `index.js`：默认生成 `createEntry([...allowed])`，`dishSync` 用 `customEntries` 生成直调入口（`prepare-functions.js:53-58`）；前缀映射 `auth/space/content` 写死（`:16-20`）；
  3. 总是覆盖 `package.json`（空 dependencies，依赖 Node20 内置 fetch）；
  4. `rm -rf <fn>/_shared` 后整目录递归复制全局 `_shared`（`:63-65`）。
- 约束根因：CloudBase 只上传函数目录内文件，无法 `require('../_shared')` 跨目录，因此必须物理复制；代价是 10 份副本，且副本不会自动同步。

当前工作区实际状态（重要）：

- 9 个非 dishSync 函数的 `_shared` 已存在，但 `handlers.js` 均为 **48863B**，全局为 **49060B**，内容不同（哈希不一致）；副本里**没有** `dishHandlers.js` 等菜品文件，也没有 `require('./dishHandlers')`，即 `content` 函数副本缺少 `dish.list/pick/sync`。
- `dishSync/` 目录下**完全没有** `_shared/`，当前 `index.js` 直接 `require('./_shared/dishHandlers')`，不跑 prepare 必然启动失败。
- 结论：本地 `_shared` 副本是过期残留；部署顺序必须先 `node cloudbase/scripts/prepare-functions.js`。

---

## 5. 菜品域

### 数据来源

- 第三方：ShowAPI「菜谱大全」`https://route.showapi.com`（`dishProvider.js:12`）。
  - `1164-2` 分类树，`1164-1` 按二级分类抽页（`type/page/maxResults`，`maxResults` 硬上限 50）。
  - AppKey 仅从 `process.env.SHOWAPI_APP_KEY` 读；未配置抛 503「菜谱服务未配置」（`:23-27`）。
  - 串行节流：相邻调用 ≥1000ms（模块级 `lastCallAt`，`:17,141-145`）；单次 fetch 8s 超时。
  - 响应结构多形态兼容：`showapi_res_body` 可能是数组 / `datas` / `data.datas` / `data.list`（`:79-90`）；分类树节点名/子节点兼容 `name|className|cname|title|type`、`list|childList|children|...`（`:36-47`）。
- 分类：`dishTaxonomy.js` 内置 9 个一级、四组映射；只按标签（`type/type_v1/type_v2/type_v3` 空格拆分）判定，不按菜名猜；冲突优先级 `soup > meat > veg > staple`（`:22-24,76-106`）。接口失败/为空时用 `BUILTIN_TAXONOMY` 兜底。
- 自建库：`dishes.js` 静态 90 道（荤 40 / 素 25 / 汤 10 / 主食 15），字段与官方二级分类对齐，离线也能凑一桌。

### 配额

- `DISH_DAILY_LIMIT` 默认 100、`DISH_RESERVE` 默认 20（`dishHandlers.js:26-34`）。
- 按北京日期（UTC+8）取 `YYYY-MM-DD`（`dishPool.js:18-20`）；`dish_quota` 以 day 为主键，**全局共享**，不区分用户/空间。
- 原子占用：`INSERT ... ON CONFLICT(day) DO UPDATE SET used = used + $2 WHERE used + $2 <= $3 RETURNING used`，并发超额拿不到行即失败（`dishPool.js:169-185`）。
- 实时抽页只消耗“超出保底”的额度；`remaining <= reserve` 时完全不走第三方（`dishHandlers.js:139-152,321,341-346`）。

### 同步游标与缓存

| 元数据 key | 内容 | 说明 |
|---|---|---|
| `dishTaxonomy` | `{level1:{一级:[二级]}}` | TTL 7 天（`dishPool.js:11,207-219`） |
| `dishPage:<level2>` | `{level2,page,allPage,at}` | 随机页码上限，默认 10、上限 1000（`dishHandlers.js:20-21,128-137`） |
| `dishSyncCursor` | `{level2,page,at}` | 每抓完一页写一次（`dishHandlers.js:436-437`） |
| `dishLastSyncAt` | `{at,fetched,inserted}` | 本轮汇总（`:446-447`） |

池子快照游标不是时间戳，而是 `last_seen_at:id` 复合游标（`dishPool.js:121-150`），按 `(last_seen_at,id) DESC` 翻页，单页 ≤1000。

### 随机推荐规则

- `dish.pick`：每组份数 0–4、总数 ≥1；最多对 3 个组做实时抽页、总调用 ≤3，组内空页最多重试 1 次（`dishHandlers.js:15-18,158-199`）。
- 每次抽页：随机二级分类（或用户指定 subCategory）→ 随机页码 → **整页 50 道全部入库**（source=live）→ 从乱序候选中取本组所需。
- 降级链：实时候选 → `dishes` 池（先排除最近点过，不足再允许最近菜）→ 内置 90 道；返回 `degraded/note/quotaRemaining`。
- 排除项 ≤500、单名 ≤40 字；`used` 集合保证一桌不重名。
- `dish.pick` 的实时抽页会顺带把整页菜品写进池子，因此“点菜”同时承担了增量入库。

### 定时任务

- `dishSync`：默认 4 页、上限 10 页（`dishHandlers.js:23-24`）；每页先 `tryConsume(1)`，`remaining <= reserve` 立即停；随机组→随机二级→随机页码→整页入库→写游标；失败（尤其 503）直接结束本轮；结束做 `trimPool()` 并写 lastSync；任何异常都返回 `{ok:false}` 不向调度器抛错（`:387-454`）。
- 池子上限 100000，超出按 `last_seen_at` 淘汰最久未出现（`dishPool.js:8,112-119`）。

---

## 6. 脚本链路

| 脚本 | 作用 | 何时用 |
|---|---|---|
| `cloudbase/scripts/init-pg-role.js` | 创建 PG 登录角色 `s520j_app`（随机 24 字节密码、`BYPASSRLS`）、授权、把 `cloudbase.pg*` 写回 `local.properties` | 一次性初始化（`init-pg-role.js:1-7`） |
| `gen-cloudbaserc.js` | 从 `local.properties` 读 `cloudbase.envId/apiKey`，生成 `cloudbaserc.json`：10 个函数、dishSync 60s + 定时触发器、9 条 `/api/<name>` 网关路由 | 配置变更 / 首次部署（`gen-cloudbaserc.js:17-58`） |
| `prepare-functions.js` | 生成/覆盖 `index.js` 与 `package.json`，复制全局 `_shared` 到每个函数目录 | **每次部署前**（`prepare-functions.js:47-66`） |
| `run-sql.js` | 逐文件按 `;` 拆分、过滤 BEGIN/COMMIT、逐条 POST exec-pgsql（role=cloudbase_postgres）；SELECT 结果原样打印 | 初始化库 / 迁移（`run-sql.js:34-57`） |

推荐顺序：`init-pg-role`（一次）→ `gen-cloudbaserc` → `prepare-functions` → `run-sql 001 002 003 004 006`（005 仅在核对环境后手动）→ `tcb fn deploy` / `tcb framework deploy`。

注意脚本间不自洽的点：
- `init-pg-role` 创建的 `s520j_app` 角色**没有任何调用方**；云函数统一用 `CB_PG_ROLE=cloudbase_postgres`（`cloudbaserc.json:14`、`gen-cloudbaserc.js:42`）。
- `run-sql.js` 的“按分号拆分”是朴素 split：实测 002 被拆成 56 条，**行内注释会与后续语句粘成同一条**提交给网关；当前脚本恰好没有字符串内分号/`$$` 函数体，一旦出现就会拆断（潜在脆弱点，非当前故障）。
- `gen-cloudbaserc.js` 不会写入 `SHOWAPI_APP_KEY / DISH_DAILY_LIMIT / DISH_RESERVE`（注释说由控制台单独配置，`:38`），而 `cloudbaserc.example.json:22-29` 是有的。
- 当前工作区 `cloudbaserc.json` 只有 9 个函数、9 条路由、无 `dishSync`、无定时器、也无 `SHOWAPI_APP_KEY` —— 明显是 gen 脚本升级前的旧产物。

### `scripts/build_apk.ps1`

参数 `-Tag`（默认 dev）、`-Release`、`-Jdk`、`-Sdk`。步骤：
1. 解析 JDK：显式 `-Jdk` → 便携 `C:\Users\song9\.local\share\android-toolchain\jdk17` → `$env:JAVA_HOME` → `C:\Program Files\Eclipse Adoptium\jdk-17...`；
2. 解析 SDK：显式 `-Sdk` → 便携 `...\android-toolchain\sdk` → `C:\Android\Sdk`；
3. 校验 `jdk\bin\java.exe`、`sdk\platforms`，设置 `JAVA_HOME/ANDROID_HOME/ANDROID_SDK_ROOT/PATH`；
4. 由 `-Tag` 里的 `vX.Y.Z` 计算 versionCode（`X*1000+Y*100+Z`）；
5. 调 `gradlew.bat assembleDebug|assembleRelease -Ps520jVersionName -Ps520jVersionCode --no-daemon`；
6. 复制 `app/build/outputs/apk/<variant>/app-<variant>.apk` → `dist/S520J-<tag>-<variant>.apk`，并写 `dist/build-info.txt`（tag/variant/versionCode/builtAt/size/jdk/sdk）。

### `scripts/setup_toolchain.ps1`

一次性无管理员安装：Microsoft JDK 17（`aka.ms/download-jdk`）、Android cmdline-tools `11076708`，写入 5 个 SDK license 文件，`sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"`；输出根目录 `C:\Users\song9\.local\share\android-toolchain`。

---

## 7. 依赖与版本

`gradle/libs.versions.toml` + `app/build.gradle.kts`：

| 项 | 版本 |
|---|---|
| AGP | 8.7.3 |
| Kotlin | 2.0.21（含 compose / serialization 插件同版本） |
| KSP | 2.0.21-1.0.28 |
| Hilt | 2.52；hilt-navigation-compose 1.2.0 |
| Compose BOM | 2024.10.01；material3 / icons-extended 走 BOM |
| Room | 2.6.1（runtime/ktx/compiler/testing） |
| Retrofit | 2.11.0；OkHttp logging 4.12.0；kotlinx-serialization-json 1.7.3；retrofit2-kotlinx-serialization-converter 1.0.0 |
| DataStore | 1.1.1 |
| Coil | 2.7.0 |
| WorkManager | 2.9.1 |
| Coroutines | 1.9.0 |
| Navigation | 2.8.4；Lifecycle 2.8.7；Activity Compose 1.9.3；core-ktx 1.13.1 |
| 测试 | JUnit 4.13.2、MockK 1.13.13、androidx.test.ext:junit 1.2.1 |

SDK/编译：`compileSdk=35`、`minSdk=26`、`targetSdk=35`、`buildToolsVersion="36.0.0"`、Java/Kotlin JVM target 17、namespace/applicationId `com.s520j.app`、`buildConfigField CLOUD_BASE_URL` 默认 `https://lin-run-forward-d3fjemwrbf5f4738-1527676157.ap-shanghai.app.tcloudbase.com`（`app/build.gradle.kts:10-29`）。

`gradle.properties`：`-Xmx2048m`、`MaxMetaspaceSize=512m`、caching/parallel、`android.useAndroidX=true`、`nonTransitiveRClass=true`、`sdkDownload=false`、`android.overridePathCheck=true`（中文路径 Ai编程）。
`settings.gradle.kts`：仓库 google/mavenCentral/gradlePluginPortal，`FAIL_ON_PROJECT_REPOS`，单模块 `:app`。

---

## 8. 测试

### `cloudbase/dish-rules.test.js`（`node --test`）

用内存 Map + 伪造 `q()` 拦截 `require.cache` 替换 `_shared/core`，再 stub `dishProvider`。覆盖：

1. 内置分类树兜底：四组映射（猪/鱼→meat、蛋类→veg、汤羹→soup、米面类→staple），烘焙/甜品/饮品/干果/腌咸/专题/口味/器具必须不映射；多级标签空格拼接；冲突优先级 soup>meat>veg>staple；只用标签不按菜名猜（`cpName:'红烧肉'` → null）；`groupsFromTaxonomy` 过滤空分类。
2. 自建库自洽：90 道无重名、分组合法、二级分类与分组/一级分类一致、各组数量下限（40/25/10/15）、每组 ≥2 个二级且 ≥4 道。
3. 供应商解析：`parseTaxonomy` 扁平/`childList`/空分类；`parseDishPage` 扁平 `datas` 与嵌套 `data.datas`；`path` 拼接；`allPage/allNum`；`normalizeDish` 优先级。
4. 客户端行为：AppKey 只出现在 URL query、不出现在表单体；`maxResults` 被压到 50；相邻请求 ≥990ms；`showapi_res_code != 0`、`flag:false`、`ret_code!=0`、网络异常统一 503；未配置 AppKey 503 且 message 含“未配置”。
5. 池子：同批去重、重复只更新 `last_seen_at`、501 条拆 2 批、非法 group 丢弃；随机抽样按组/二级/排除项；快照三页游标翻页且末页 nextCursor=null；`trimPool(2)` 只留最新 2 条。
6. 配额：`tryConsume` 原子自增、超额不写库、0 次不扣。
7. 接口层：`dish.list/pick/sync` 已注册且非 public；`dish.list` 仅首页下发 categories/builtin；`dish.pick` 最多 3 次实时调用、返回顺序按 meat→veg→soup→staple、整页 150 道入库、返回值不含 AppKey；空页重试不突破 3 次；剩余 19<保底 20 时 0 次实时、池子优先自建库兜底且不占保底；份数/分组/二级校验；`randomPage` 边界。
8. `dish.sync`：额度不足跳过；4 页 × 50 入库、写游标与 lastSync；SQL 异常返回 `{ok:false}`，`dishSyncInternal` 同样不抛。

### `cloudbase/notification-rules.test.js`

伪造 `q` + `assertMember/spaceOfUser/canRead` 的桩，验证：

- 心愿 4 类动态（`wish.proposed/accepted/rejected/realized`）只发给另一方，不发自己；重复 create、同状态重试均不新增（id 由 `objectId+stateVersion+recipient` 决定 + `ON CONFLICT DO NOTHING`）；改标题/正文等非状态编辑不发通知。
- 空间：绑定申请只通知被申请方、重试复用同一 request 不重复通知；单方 confirmBind 不算成功事件，双方确认后通知加入方 `space.bound`；重复确认报错且不新增；`space.update` 不发通知；unbindRequest 只通知对方且重复请求不重复通知；confirmUnbind 冻结后通知发起方；重复 confirmUnbind 拒绝。
- `LETTER/PHOTO/JOT/NOTE` 等模块创建内容不产生云端通知。

---

## 9. 风险与技术债清单

### 密钥 / 凭据 / 硬编码

| 位置 | 问题 |
|---|---|
| `cloudbaserc.json:13,24,35,...` | 明文写入 service_role 级 API Key（JWT 内含 `"role":"service_role"`、`is_system_admin:true`、exp≈253402300799 即 9999 年）；虽被 `.gitignore:32` 排除，但当前文件真实存在且 9 处重复，极易误传/误贴 |
| `cloudbase/scripts/gen-cloudbaserc.js:39-43` | 把同一 API Key 复制进 10 个函数的 envVariables，任何能读函数配置的人都拿到超长有效期管理员凭据 |
| `cloudbase/scripts/init-pg-role.js:23-26` | 硬编码数据库 host `30.251.13.209`、port `50975`、db `postgres-nemdgrxi`，换环境必须改代码 |
| `cloudbase/scripts/init-pg-role.js:42` | 密码以字符串插值拼进 `CREATE ROLE ... PASSWORD '...'`（值本身随机，风险低，但模式不可复制） |
| `_shared/core.js:11` | `ENV_ID` 缺失时回退硬编码 `lin-run-forward-d3fjemwrbf5f4738`，误配环境不会报错而是打到旧环境 |
| `_shared/dishProvider.js:149` | AppKey 放在 URL query（`?appKey=`），网关/APM/代理日志可能记录完整 URL；代码注释声称“不写日志”只能约束自身 |
| `app/build.gradle.kts:14` | 默认 CloudBase 访问域名硬编码在构建脚本 |
| `scripts/build_apk.ps1:15,25` / `setup_toolchain.ps1:7` | 硬编码 `C:\Users\song9\...` 路径，当前机器用户是 `LINKUNSONG`，便携工具链分支永远不命中，只能靠 `-Jdk/-Sdk` 或 `JAVA_HOME` |

### 鉴权 / 越权 / 滥用

| 位置 | 问题 |
|---|---|
| `_shared/handlers.js:37-46` | 登录无失败次数限制/锁定/验证码，密码仅 8–32 位字母数字（`core.js:93`），可被在线爆破 |
| `_shared/handlers.js:21-22,41-43` | 注册查重与登录错误文案区分“账号不存在 / 密码不正确”，可枚举账号 ID |
| `_shared/core.js:89` | PBKDF2-SHA256 仅 10000 轮，按 2026 年标准偏低 |
| `_shared/handlers.js:1131` + `dishHandlers.js:462` | `dish.sync` 对任意已登录用户开放，单次最多 10 页，可被反复调用烧光全局第三方配额（配额不按用户隔离） |
| `_shared/handlers.js:287-305` | `space.joinByCode` 无尝试频率限制，6 位码（32^6）理论上可枚举 |
| `_shared/entry.js:41-43` | 前缀匹配即放行，`actions` 表全局共享；将来新增前缀重叠 action 时易出现跨函数越权 |
| `_shared/handlers.js:880-884` | `media.list` 直接返回 `media_assets` 原始行（含 `storage_key` 等内部字段），未做字段裁剪 |

### SQL / 事务 / 一致性

| 位置 | 问题 |
|---|---|
| `cloudbase/scripts/run-sql.js:34-41` | 朴素 `split(';')`：行内注释与后续语句被粘成一条提交，且遇到字符串内分号/`$$` 函数体会拆断；当前 001~006 侥幸可用，模式脆弱 |
| `_shared/core.js:74-82` | `withTx` 并非事务，只是顺序执行；多语句场景（建空间、绑定、解绑、恢复、purge）中途失败会留下半写状态，仅打日志“可能留下部分写入” |
| `_shared/handlers.js:643` | `UPDATE contents SET ${sets.join(', ')}` 为动态拼接（字段名白名单、值走参数，当前无注入，但模式脆弱） |
| `_shared/handlers.js:687` | `contentList` 用模板拼接 `AND module = ANY($2)` 片段；参数化安全，但与 `limit` 无上限一起构成可放大查询 |
| `_shared/handlers.js:726-753` | `sync.push` 的 ops 数量无上限、逐条串行 SQL；遇到 5xx 立即抛错，已成功写入的前序 op 结果不会返回给客户端 |
| `_shared/handlers.js:618-626` | 冲突只写 `conflict_snapshots` 并回传 server 版本，无自动合并；快照表无清理任务，仅靠 `expire_at` 字段 |
| `_shared/dishPool.js:100-107` | `ORDER BY random()` 对最多 10 万行的 dishes 全表排序，池子变大后单次点菜延迟上升 |
| `cloudbase/db/006_dish_pool.sql:24-27` | `dish_quota` 每天一行、永不清理（量小但无归档策略） |
| `cloudbase/db/001_schema.sql:162` | `recycle_bin.deleted_at_col` 命名与其他表 `deleted_at` 不一致，易误用 |
| `cloudbase/db/005_reset_accounts_once.sql:10-28` | 破坏性 TRUNCATE 脚本与常规迁移同目录，误执行即清空全环境账号数据 |

### 部署 / 配置漂移

| 位置 | 问题 |
|---|---|
| `cloudbase/functions/*/_shared/handlers.js` | 9 份副本均为 48863B，全局为 49060B，且副本缺 `dishHandlers.js` 与 dish 路由；不跑 prepare 直接部署会丢菜品接口 |
| `cloudbase/functions/dishSync/index.js:6` | 目录内无 `_shared/`，当前未 prepare 状态部署必然 `MODULE_NOT_FOUND` |
| `cloudbaserc.json:5-105` | 缺 `dishSync` 函数与定时触发器，且 content 无 `SHOWAPI_APP_KEY`、`DISH_DAILY_LIMIT/DISH_RESERVE`；按此部署 = 无每日同步、无实时抽页（只剩池子+内置） |
| `cloudbase/scripts/gen-cloudbaserc.js:45` | 定时 cron 为 7 段 `0 30 3 * * * *`，与常见 5/6 段格式不同，需确认 CloudBase 触发器解析规则 |
| `cloudbase/db/002_rls.sql:5` | 注释引用 `_shared/auth.js`，实际文件已并入 `core.js`，文档漂移 |
| `cloudbase/db/002_rls.sql:27-29` vs `cloudbaserc.json:14` | RLS 章节只给 `service_role` 授权，云函数实际用 `cloudbase_postgres`；`init-pg-role.js` 建的 `s520j_app` 无人使用，三套角色语义不一致 |
| `prepare-functions.js:13` vs `cloudbaserc.json` | 脚本管 10 个函数，实际配置文件只有 9 个，二者长期不同步 |

### 日志 / 数据暴露

| 位置 | 问题 |
|---|---|
| `_shared/core.js:55` | 记录网关返回的 `code/message`，DB 错误 message 可能带 SQL 片段/表名，进入云端日志 |
| `_shared/entry.js:49` | 未捕获异常整对象打日志（含 event 上下文，可能含 token/payload 内容） |
| `_shared/dishHandlers.js:42-46` | 受控日志只输出自定义文案与 status，相对安全；但 `dishProvider.js:157` 仍记录了 endpoint 名 |
| `cloudbase/db/004_media_sync.sql:15` | 图片 base64 存 PG，`media.upload` 单张 ≤250KB、`media.fetch` 每次 ≤4 张；`media.assets` 与 `media_blobs` 两套媒体表并存，语义重复 |
| `_shared/handlers.js:900-921` | `backup.create` 内联返回全部内容且不落文件，大数据量时响应体可能撑爆网关限制，也未写 `file_key` |
| `_shared/handlers.js:1015-1035` | `event.pull` 只按 `created_at > cursor` 分页，同毫秒多事件可能被跳过（缺 id 二级排序） |
| `_shared/handlers.js:680-692` | `contentList` 的 `limit` 直接取客户端数值，无上限（可一次拉全表） |

### 工程 / 构建

| 位置 | 问题 |
|---|---|
| `app/build.gradle.kts:19` vs `setup_toolchain.ps1:63` | 工程要求 `buildToolsVersion 36.0.0`，安装脚本只装 `35.0.0`，且 `sdkDownload=false`，按脚本装完仍可能构建失败 |
| `app/build.gradle.kts:35-38` | release 未开启 minify/混淆，也未配置签名，`assembleRelease` 产物不可直接分发 |
| `gradle.properties:8` | `android.overridePathCheck=true` 是为中文路径开的绕过项，属长期技术债 |
| `cloudbase/functions/*/package.json` | 全部空依赖，完全依赖 Node20 内置 fetch；一旦运行时降级/变更即整体不可用 |
| `_shared/handlers.js:877-898` | media/backup 仍是半成品（`media.prepare` 恒 501、`media.complete` 无校验、`backup` 无文件），接口已暴露给客户端 |
