# Tasks

> 交付策略：分两批，每批独立「先失败测试 → 实现 → 全量单测 → 出包 → 双机实机验收 → 通过后 commit + tag」。
> 硬性约束：不改动绑定流程（创建空间 / 绑定码 / 加入 / 双方确认绑定）。
> 云函数与 SQL 迁移由我执行部署，部署结果与接口自测会回报。
> 完成每个 Task 后必须修改本文件勾选对应复选框。

# 批次一：空间资料 + 解绑闭环 + 双向同步（`v0.9.0-stage9-space-sync`，900）

> 状态：代码、单测、出包、云函数部署与接口自测均已完成，等待双机实机验收。

## Task 9.1: 云端解绑决定接口与拉取核对

- [x] SubTask 9.1.1: `cloudbase/functions/_shared/handlers.js` 新增 `space.rejectUnbind`（仅 `to_user`，置 `rejected`）与 `space.cancelUnbind`（仅 `from_user`，置 `cancelled`），复用现有 `space.get` 的 `pendingRequest` 下发。
- [x] SubTask 9.1.2: 草稿阶段确认 `sync.pull` 对 `visibility=couple` 的过滤与游标无遗漏；发现真实缺陷在 `push`：未绑定时 `couple` 内容无 `spaceId`，云端 400 后 outbox 被直接删除导致内容永久丢失。已在 `SyncRepository.push()` 中延迟推送（保留 outbox 待绑定后重推）。
- [x] SubTask 9.1.3: 部署云函数并用 curl 自测四类解绑动作（发起/确认/拒绝/撤回）与 `space.get` 的返回。已部署 `space` 并在真实环境跑通：绑定→非创建者改名→发起去重→对方收到 `unbind_pending`→拒绝回到 active→发起后撤回→确认后 frozen。
- [x] SubTask 9.1.4: 接口契约测试：请求/响应字段与客户端解析一致（`CloudSpaceRepositoryTest` / `SpaceMirrorTest`）。

## Task 9.2: 空间资料编辑 + 解绑客户端闭环

- [x] SubTask 9.2.1: `CloudSpaceRepository` 新增 `updateInfo(name, intro)`、`requestUnbind()`、`confirmUnbind(requestId)`、`rejectUnbind(requestId)`、`cancelUnbind(requestId)`，统一 `guarded` 与离线降级。
- [x] SubTask 9.2.2: `AppViewModel` 解绑相关方法改为云端优先 + `refreshLocal()`；新增 `rejectUnbind()` / `cancelUnbind()` / `updateSpaceInfo()`；失败走 `reportFailure`。
- [x] SubTask 9.2.3: `SpaceScreen` 空间信息卡新增「编辑资料」弹窗（双方可用，名称 ≤20 字）；解绑区区分「我发起的（可撤回）」与「对方发起的（确认 / 拒绝）」；移除「模拟对方确认」入口。
- [x] SubTask 9.2.4: 测试（先红后绿）：解绑请求成功进入等待态；确认后刷新为冻结；拒绝后回到已绑定；撤回后回到已绑定；改名云端失败时不落本地。

## Task 9.3: 同步可见化与自动拉取

- [x] SubTask 9.3.1: 新增前台同步协调器：进入心动/拾光/我们即同步一次；前台每 20 秒静默同步；退到后台立即停止。
- [x] SubTask 9.3.2: `UsScreen` 顶部新增同步卡（上次同步时间 + 立即同步 + 结果文案）。
- [x] SubTask 9.3.3: 三个主页面支持下拉刷新，复用统一触发入口。
- [x] SubTask 9.3.4: 全局顶部异常状态条（离线 / 失败可点击重试，成功后自动隐藏）。
- [x] SubTask 9.3.5: 测试：节流（20 秒内不重复请求）、失败进入异常态、恢复后清除、进入页面触发（`SyncPolicyTest` 11 例）。

## Task 9.4: 批次一回归与交付

- [x] SubTask 9.4.1: 全量单测 0 失败（25 个测试类 / 179 条 / 0 失败）。
- [x] SubTask 9.4.2: 构建 `v0.9.0-stage9-space-sync`（900）并 badging 校验（versionCode=900，versionName 一致）。
- [x] SubTask 9.4.3: 更新 `build-s520j-android-app/tasks.md` 与本 spec checklist。
- [x] SubTask 9.4.4: 提交双机实机验收步骤并暂停等待结果。
- [ ] SubTask 9.4.5: 实机通过后 Git commit + tag（含未提交的 hotfix2/hotfix3）。

# 批次二：心动 / 挑战 / 碰一碰 / 五星榜 / 我们页（`v0.10.0-stage10-couple-live`，1000）

> 进入条件：批次一实机验收通过。

## Task 10.1: 云端挑战、事件与头像接口

- [x] SubTask 10.1.1: 新增 `cloudbase/db/003_couple_live.sql`（`challenge_days` 空间+天唯一、`couple_events` 事件流；002 已被 RLS 占用故顺延编号）。
- [x] SubTask 10.1.2: 新增 `challenge.get` / `challenge.mark`（仅标记自己、幂等、双方完成写达成时间并投递 `challenge_done`、跨日按 day 分行）。
- [x] SubTask 10.1.3: 新增 `event.send` / `event.pull`（按空间隔离、`created_at` 游标拉取、排除自己发送）。
- [x] SubTask 10.1.4: 新增 `profile.updateAvatar`（base64 数据 URL，写 `users.avatar`；同时保留 `users.updateAvatar` 兼容已装包）。
- [x] SubTask 10.1.5: 修复 `entry.js` 只接受单前缀导致 `users.updateAvatar` 返回「接口不存在」；auth 函数现接受 `auth/users/profile`，space 函数接受 `space/challenge/event`。SQL 已执行（5/5），auth/space 已部署，13 项真实接口自测全部通过（临时数据已清理）。

## Task 10.2: 提醒体系 + 挑战 + 碰一碰

- [x] SubTask 10.2.1: 新增 `CoupleNotifier`：高优先级渠道、Android 13+ 权限请求（拒绝后不再重复弹，`poke_notify_asked` 持久化）、前台横幅 `CoupleBanner`、通知中心写入。
- [x] SubTask 10.2.2: `DailyChallenge` 接入云端：`markSelfDone` 不可撤销、`mergeCloud` 以云端为准并跨天重置、双方达成显示「今日达成」。
- [x] SubTask 10.2.3: `sendPoke` 改为 `event.send`；同步时 `event.pull` 拉到 `poke` / `challenge_done` 弹横幅 + 系统通知。
- [x] SubTask 10.2.4: 事件去重：`event_cursor` 持久化，通知以事件 id 为主键 REPLACE，重装/断网不重复提醒。
- [x] SubTask 10.2.5: 测试：标记幂等、云端合并跨天重置、事件解析与游标推进、未登录/网络异常降级（`ChallengeStateTest`、`CoupleLiveRepositoryTest`）。

## Task 10.3: 心动首页与五星榜

- [x] SubTask 10.3.1: `HeartScreen` 顶部姓名固定为「许愿树」「小锦鲤」。
- [x] SubTask 10.3.2: `StarBoard` 增加「全部」聚合；新增 `StarBoardScreen` 五星榜子页面（全部 / 松记 / 锦记 + 空态「去记录」）。
- [x] SubTask 10.3.3: `GalleryScreen` 两个星级卡片合并为一个「五星榜」入口。
- [x] SubTask 10.3.4: 测试：三分类过滤、全部分类合并排序、空分类、历史数据归属不变。

## Task 10.4: 我们页资料同步

- [x] SubTask 10.4.1: 自己头像选图 → `AvatarCompressor` 压缩 256px JPEG → `users.updateAvatar`；本机即时显示（data URL 自行解码渲染）。
- [x] SubTask 10.4.2: 对方昵称/头像从 `space.get` 成员写入 `partnerNickname / partnerAvatar`；未绑定显示「未绑定共同空间」占位。
- [x] SubTask 10.4.3: 移除手改对方资料的入口（对方头像只读，无选择器）。
- [x] SubTask 10.4.4: 测试：压缩尺寸/采样率（`AvatarCompressorTest`）、资料映射与单人空间不写（`CloudSpaceRepositoryTest`）。

## Task 10.5: 批次二回归与交付

- [x] SubTask 10.5.1: 全量单测 0 失败（208 项）。
- [x] SubTask 10.5.2: 构建 `v0.10.0-stage10-couple-live`（1000），badging 校验通过。
- [x] SubTask 10.5.3: 双机实机验收（6 项）全部通过。
- [ ] SubTask 10.5.4: 通过后 Git commit + tag。

## Task 10.6: 验收后回归修复（v1.0.0）

- [x] SubTask 10.6.1: 可见范围切换不生效：本机 `version` 自增导致 `baseVersion` 与云端基线不符而判冲突、修改被云端覆盖。改为「本机 version 始终等于最近云端版本」，冲突「保留本机」也改为对齐云端版本后由服务端 +1。
- [x] SubTask 10.6.2: 共同→私有后对方仍可见：`syncPull` 增加 `revokedIds`（只回 id，不回内容），客户端删除本地旧副本；真实接口验证通过。
- [x] SubTask 10.6.3: 图片不同步：新增 `media.upload` / `media.fetch` + `media_blobs` 表；记录改存 `mediaRefs`（assetId），本机路径 `mediaIds` 不外传；对方拉取后下载缓存到 `filesDir/media`。真实接口验证通过（含越权校验与 250KB 上限）。
- [x] SubTask 10.6.4: 新增 `PhotoCompressor`（≤1600px / ≤250KB）与 `MediaRepository`；Room v6 迁移新增 `mediaRefs` 列。

# Task Dependencies

- Task 9.2 / 9.3 依赖 Task 9.1
- Task 9.4 依赖 9.1–9.3
- Task 10.2 / 10.3 / 10.4 依赖 Task 10.1
- Task 10.5 依赖 10.1–10.4，且批次一已通过实机验收
