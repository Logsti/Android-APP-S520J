# Android 客户端：数据与基础设施层通读笔记

> 范围：`app/src/main/java/com/s520j/app/data/**`、`di/`、`notifications/CoupleNotifier.kt`、`util/*Compressor.kt`、`MainActivity.kt`、`S520JApplication.kt`、`app/src/test/java/com/s520j/app/data/**` 与 `util/**`。
> 路径前缀统一省略为 `data/…` / `di/…`（相对 `app/src/main/java/com/s520j/app/`）。本笔记只读不改。
> 技术栈：Room 7 / DataStore / Hilt / Retrofit + kotlinx.serialization / WorkManager / OkHttp。

---

## 1. Room

### 1.1 实体一览（`data/local/Entities.kt`）

| 实体 | 表名 | 主键 | 关键字段 | 索引 |
|---|---|---|---|---|
| `UserEntity` | `users` | `id` (String) | `nickname`、`accountId`、`recoverySalt`、`recoveryHash`、`avatar?`、`coupleStartAt`、`createdAt` | 无（`accountId` 查询靠全表扫，唯一性只由代码 `countByAccountId` 保证） |
| `SpaceEntity` | `spaces` | `id` | `name`、`intro`、`status`(ACTIVE/FROZEN)、`bindState`(枚举名)、`bindCode?`、`bindCodeExpiresAt?`、`createdBy`、`partnerUserId?`、`pendingAction?`、`bindRequestedBy?`、`unbindRequestedBy?`、`ownerConfirmedAt?`、`partnerConfirmedAt?`、`bindRequestId?` | 无（`findByBindCode` 无索引） |
| `SpaceMemberEntity` | `space_members` | 复合 `spaceId`+`userId` | `role`(OWNER/MEMBER)、`joinedAt` | 无 |
| `ContentEntity` | `contents` | `id` | `module`、`spaceId?`、`ownerId`、`ownerName`、`visibility`、`title`、`content`、`eventDate?`、`mediaIds`（本机路径，`\|` 分隔）、`mediaRefs`（云端 assetId，默认 `""`）、`extraJson`（`k=v;`）、`createdAt/updatedAt`、`deletedAt?`、`version` | `module`、`spaceId`、`deletedAt`（无 `updatedAt` 索引，而列表 `ORDER BY updatedAt DESC`） |
| `OutboxEntity` | `outbox` | 自增 `seq` | `entityId`、`op`(UPSERT/SOFT_DELETE/RESTORE/PURGE)、`createdAt`、`attempts`（实际未使用） | 无（`countByEntity` 全表扫） |
| `SyncConflictEntity` | `sync_conflicts` | `id`(UUID) | `entityId`、`serverJson`、`localJson`、`createdAt`、`expireAt` | 无（`purgeExpired` 全表扫） |
| `NotificationEntity` | `notifications` | `id` | `title`、`body`、`createdAt`、`read` | 无（`ORDER BY createdAt DESC`） |
| `MediaEntity` | `media_assets` | `id` | `recordId`、`type`、`localUri?`、`remoteKey?`、`thumbKey?`、`size`、`status`、`visibility` | 无，**且无任何 DAO，全项目零读写（死表）** |
| `CachedDishEntity` | `cached_dishes` | 自增 `id` | `name`、`level1`、`level2`、`group`、`lastSeenAt` | `name` 唯一、`group`、`lastSeenAt` |

补充：
- `BindState`（同文件）：`UNBOUND`/`INVITE_GENERATED`/`WAITING_PARTNER`/`REQUEST_PENDING`/`BOUND`/`FAILED`/`FROZEN`，带中文 `label`。
- `FieldCodec`（`Entities.kt:153`）：列表用 `|`、Map 用 `k=v;` 手写编码，**不做转义**（值里含 `|` / `;` / `=` 会破坏往返）。
- `ContentEntity.toRecord()/ContentRecord.toEntity()` 为双向映射，`ContentModule`/`Visibility` 由字符串 ↔ 枚举（`ContentModule.from` 未知值回退 `NOTE`）。
- `CachedDishEntity` 定位：只服务「今天吃啥」离线随机，不参与内容同步/备份/回收站。

### 1.2 `S520JDatabase`（`data/local/S520JDatabase.kt`）

- 数据库名 `s520j.db`，`version = 7`，`exportSchema = false`。
- 注册 9 个实体、7 个 DAO 入口（`contentDao`/`outboxDao`/`userDao`/`spaceDao`/`notificationDao`/`syncConflictDao`/`cachedDishDao`）。
- 迁移链（全部 `execSQL` 手写）：

| 版本 | 改动 |
|---|---|
| 1→2 | `spaces` 增 `pendingAction`、`bindRequestedBy`、`unbindRequestedBy`、`ownerConfirmedAt`、`partnerConfirmedAt` |
| 2→3 | 新建 `sync_conflicts`（`id/entityId/serverJson/createdAt/expireAt`） |
| 3→4 | `sync_conflicts` 增 `localJson TEXT NOT NULL DEFAULT ''` |
| 4→5 | `spaces` 增 `bindRequestId` |
| 5→6 | `contents` 增 `mediaRefs TEXT NOT NULL DEFAULT ''` |
| 6→7 | 新建 `cached_dishes` + 三个索引（`name` 唯一、`group`、`lastSeenAt`） |

- `create()` = `Room.databaseBuilder(...).addMigrations(...).fallbackToDestructiveMigration()`。

### 1.3 DAO 方法分组（`data/local/S520JDao.kt`）

| DAO | 分组与要点 |
|---|---|
| `ContentDao` | **观察**：`observeByModule`、`observeAll`、`observeRecycleBin`（Flow，均过滤/排序 `deletedAt`、`updatedAt`）；**读取**：`findById`、`listAll`、`listByModule`；**写**：`upsert`/`upsertAll`(REPLACE)、`update`；**删**：`clearAll`、`hardDelete`、`clearModule`；**统计/清理**：`count`、`purgeDeletedBefore`、`countPurgeable` |
| `OutboxDao` | `pending()`(seq 升序)、`observeCount`、`enqueue`、`remove(seq)`、`countByEntity`、`count` |
| `SyncConflictDao` | `upsert`、`list`/`observeAll`、`findById`、`deleteById`、`observeCount`、`purgeExpired(now)` |
| `UserDao` | `observeCurrentUser`/`currentUser`（`LIMIT 1` 无排序）、`findByAccountId`、`countByAccountId`、`upsert`、`clear` |
| `SpaceDao` | `observeSpace`/`currentSpace`（`LIMIT 1`）、`findByBindCode`、`upsert`、`addMember`、`members`、`clear` |
| `NotificationDao` | `observeAll`、`observeUnreadCount`、`upsert`(REPLACE)、`insertIfAbsent`(IGNORE，返回 -1 表示重复)、`markRead`、`markAllRead` |
| `CachedDishDao` | `upsertAll`(REPLACE)、`count`、`randomByGroup`、`randomByGroupAndLevel2`、`random`、`byNames`、`deleteOldest`、`clear` |

---

## 2. Repository

| 类 | 职责 | 关键公开方法 |
|---|---|---|
| `AuthRepository`（`data/repository/AuthRepository.kt`） | 本机账号闭环（注册/登录/改密/登出）；云端认证成功后由 `adoptCloudAccount` 落地本机，保证断网可同密码登录 | `register(accountId,nickname,recoveryCode,cloudUserId?)`（账号 ID 大写化 + `AccountIdRule` 8–20 位、密码 8–32 位、查重）、`adoptCloudAccount(...)`（以云端 userId 为主，保留本机 avatar/coupleStartAt/createdAt）、`login`、`changeRecoveryCode`（校验旧密码 → 换新盐重哈希）、`logout`、`hash()` |
| `ContentRepository`（`…/ContentRepository.kt`） | 内容读写 + outbox 入队 + 种子数据 | `observeModule`/`observeAll`/`observeRecycleBin`/`observeOutboxCount`、`get`、`save`（刷 `updatedAt` + 入队 `UPSERT`）、`softDelete`（写 `deletedAt` + `SOFT_DELETE`）、`restore`（清 `deletedAt` + `RESTORE`）、`purge`（硬删 + `PURGE`）、`count`、`migrateTaxonomyV2()`（`QuickRecordRouter.migrate`，按索引对比 module/extra 后批量写回）、`seedIfEmpty(ownerId,ownerName)` |
| `SpaceRepository`（`…/SpaceRepository.kt`） | 纯本地空间状态机（旧/降级路径），不访问网络 | `observeSpace`、`currentSpace`、`createSpace`（`afterCreate` + 6 位绑定码 + 24h TTL + OWNER 成员）、`shareInvite`、`refreshBindCode`、`submitBindCode`（返回 `BindSubmitResult(space,error)`）、`confirmBind`、`requestUnbind`、`confirmUnbind`、`rebind`、`freeze`、`isReadOnly` |
| `LocalResetCoordinator`（`…/LocalResetCoordinator.kt`） | v1.2.0 一次性本机环境重置的装配层 | `ensureCompleted()`；标记存独立 SharedPreferences（`s520j_one_time_reset` / `account_environment_reset_v1_completed`），清 `db.clearAllTables()` + `prefs.clearForOneTimeReset()` |
| `OneTimeReset`（`…/OneTimeReset.kt`，`internal`） | 纯协调逻辑：Mutex + 双重检查，**先清理成功再写标记** | `ensureCompleted()` |
| `DishSnapshotSync`（`…/DishSnapshotSync.kt`） | `dish.list` 分页写入 `cached_dishes`：断点续传、增量合并、5 万上限按 `lastSeenAt` 淘汰 | `start()`（后台 scope）、`sync()`、`reset()`、`candidates(group,level2,limit=8)`（快照优先 → 内置菜兜底）、`localCandidates`、`localCount`、`progress`/`categories`/`builtin` StateFlow；`DishSnapshotPolicy` 纯函数（`MAX_SNAPSHOT=50_000`、`trimCount`、`mergeBatch` 同页去重） |
| `SeedData`（`internal object`，`ContentRepository.kt:82`） | 23 条演示内容，`id = seed-<module>-<titleHash>`、`spaceId = "space-local"`；刻意不含「我方提出且待回应」的心愿 | `records(now,day,ownerId,ownerName)` |

说明：`AppViewModel` 实际走云端路径（`cloudSpace.*`），仅 `shareInvite`/`confirmBind`(旧数据兜底)/`requestUnbind`/`confirmUnbind`/`rebind`/`freeze` 仍走 `SpaceRepository`；`createSpace`/`submitBindCode`/`refreshBindCode` 未见调用方（旧本地路径残留）。

---

## 3. 云端访问

### 3.1 `CloudApi`（`data/cloud/CloudApi.kt`）

- 单一 Retrofit 方法：`@POST @Url path`，`@Header("Authorization")` + `@Body CloudRequest`，返回 `CloudResponse`。
- 请求体：`CloudRequest(action, payload: JsonObject = {})`；响应：`CloudResponse(code, message, data: JsonElement?)`，`code=0` 成功。
- `CloudRequestException(code, message)` 保留业务码，供同步层区分鉴权/权限/暂时失败。
- 端点约定（`path` 为云函数分组，`action` 为点号命名）：`api/auth`（auth.register/login/refresh/changePassword/logout/me、users.updateAvatar）、`api/space`（space.get/create/update/generateBindCode/joinByCode/confirmBind/unbindRequest/confirmUnbind/rejectUnbind/cancelUnbind、challenge.get/mark、event.send/pull）、`api/sync`（sync.push/pull）、`api/content`（dish.pick/dish.list）、`api/media`（media.upload/media.fetch）、`api/notification`（notification.list）、`api/settings`（settings.get/put）。
- 装配（`di/CloudModule.kt`）：`Json{ignoreUnknownKeys, explicitNulls=false, isLenient}`；OkHttp 连接 10s、读/写 25s；`baseUrl = BuildConfig.CLOUD_BASE_URL`。

### 3.2 四个云端仓库

| 类 | 职责 | token / 错误码处理 |
|---|---|---|
| `CloudAuthRepository` | 云端账号与会话：`register`/`login`（共用 `authenticate`，回写 `userId/accessToken/refreshToken` 与本机头像）、`ensureAccessToken(force)`、`resolveSpaceId()`（**绝不自动建空间**）、`resolveSpaceIdForSync(token)`（同步路径必须抛出 401/403）、`changePassword`、`updateAvatar(dataUrl)`、`fetchMe()`、`logout()`、`hasSession()` | `Mutex` 串行刷新；本地过期提前 5 分钟刷新；`TOKEN_TTL_MS=100min`；刷新仅 `code==401` 清会话（500 保留登录态）；`asObject()` 安全转型避免 `{"space":null}` 崩 |
| `CloudSpaceRepository` | 跨设备绑定：`refreshLocal()`（拉 `space.get` → `SpaceMirror.merge` → 本地镜像 + 对方昵称/头像）、`create`/`updateInfo`/`refreshCode`/`join`/`confirmBind`/`requestUnbind`/`confirmUnbind`/`rejectUnbind`/`cancelUnbind` | 全部走 `ensureAccessToken`；`guarded{}` 统一捕获（`IOException` → `offline=true`）；写操作仅在云端成功后落本地；云端无空间时清镜像，但 `REQUEST_PENDING` 时保留 |
| `DishHistoryRepository` | 点过的菜（`dish_history`，上限 500 条 / recent 100）：`local()`、`refresh()`（云端合并）、`record(items)`（同名只更新时间）、`setPreferNew`、`clear`、`syncPending()` | scope 优先 `space`、失败/未绑定回退 `user`；`pendingSync` 标记离线待补推；所有网络异常静默降级为本地 |
| `DishRandomRepository` | 点菜云端：`pick(DishPickRequest)`（groups/exclude/recent → items/quotaRemaining/degraded/note）、`listPage(cursor,limit≤1000)`（首屏额外带 `categories`、`builtin`） | 401 时 `ensureAccessToken(force=true)` 后重试一次；`IOException` 原样上抛；其余非 0 抛 `CloudRequestException`；`CloudRequestException(401,"未登录云端")` 表示无 token |

解析风格统一为手写 `JsonObject`/`JsonArray` 取值（`str()/asObject()/jsonPrimitive`），不用完整反射序列化。

---

## 4. 同步链路

### 4.1 `SyncRepository.syncNow(automatic)` 步骤顺序（`data/sync/SyncRepository.kt:89`）

1. `localReset.ensureCompleted()`（保证旧环境已清，避免旧 outbox 上传）。
2. `mutex.withLock`：快照 `cloudSession`（记录 `previousAccount`）。
3. `cloudAuth.ensureAccessToken()`：null → 若会话被清则按 401 失败提醒，否则 `OFFLINE("未登录云端")`。
4. 置 `SYNCING`；`cloudAuth.resolveSpaceIdForSync(token)`（拿不到会抛 403/401）。
5. `push(token, spaceId)` → 返回成功条数。
6. `pull(token, spaceId)` → 返回接收条数。
7. 独立 try：`notificationSync.pull(token, accountId)`（内容失败也照常收通知/冻结事件）。
8. 成功：`handleSyncRecovered` + `finish(IDLE,"已同步：上传 x 条，接收 y 条")`；失败：`handleSyncFailure` + 文案（冻结/401/403/通用）。
9. `finish()` 汇总 `pending=outbox.count`、`conflicts=list.size`、`lastSyncAt` 落盘。

### 4.2 push（outbox → `sync.push`）

- 读取 `outbox.pending()`（`seq ASC`）；`CloudPayload.isSeed(id)`（`seed-` 前缀）直接移除、不上传。
- 按 `op` 映射 action：`UPSERT` → `CloudPayload.pushOp`；`RESTORE`/`PURGE` → `deleteOp`；其它（含 `SOFT_DELETE`）→ `deleteOp(...,"SOFT_DELETE")`。
- **UPSERT 跳过规则**：实体已不存在则跳过但**保留 outbox 项**；`SyncPolicy.shouldDeferPush(visibility,spaceId)`（couple 且无 spaceId）也保留，等绑定后再推。
- `pushOp` 结构：`op/seq/entityId/baseVersion/record{…, patch:record}`（`baseVersion` 即乐观锁基线，等于本机 `version`）。
- `chunked(BATCH=30)` 调 `sync.push{ops}`。
- 逐条结果处理：`ok!=true` 且（非 4xx 或 401/403 或需账号提醒）→ 抛 `CloudRequestException`（保留 outbox）；业务 4xx → **移除 outbox（静默丢弃）**；`ok` → 移除；`conflict=true` → `saveConflict`（服务端版本落库 + 双方快照 30 天）；否则若该实体无剩余待推送项，则用 `decodeServerRecord` 回写服务端最新版本。

### 4.3 pull（`sync.pull` 增量）

- 游标：`prefs.syncCursorSnapshot()`（Long，DataStore `sync_cursor`），循环直到 `hasMore=false`，每轮写回 `data["cursor"]`。
- `revokedIds`：对方把内容改回仅自己后云端只回 id → 本机无待推送即 `hardDelete`。
- `records`：本机有未推送修改（`outbox.countByEntity>0`）则**不覆盖**；否则 `decodeRecord` → `mediaRefs` 经 `FieldCodec` 解码 → `mediaRepository.ensureLocal(refs)` 下载为本地路径 → 以 `mediaIds` 覆盖后 upsert。
- 无 `revokedIds`/`records` 时的 `cursor` 兜底为原游标（若服务端漏字段且 `hasMore=true` 会死循环，见风险表）。

### 4.4 各角色

| 角色 | 定位 |
|---|---|
| `SyncPolicy`（`data/sync/SyncPolicy.kt`） | 纯函数：`AUTO_SYNC_INTERVAL_MS=20s`、`shouldAutoSync`（时钟回拨返回 false）、`statusBarText`（仅 OFFLINE/FAILED 有文案）、`shouldDeferPush`、`autoSyncFailed`（连续 3 次 + 有待上传 + 未提醒 → 提醒一次）、`syncRecovered`（generation+1 使新周期可再提醒）、`isSpaceFrozenFailure`（403 且 message 含「冻结」）、`requiredAccountAlert`（401/403/账号不存在等） |
| `SyncApply`（`…/SyncApply.kt`） | `shouldApplyRemote(hasPending)=!hasPending`（**主流程未调用**，SyncRepository 内联同逻辑）、`conflictExpireAt(now)=now+30d` |
| `ConflictResolver`（`…/ConflictResolver.kt`） | `keepLocal(localJson,serverJson,now,fallbackSpaceId)`：保留本机内容、`version` 对齐云端（避免再次冲突）、`updatedAt=now`、`deletedAt=null`；`snapshot()` 供冲突页展示 |
| `RecyclePolicy`（`…/RecyclePolicy.kt`） | `RETENTION_DAYS=30`、`shouldPurge(deletedAt,now)`（纯函数，主流程改用 SQL `purgeDeletedBefore`） |
| `NotificationSyncRepository`（`…/NotificationSyncRepository.kt`） | `pull(token,accountId)`：`notification.list` → `insertIfAbsent`(IGNORE) 合并，**已读状态只以本机为准**；账号首次拉取（无基线）全部标已读且不发系统通知；`showLocal()` 供本地异常提醒；`localAlertId` = SHA-256(`account\0kind\0signature\0generation`) |
| `SyncWorker`（`…/SyncWorker.kt`） | `CoroutineWorker` + `@EntryPoint` 取 `SyncRepository`；`cleanupExpired()` → `syncNow(automatic=true)`；**永远 `Result.success()`**；周期 15 分钟、需网络、unique 名 `s520j-periodic-sync`、`ExistingPeriodicWorkPolicy.KEEP` |
| 清理 | `cleanupExpired(now)`：`contents` 中 `deletedAt <= now-30d` 硬删 + `sync_conflicts` 过期删除 |

### 4.5 冲突与失败可视化

- 冲突快照：`serverJson` + `localJson` + 30 天 `expireAt`；`resolveConflict(id, keepLocal)`：保留本机 → 重新 upsert + 入队 `UPSERT`；保留云端 → 只删快照（云端已在冲突时落库）。
- 自动同步失败节流：`SyncFailureTrack(accountId, consecutive≤3, signature, alerted, generation)` 存 DataStore；账号级异常（401/403/账号不可用）立即本地通知一次，不受 20s 节流。

---

## 5. 空间

### 5.1 `SpaceStateMachine`（`data/space/SpaceStateMachine.kt`）

状态：`UNBOUND`(未绑定) → `INVITE_GENERATED`(已生成邀请) → `WAITING_PARTNER`(等待对方加入) → `REQUEST_PENDING`(绑定/解绑申请中) → `BOUND`(已绑定) / `FAILED`(绑定失败) / `FROZEN`(冻结归档)。

| 触发 | 合法转换 |
|---|---|
| `afterCreate()` | → `INVITE_GENERATED`；仅 `state==null / UNBOUND / FAILED` 允许创建（`canCreate`） |
| `afterShare()` | `INVITE_GENERATED` 或 `WAITING_PARTNER` → `WAITING_PARTNER`（`SpaceRepository.shareInvite` 前置判断） |
| `afterSubmit(expired,occupied)` | 过期/已满 → `FAILED`（返回错误文案）；正常 → `REQUEST_PENDING` |
| `afterConfirm(BIND,o,p)` | 双方确认 → `BOUND`，否则 `REQUEST_PENDING` |
| `requestUnbind` | 仅 `BOUND` → `REQUEST_PENDING`（`pendingAction=UNBIND`） |
| `afterConfirm(UNBIND,o,p)` | 双方确认 → `FROZEN`（`status=FROZEN`），否则 `REQUEST_PENDING` |
| `afterRebind()` | `FROZEN` → `BOUND`（解冻、历史保留、清空码与申请） |
| `freeze()` | 任意 → `FROZEN`（登出语义） |

常量/规则：`BIND_CODE_TTL_MS=24h`；`isBindCodeExpired(expiresAt,now)` 边界即过期；`canEdit(state) = state != FROZEN`（null 视为可编辑）；`canExport` 恒 true；错误文案 `ERROR_CODE_NOT_FOUND / ERROR_CODE_EXPIRED / ERROR_SPACE_FULL`；`PendingAction{BIND,UNBIND}` 严格区分动作语义。

### 5.2 `SpaceMirror`（`data/space/SpaceMirror.kt`）

- 纯函数把云端 `space.get` 映射为 `SpaceEntity`：`Remote{id,name,intro,status,createdBy,members,codeExpiresAt,pending}`、`Member{userId,nickname,role,avatar}`、`BindRequest{id,fromUser,toUser,status,confirmedByA,confirmedByB}`。
- 合并优先级：`status!=active` → `FROZEN`；`pending.status=="pending"` → `REQUEST_PENDING(BIND)`（`confirmedByA→ownerConfirmedAt`、`confirmedByB→partnerConfirmedAt`，1L 仅作已确认标记）；`"unbind_pending"` → `REQUEST_PENDING(UNBIND)`；`members>=2` → `BOUND`；单人空间 → 有有效云端码则 `INVITE_GENERATED`，否则沿用本地 `SOLO_STATES{UNBOUND,INVITE_GENERATED,WAITING_PARTNER,FAILED}` 及本地绑定码，否则 `UNBOUND`。
- `A 端=创建者`、`B 端=加入者` 的假设贯穿全层。

### 5.3 `CoupleLiveRepository`（`data/couple/CoupleLiveRepository.kt`）

- 用途：共同空间的「每日挑战」与「双人事件」。`getChallenge(day)`、`markChallenge(day)`（`selfDone/partnerDone/doneAt` 由服务端按成员身份判定）、`sendPoke(text)`（成功以返回 `id` 非空判定）、`pullEvents()`（排除自己、按 `event_cursor` 递增并回写）。
- `CoupleEvent.TYPE_POKE="poke"`、`TYPE_CHALLENGE_DONE="challenge_done"`。
- 降级策略：未登录/未绑定/离线一律返回 `null` 或空列表，调用方退回本地能力（异常全部吞掉）。

---

## 6. 媒体

### 6.1 `MediaRepository`（`data/media/MediaRepository.kt`）

- 通道：`api/media`，`media.upload{id,mime,data}` / `media.fetch{ids}`。
- 上传：`upload(localUri)` 逐级收紧预算 `BUDGETS = 68KB → 56KB → 44KB`（68KB 来自 `PhotoCompressor.MAX_BYTES`，为绕开 HTTP 网关约 100KB 请求体上限 + base64 4/3 膨胀）；每级生成 UUID（去 `-`）作为 assetId；`uploadOnce` 仅对 `IOException` 重试 `MAX_RETRY=2`（退避 300ms→600ms），服务端非 0 直接放弃该预算。
- 并发：`uploadAll` 用 `Semaphore(2)`（弱网避免多路大请求互相拖慢）；`ensureLocal` 下载 `FETCH_BATCH=4` 张/批。
- 本地化：`ensureLocal(refs)` 过滤空值与占位符 `MISSING_REF="-"`，缓存目录 `filesDir/media/<assetId>.jpg`，只下载缺失项，返回绝对路径（与 `mediaIds` 一一对应）。
- 语义：`mediaIds` 永不出网（本机 `content://`/缓存路径），跨设备只交换 `mediaRefs`；上传失败位写 `-` 保持索引对齐。

### 6.2 `DishHeaderStore`（`data/media/DishHeaderStore.kt`）

- 点菜页自定义头图：`save(uri)` 读边界 → `inSampleSize` 粗采样 → 缩到最长边 1600px → JPEG 85 → 写 `filesDir/dish_header/header.jpg`，返回绝对路径；`deleteCustom()` 删副本。
- 只在本机，不上传云端，不参与同步。

### 6.3 压缩参数

| 组件 | 参数 |
|---|---|
| `PhotoCompressor`（`util/PhotoCompressor.kt`） | `MAX_EDGE=1600`、`MAX_BYTES=68KB`；边长阶梯 `1600/1280/1080/900/720/600/480`、质量阶梯 `72/62/52/44/36/30`，取第一张 ≤ 预算者，否则返回最小一张；单流读取（picker URI 不可重复读）；返回 `(base64 NO_WRAP, 字节数)` |
| `AvatarCompressor`（`util/AvatarCompressor.kt`） | 最长边 `256px`、JPEG `80`、输出 `data:image/jpeg;base64,` 数据 URL（约 10–30KB，远低于服务端 2MB 上限） |
| `DishHeaderStore` | 1600px / JPEG 85 |

三者各自实现 `sampleSize`/`targetSize`/等比缩放，逻辑重复三份。

---

## 7. 备份与导出

### 7.1 `BackupCodec`（`data/backup/BackupCodec.kt`）

- `VERSION = 1`；结构：`{version, createdAt, records[]}`。
- 单条 record = `CloudPayload.encodeRecord(entity, entity.spaceId)` + 额外补 `ownerId`、`ownerName`、`localSpaceId`（云端负载不含归属，避免恢复后作者丢失）。
- `decode`：版本必须等于 1、`records` 必须是数组，否则返回 `null`（坏文件不覆盖本地）；`runCatching` 包裹解析异常。
- 文件名：`s520j-backup-<createdAt>.json`。
- 不含图片二进制（图片走 ZIP 导出）。

### 7.2 `BackupService`（`data/backup/BackupService.kt`）

- 目录 `filesDir/backups`；`list()` 从文件名解析时间戳（失败退回 `lastModified`），按时间倒序；`create()` 导全表 `contents`（含回收站）；`restore(name)` 按 id `upsertAll`（返回条数，无效文件 -1）；`delete`；`clearLocal()` 仅清 `contents`。
- `exportZip(uri)`：`data.json` + `images/<recordId>-<idx>.jpg`（`mediaIds` 顺序）+ `images/cache-<fileName>`（`filesDir/media` 全部缓存图片）；ZIP 不加密；读取失败静默跳过。

### 7.3 `ExportBuilder`（`data/export/ExportBuilder.kt`）

- `SCHEMA_VERSION = 1`，prettyPrint JSON。
- 顶层：`version`、`exportedAt`、`includesImages`（**恒 false**）、`space`（null 或 16 字段：id/name/intro/status/bindState/bindCode/bindCodeExpiresAt/createdBy/partnerUserId/createdAt/updatedAt/pendingAction/bindRequestedBy/unbindRequestedBy/ownerConfirmedAt/partnerConfirmedAt）、`session{nickname,partnerNickname,coupleStartAt}`、`records[]`。
- 单条记录 15 字段：`id/module/moduleLabel/spaceId/ownerName/visibility/title/content/eventDate/mediaIds/extra/createdAt/updatedAt/deletedAt/version`（含回收站记录；不含图片）。

---

## 8. DI 绑定清单

| 模块/类 | 绑定 |
|---|---|
| `di/AppModule` | `@Provides @Singleton S520JDatabase`（`S520JDatabase.create`）；`ContentDao`/`OutboxDao`/`UserDao`/`SpaceDao`/`NotificationDao` 逐个 `@Provides`（**`SyncConflictDao`、`CachedDishDao` 未提供，使用方直接依赖 `db`**） |
| `di/CloudModule` | `@Provides @Singleton Json`（`ignoreUnknownKeys`、`explicitNulls=false`、`isLenient`）；`@Provides @Singleton CloudApi`（OkHttp 10s/25s/25s + Retrofit + kotlinx converter，`baseUrl=BuildConfig.CLOUD_BASE_URL`） |
| 构造注入（`@Inject constructor` + `@Singleton`） | 全部仓库与服务：`AuthRepository`、`ContentRepository`、`SpaceRepository`、`LocalResetCoordinator`、`DishSnapshotSync`、`CloudAuthRepository`、`CloudSpaceRepository`、`DishHistoryRepository`、`DishRandomRepository`、`MediaRepository`、`DishHeaderStore`、`SyncRepository`、`NotificationSyncRepository`、`CoupleLiveRepository`、`NetworkStateTracker`、`CoupleNotifier`、`UserPreferences`、`BackupService` |
| 无 DI（object/纯函数） | `CloudPayload`、`SyncPolicy`、`SyncApply`、`ConflictResolver`、`RecyclePolicy`、`SpaceStateMachine`、`SpaceMirror`、`BackupCodec`、`ExportBuilder`、`FieldCodec`、`PhotoCompressor`、`AvatarCompressor`、`CoupleStartDate`、`SeedData`、`DishSnapshotPolicy` |
| WorkManager | `SyncWorker` 通过 `@EntryPoint SyncEntryPoint` + `EntryPointAccessors` 取 `SyncRepository` |

### 8.1 应用入口与配套基础设施

- `S520JApplication`：仅 `@HiltAndroidApp`。
- `MainActivity`：`@AndroidEntryPoint` + `enableEdgeToEdge`；`AppViewModel`（`viewModels()`）驱动 `fontScale`/`session`/`localResetReady`；`LaunchedEffect(Unit){ seedIfNeeded() }`；`localResetReady` 为 true 后才渲染 `loggedIn ? MainShell : AuthScreen`。
- `CoupleNotifier`：两个通知渠道 `s520j_couple`（IMPORTANCE_HIGH，碰一碰/挑战达成）、`s520j_updates`（IMPORTANCE_DEFAULT，心愿/空间/同步异常）；无通知权限静默跳过；`notify` 用 `runCatching` 包裹；`PendingIntent` 指向 launcher intent，`FLAG_IMMUTABLE`。
- `UserPreferences`：单例 DataStore `s520j_prefs`，键覆盖会话（userId/nickname/accountId/partnerNickname/coupleStartAt/loggedIn）、字号、问候语、挑战状态、自定义碰一碰、分类迁移标记、提醒、默认可见性、双头像 URI、云端 token/spaceId、同步/事件游标、通知基线、同步失败轨迹、点菜（头图路径/快照游标/同步时间/优先新菜/历史）；`session` 默认 `nickname="你"`、`partnerNickname="TA"`、`coupleStartAt=FIXED_COUPLE_START_MS`。
- `NetworkStateTracker`：`callbackFlow` 包装 `registerDefaultNetworkCallback`，暴露 `Flow<Boolean>`（`distinctUntilChanged`）+ `currentConnected()` 同步查询。
- `CoupleStartDate`：固定相伴起始 `1763136000000L`（2025-11-15 00:00 +08:00），新注册用户统一从此起算。

---

## 9. 测试覆盖矩阵

### 9.1 数据层（`app/src/test/java/com/s520j/app/data/`）

| 测试文件 | 验证内容 |
|---|---|
| `CoupleStartDateTest` | 常量精确等于 2025-11-15 00:00 Asia/Shanghai 的 epoch，并反解日期 |
| `local/FieldCodecTest` | 列表/Map 编解码往返、空串与空白、跳过空项、`=` 保留在值内、BindState 七态与中文标签 |
| `model/VisibilityTest` | `from` 大小写兼容、未知值回退 `PRIVATE` |
| `repository/SeedDataTest` | 种子共 23 条、不含「我方提出且待回应」心愿、对方心愿可操作、各模块计数 |
| `repository/RecoveryCodeRuleTest` | 密码 8–32 位、仅字母数字、大小写均合法、各类错误文案 |
| `repository/ChangePasswordTest` | `changeRecoveryCode` 空旧密码/过短/含特殊字符/新旧相同/旧密码错/未登录各分支；成功时 upsert 一次；`hash` 确定性 |
| `repository/LocalResetCoordinatorTest` | 首次升级「先清库再写标记」顺序、模拟重启不重复清理、新装保持干净未登录、同步在重置后执行 |
| `space/SpaceStateMachineTest` | 七态全部转换、24h 过期边界（含恰好到期）、绑定/解绑双确认、`canEdit/canExport/canCreate`、`PendingAction.from`、`stateOf` |
| `space/SpaceMirrorTest` | 双人→BOUND、绑定申请→REQUEST_PENDING+BIND、解绑申请→UNBIND、拒绝/撤回回 BOUND、冻结、单人码保留、无码回 UNBOUND |
| `sync/SyncPolicyTest` | 20s 节流（含时钟回拨）、状态条文案、未绑定 couple 延后推送、连续 3 次失败+待上传才提醒一次、恢复后同签名可再提醒、账号切换重置、账号/权限/冻结分类 |
| `sync/CloudPayloadTest` | `mediaRefs` 编解码、`decodeRevokedIds`、`encodeRecord` 字段与小写归一化、private 不写 spaceId、缺 id 返回 null、`isSeed` |
| `sync/SyncApplyTest` | `shouldApplyRemote` 双向、`pushOp` 携带 seq/baseVersion、deleteOp 不带内容、冲突 30 天 |
| `sync/ConflictResolverTest` | 保留本机时版本对齐云端、删除标记清除、缺 id 无法解析、快照自带 spaceId 优先 |
| `sync/RecyclePolicyTest` | 29 天不清理、30/45 天清理、未删除永不清理 |
| `sync/NotificationSyncRepositoryTest` | 首次基线只归档已读且不发通知、基线后新事件未读+去重通知、本机已读不被云覆盖、同设备多账号各自基线、本地异常提醒按 ID 去重 |
| `backup/BackupCodecTest` | 字段完整往返（含 mediaRefs/extra）、版本不符拒绝、损坏文件 null、文件名时间戳 |
| `export/ExportBuilderTest` | 顶层字段、标题转义、space/session/record 字段集合精确匹配、回收站记录包含、`includesImages=false`、无空间时 `space=null` |
| `cloud/CloudAuthRepositoryTest` | 云端无空间不自动创建、有空间返回 id、`updateAvatar` 携带 data URL、同步路径保留 403、未登录不发请求、刷新 500 不清会话 |
| `cloud/CloudSpaceRepositoryTest` | create/updateInfo/refreshCode/join 成功路径、云端无空间清镜像但 REQUEST_PENDING 保留、确认绑定本地标记已确认、任意异常转 Failure（含 DB 列缺失、非法 JSON、网络异常）、对方昵称/头像写入、单人空间不写 |
| `couple/CoupleLiveRepositoryTest` | 挑战双方标记解析、`markChallenge` 的 doneAt、`sendPoke` 成功/网络异常 false、事件解析+游标推进、未登录空列表不动游标、业务失败降级 null |

### 9.2 工具层（`app/src/test/java/com/s520j/app/util/`）

| 测试文件 | 验证内容 |
|---|---|
| `PhotoCompressorTest` | 小图不放大、横/竖图最长边 1600、`inSampleSize` 控制内存、68KB 预算 + base64 膨胀后 < 100KB |
| `AvatarCompressorTest` | 小图不放大、横/竖 256、极端比例不产生 0 尺寸、采样率为 2 的幂且落 ≤512 |

### 9.3 覆盖缺口

- 无 Room 迁移测试（`room-testing` 依赖已引入但未使用），无 schema 导出可供比对。
- `SyncRepository`（push/pull 主链路）、`MediaRepository`、`BackupService`、`DishSnapshotSync`、`DishHistoryRepository`、`DishRandomRepository`、`ContentRepository`、`SpaceRepository`、`AuthRepository.register/login`、`UserPreferences`、`NetworkStateTracker`、`CoupleNotifier`、`MainActivity` 均无单测；`androidTest` 仅保留默认依赖。

---

## 10. 风险与技术债

| # | 位置 | 类型 | 说明 |
|---|---|---|---|
| 1 | `data/local/S520JDatabase.kt:116` | fallbackToDestructiveMigration | 任何未显式覆盖的版本变化会**直接清库**，用户内容/outbox 全丢 |
| 2 | `data/local/S520JDatabase.kt:23` | 配置缺失 | `exportSchema=false`，无 schema 快照、无迁移测试，手写 SQL 与实体易漂移 |
| 3 | `data/local/Entities.kt:118` | 死代码 | `MediaEntity/media_assets` 无 DAO、零读写，只在建库时占用表 |
| 4 | `data/local/Entities.kt:89` | 索引缺失 | `outbox` 无 `entityId` 索引，`countByEntity` 每次推送/拉取全表扫 |
| 5 | `data/local/Entities.kt:99` | 索引缺失 | `sync_conflicts` 无 `expireAt` 索引，`purgeExpired` 全表扫 |
| 6 | `data/local/Entities.kt:39` | 索引缺失 | `spaces.bindCode` 无索引/唯一约束，`findByBindCode` 全表扫且可重复码 |
| 7 | `data/local/Entities.kt:11` | 索引缺失 | `users.accountId` 无索引，登录/查重全表扫 |
| 8 | `data/local/Entities.kt:159` | 编码缺陷 | `FieldCodec` 不转义，`extra` 值含 `;`/`=`、`mediaIds` 含 `\|` 时数据被截断 |
| 9 | `data/local/S520JDao.kt:112` | 潜在错误 | `currentUser/currentSpace` 用 `LIMIT 1` 且无排序，多行时结果不确定 |
| 10 | `data/model/Models.kt:56` | 静默降级 | `ContentModule.from` 未知模块回退 `NOTE`，云端新增模块会被错误归类到「我们的笔记」 |
| 11 | `data/repository/ContentRepository.kt:37` | 非原子写 | `contentDao.upsert` 与 `outbox.enqueue` 不在同一事务，崩溃会「本地已改但永不上传」 |
| 12 | `data/repository/ContentRepository.kt:55` | 注释与实现不符 | `purge` 注释写「需双方确认」，实现是本地直接 `hardDelete` |
| 13 | `data/repository/ContentRepository.kt:67` | 脆弱实现 | `migrateTaxonomyV2` 用 `filterIndexed` 按下标比对原列表，依赖 `QuickRecordRouter` 不改变顺序/长度 |
| 14 | `data/repository/ContentRepository.kt:149` | 硬编码 | 种子数据 `spaceId="space-local"` 与真实空间 id 不符，绑定后仍挂在假空间下 |
| 15 | `data/repository/AuthRepository.kt:175` | 安全 | PBKDF2 仅 10,000 次迭代（现代建议 ≥ 数十万），且 `salt.toByteArray()` 依赖默认字符集 |
| 16 | `data/repository/AuthRepository.kt:70` | 双身份 | 本地 id `user-<accountId>` 与云端 userId 两套体系，`adoptCloudAccount` 可能在同一 `users` 留下多行 |
| 17 | `data/repository/SpaceRepository.kt:240` | 重复实现 | 本地 6 位绑定码生成与云端 `space.generateBindCode` 并存，本地码在云端不存在 |
| 18 | `data/repository/SpaceRepository.kt:67` | 死代码 | `refreshBindCode` / `submitBindCode` / `createSpace` 已被 `CloudSpaceRepository` 取代，但仍维护同一状态机 |
| 19 | `data/sync/SyncRepository.kt:260` | 数据丢失 | 服务端 4xx 业务拒绝时直接删 outbox（无 UI 提示），用户内容永久不上云 |
| 20 | `data/sync/SyncRepository.kt:230` | 队列泄漏 | `UPSERT` 找不到实体（已被硬删）时 outbox 项永久保留，每轮重复扫描 |
| 21 | `data/sync/SyncRepository.kt:348` | 资源泄漏 | `revokedIds` 硬删内容但不清理 `filesDir/media` 缓存图片 |
| 22 | `data/sync/SyncRepository.kt:309` | 一致性 | `resolveConflict(keepLocal)` 不检查快照是否已过期，过期后仍可能把旧本机版本推上去 |
| 23 | `data/sync/SyncRepository.kt:368` | 死循环风险 | 服务端未返回 `cursor` 而 `hasMore=true` 时游标不前进，`pull` 无限循环 |
| 24 | `data/sync/SyncRepository.kt:130` | 状态误报 | 通知拉取失败会把整次同步置为 `FAILED`，即使 push/pull 全部成功 |
| 25 | `data/sync/SyncWorker.kt:31` | 重试缺失 | 永远 `Result.success()`，失败不重试，只能等下一个 15 分钟周期 |
| 26 | `data/sync/SyncApply.kt:12` | 重复规则 | `shouldApplyRemote` 主流程未使用，`SyncRepository.kt:355` 自行内联同一判断，规则两处维护 |
| 27 | `data/sync/RecyclePolicy.kt:7` | 重复常量 | 30 天保留期在 `RecyclePolicy`、`SyncApply`、`SyncRepository.cleanupExpired` 三处各写一份 |
| 28 | `data/cloud/CloudAuthRepository.kt:59` | 异常吞掉 | `catch (IOException)` 后紧跟 `catch (Exception)`，后者吞掉所有异常并统一报「网络不可用」 |
| 29 | `data/cloud/CloudAuthRepository.kt:222` | 硬编码 | `TOKEN_TTL_MS=100min` 与服务端 2 小时不一致，刷新过早（仅浪费请求，不影响正确性） |
| 30 | `data/cloud/DishHistoryRepository.kt:134` | 语义偏差 | `scopes().any{push}` 成功即短路，已绑定时不再更新 `user` 副本，与「回退」注释不完全一致 |
| 31 | `data/cloud/CloudSpaceRepository.kt:62` | 隐式副作用 | `refreshLocal` 在单人空间无有效码时会自动调 `generateBindCode` 并改写本地状态，进页面即消耗云端配额 |
| 32 | `data/couple/CoupleLiveRepository.kt:105` | 异常吞掉 | 统一 `catch (Exception) → null` 且无日志，线上问题不可诊断 |
| 33 | `data/media/MediaRepository.kt:59` | 日志隐私 | `Log.w` 打印图片本地 URI、压缩预算与最终大小（`59/62/79/83` 多处），Release 也输出 |
| 34 | `data/media/MediaRepository.kt:120` | 静默中断 | 下载批次任一失败即 `?: return`，剩余批次不再尝试（无重试、无提示） |
| 35 | `data/media/MediaRepository.kt:142` | 魔法值 | `MISSING_REF="-"` 与 `mediaIds` 索引强耦合，迁移/备份逻辑需同时理解 |
| 36 | `data/util` 压缩重复 | 重复代码 | `PhotoCompressor`/`AvatarCompressor`/`DishHeaderStore` 各实现一份 `sampleSize`+`targetSize`+等比缩放 |
| 37 | `data/prefs/UserPreferences.kt:59` | 安全 | `cloud_access_token`/`cloud_refresh_token` 明文存 DataStore，无 EncryptedSharedPreferences/Keystore |
| 38 | `data/prefs/UserPreferences.kt:198` | 副作用残留 | `clearForOneTimeReset` 清 DataStore 但不删 `dish_header/header.jpg` 等磁盘文件 |
| 39 | `data/backup/BackupService.kt:52` | 同步缺口 | `restore` 只 upsert `contents`、不重建 outbox，恢复的内容永远不会上传云端 |
| 40 | `data/backup/BackupService.kt:63` | 一致性 | `clearLocal` 清 `contents` 但不清 `outbox`，留下指向已删实体的待推送项 |
| 41 | `data/backup/BackupService.kt:68` | 安全 | ZIP 导出明文、包含全部图片缓存，无加密/口令、无大小上限保护 |
| 42 | `data/export/ExportBuilder.kt:54` | 敏感信息 | 导出 JSON 含 `space.bindCode`（有效期内的可加入凭证） |
| 43 | `data/repository/DishSnapshotSync.kt:74` | 生命周期 | 自建 `CoroutineScope(SupervisorJob())` 永不取消，无法停止/测试替换调度器 |
| 44 | `data/repository/DishSnapshotSync.kt:110` | 计数偏差 | `downloaded` 按 `page.items` 累加而非实际入库条数，去重/重跑后进度会虚高 |
| 45 | `data/repository/LocalResetCoordinator.kt:31` | 非原子 | 先 `clearAllTables`+清 DataStore 再 `commit()` 标记；标记写失败会下次重新清空新数据 |
| 46 | `data/sync/NotificationSyncRepository.kt:44` | 兜底缺陷 | `createdAt` 缺失时用 `System.currentTimeMillis()`，历史事件被排在列表最前 |
| 47 | `di/AppModule.kt:25` | 抽象泄漏 | 只提供 5 个 DAO，`SyncConflictDao`/`CachedDishDao` 未入图，多个类直接依赖整个 `S520JDatabase`，难 mock |
| 48 | `notifications/CoupleNotifier.kt:43` | 兼容性 | 通知小图标用 `R.mipmap.ic_launcher`（非单色 small icon），部分 ROM 显示为白块 |
| 49 | `app/build.gradle.kts:12` | 硬编码 | 云端 `CLOUD_BASE_URL` 默认值写死内网环境域名，仅能用 `-P` 覆盖 |
| 50 | `MainActivity.kt:29` | 隐式初始化 | 每次进入组合都调 `seedIfNeeded()`，正确性完全依赖 `contentDao.count()==0` 守卫与协程时序 |
