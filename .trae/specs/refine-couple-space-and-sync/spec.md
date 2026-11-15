# 共同空间与双人同步体验优化 Spec

## Why

实机使用后暴露出 8 个影响「像正常情侣 APP 一样使用」的问题：共同空间信息不能改、解绑请求对方永远收不到、共享内容同步既隐蔽又只上不下、首页姓名固定死、今日挑战与碰一碰都只在本机自娱自乐、拾光五星榜割裂成两个入口、我们页对方资料无法随绑定自动同步。

本次改造只解决这些问题，**不触碰现有的绑定（创建空间 / 输入绑定码 / 双方确认绑定）逻辑**。

## 交付批次

- **批次一（`v0.9.0-stage9-space-sync`，versionCode 900）**：问题 1 空间资料编辑、问题 2 解绑闭环、问题 3 双向可见同步。云端只需新增解绑拒绝/撤回并核对拉取链路。
- **批次二（`v0.10.0-stage10-couple-live`，versionCode 1000）**：问题 4 首页固定姓名、5 今日挑战同步、6 碰一碰提醒、7 五星榜合并、8 我们页资料同步。云端新增挑战、事件、头像接口。
- 每批独立走「先失败测试 → 实现 → 全量单测 → 出包 → 双机实机验收 → 通过后 commit + tag」，批次一验收通过才进入批次二。

## What Changes

- 共同空间名称与简介支持**双方编辑**，保存后写云端，对方在下一次同步中看到。
- 解绑改为云端闭环：发起 → 对方收到提醒 → 对方「确认解绑」或「拒绝解绑」；发起方在对方处理前可撤回；确认即刻冻结归档。
- 同步从「藏在设置里的手动按钮」升级为：`我们`页显眼入口 + 三个主页面下拉刷新 + APP 前台每 20 秒静默轮询 + 顶部异常状态条；并修复/加固拉取链路，保证云端 → 手机方向真实生效。
- 心动首页顶部卡片姓名固定为「许愿树」「小锦鲤」。
- 今日心动挑战接入云端：各自只能标记自己、当天不可撤销，双方都标记后才算达成，并给对方推送完成提醒。
- 碰一碰改为云端投递：对方在前台看到明显横幅、后台收到系统通知，通知中心留未读记录。
- 拾光两个五星榜合并为一个「五星榜」入口，子页面内分「全部 / 松记五星榜 / 锦记五星榜」三个分类，归属仍沿用记录的手工「榜单」字段。
- 我们页：自己头像可编辑（压缩后上传云端），对方昵称与头像由绑定关系自动同步、不可手改；未绑定时显示占位。

## Impact

- Affected specs：`build-s520j-android-app`（阶段 9 增量）、`refine-couple-space-and-sync`（本 spec）
- Affected code：
  - 云函数：`cloudbase/functions/_shared/handlers.js`（解绑拒绝/撤回、挑战、事件、资料更新）、`cloudbase/db/`（新增表）、`cloudbase/functions/sync/_shared/handlers.js`（拉取兜底核对）
  - 数据层：`data/cloud/CloudSpaceRepository.kt`、`data/cloud/CloudAuthRepository.kt`、`data/sync/SyncRepository.kt`、`data/prefs/UserPreferences.kt`、`data/local/*`
  - UI：`ui/AppViewModel.kt`、`ui/screens/us/SpaceScreen.kt`、`ui/screens/us/UsScreen.kt`、`ui/screens/heart/HeartScreen.kt`、`ui/screens/gallery/GalleryScreen.kt`、新增五星榜子页面与同步状态组件
  - 通知：新增 NotificationChannel、Android 13+ 通知权限、前台横幅（批次二）
- 版本：批次一 `v0.9.0-stage9-space-sync`（900），批次二 `v0.10.0-stage10-couple-live`（1000）

## 约束（硬性）

- **不改动绑定流程**：`space.create` / `space.generateBindCode` / `space.joinByCode` / `space.confirmBind` 及其客户端调用链与状态机保持现状。
- 所有新增云端能力失败时必须**降级为本地可用**，不能阻塞进入页面或编辑内容。
- 轮询只在 APP 前台执行；离开前台立即停止，避免耗电。
- 对方资料（昵称/头像/解绑请求）只能由绑定关系驱动，不提供手改入口。

## ADDED Requirements

### Requirement: 共同空间资料编辑（批次一）
系统 SHALL 允许共同空间双方修改空间名称（1–20 字）与简介，并把修改同步到云端与对方设备。

#### Scenario: 双方都能改名
- **WHEN** 任一已绑定成员在「空间信息」点击「编辑资料」并保存
- **THEN** 本地立即更新，云端 `space.update` 成功，对方在下一次同步（≤20 秒或下拉刷新）看到新名称/简介

#### Scenario: 离线保存
- **WHEN** 保存时无网络
- **THEN** 提示「网络不可用，修改未保存」，本地文字不落库，避免本机与云端不一致

### Requirement: 解绑双端闭环（批次一）
系统 SHALL 让解绑请求通过云端送达对方，并支持对方确认或拒绝、发起方撤回。

#### Scenario: 对方收到并可确认
- **WHEN** A 发起解绑
- **THEN** 云端生成 `unbind_pending` 请求；B 在 ≤20 秒内收到横幅 + 系统通知 + 通知中心记录，进入共同空间可「确认解绑」或「拒绝解绑」
- **AND** B 确认后空间立即冻结归档，双方状态同步为已冻结

#### Scenario: 拒绝与撤回
- **WHEN** B 拒绝解绑，或 A 在 B 处理前撤回
- **THEN** 请求关闭，空间保持已绑定，另一方收到「解绑已被拒绝/已撤回」提示

#### Scenario: 重复解绑
- **WHEN** 已存在未处理的解绑请求
- **THEN** 再次发起直接复用/提示已有请求，不产生重复记录

### Requirement: 可见且双向的云同步（批次一）
系统 SHALL 提供显眼的同步入口、前台自动轮询与下拉刷新，并保证云端内容能真实落到本机。

#### Scenario: 进入页面即同步
- **WHEN** 用户进入心动/拾光/我们任一主页面
- **THEN** 立即触发一次同步（成果含空间状态、共享内容、挑战、事件）

#### Scenario: 前台轮询
- **WHEN** APP 处于前台且已登录云端
- **THEN** 每 20 秒静默同步一次；切到后台立即停止

#### Scenario: 手动与下拉
- **WHEN** 用户在我们页点「立即同步」或在下拉刷新
- **THEN** 强制拉取并展示「已同步：上传 X 条，接收 Y 条」与上次同步时间

#### Scenario: 异常可见
- **WHEN** 同步失败或离线
- **THEN** 顶部出现可点击重试的状态条；恢复后自动消失

### Requirement: 首页固定姓名（批次二）
系统 SHALL 在心动首页顶部卡片固定显示「许愿树」和「小锦鲤」，不再读取用户昵称。

#### Scenario: 任意账号
- **WHEN** 打开心动首页
- **THEN** 顶部卡片始终显示「许愿树」「小锦鲤」

### Requirement: 今日心动挑战双人同步（批次二）
系统 SHALL 把每日挑战状态存到云端，并按「各自标记、双方达成」规则推进。

#### Scenario: 一方标记
- **WHEN** A 点击「已标记完成 · 等 TA」
- **THEN** 云端记录 A 当日已完成且不可撤销；B 在 ≤20 秒内看到「TA 已完成」提示与按钮状态变化

#### Scenario: 双方达成
- **WHEN** A、B 当日都已标记
- **THEN** 双方卡片显示「今日达成」，并各收到一条完成提醒；次日自动重置

### Requirement: 碰一碰双端提醒（批次二）
系统 SHALL 把碰一碰事件投递到云端，并让另一方及时、明显地收到提醒。

#### Scenario: 对方在前台
- **WHEN** A 碰一碰 B，且 B 正在使用 APP
- **THEN** B 页面顶部出现明显横幅（含动作文案与发送者），通知中心新增未读

#### Scenario: 对方在后台
- **WHEN** 同上但 B 的 APP 在后台或锁屏
- **THEN** B 收到系统通知，点击进入通知中心

#### Scenario: 无通知权限
- **WHEN** 用户拒绝通知权限
- **THEN** 仍保留应用内横幅与未读记录，且不重复弹权限请求

### Requirement: 五星榜合并（批次二）
系统 SHALL 把拾光的两个五星榜合并为一个入口，并在子页面提供「全部 / 松记五星榜 / 锦记五星榜」三个分类。

#### Scenario: 分类浏览
- **WHEN** 用户进入五星榜
- **THEN** 默认「全部」按时间倒序展示所有五星菜品；切到松记/锦记只显示对应 `extra["榜单"]` 的记录
- **AND** 记录归属仍由现有手工「榜单」字段决定，历史数据无需迁移

#### Scenario: 空分类
- **WHEN** 某个分类没有记录
- **THEN** 展示空态文案与「去记录」入口，不显示空白页

### Requirement: 我们页资料同步（批次二）
系统 SHALL 只允许编辑自己的头像；对方昵称与头像由绑定关系自动同步。

#### Scenario: 编辑自己头像
- **WHEN** 用户点击自己的头像并选择照片
- **THEN** 本机立即显示，压缩为 256×256 JPEG 后上传云端；对方下次同步看到新头像

#### Scenario: 对方资料
- **WHEN** 已绑定
- **THEN** 对方昵称/头像来自共同空间成员信息，不提供手改入口；未绑定时显示「未绑定」占位

## MODIFIED Requirements

### Requirement: 共同空间页
原「已绑定只读展示 + 本地模拟解绑」改为：空间信息可编辑；解绑请求走云端并展示等待对方处理的状态；保留现有未绑定态（创建空间 / 输入绑定码）不变。

### Requirement: 同步设置页
保留「设置 → 应用设置 → 云端同步」，但不再作为唯一入口；`我们`页提供同等能力的显眼入口。

## REMOVED Requirements

### Requirement: 本地模拟解绑
**Reason**：本地状态无法让另一方感知，且与服务端 `unbind_pending` 模型冲突。
**Migration**：改为云端 `space.unbindRequest` / `confirmUnbind` / `rejectUnbind` / `cancelUnbind`；旧的本地 `requestUnbind/confirmUnbind` 仅保留为离线降级与单测用途。
