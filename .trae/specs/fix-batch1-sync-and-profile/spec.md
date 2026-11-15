# 批次一回归修复：共同内容同步与对方资料同步 Spec

## Why

批次一 v0.9.0 实机验收中，步骤 6（共同内容同步）完全失败、步骤 7（同步卡）仅显示不工作，且绑定后对方昵称/头像未同步到「我们」页。根因已定位：visibility 大小写不一致导致 `spaceId` 未上传、云端存为 `private`；空间成员资料未写入 prefs。

## Root Causes

1. **同步大小写不一致**：`Visibility.COUPLE.name` = `"COUPLE"`，但 `CloudPayload.encodeRecord` 与 `SyncPolicy.shouldDeferPush` 用 `"couple"` 判断，导致 couple 内容推送时不带 `spaceId`，服务端 `contentCreate` 将 `"COUPLE"` 归一化为 `private`。`syncPull` 过滤 `visibility='couple' AND space_id IN (...)`，对方永远拉不到。
2. **昵称未同步**：`space.get.members[]` 含 `nickname`，但 `SpaceMirror` 仅存 `partnerUserId`，`partnerNickname` 只在登录 `saveSession` 时写入，绑定流程不更新。
3. **头像无法同步**：`users.avatar` 字段存在但无更新接口，当前头像仅本地 URI，不能跨设备。

## What Changes

- 客户端统一 visibility 为小写；服务端写时归一化、读时 `LOWER()` 兜底。
- 空间刷新时把对方 `nickname`/`avatar` 写入 prefs。
- 新增 `users.updateAvatar` 云接口；客户端选头像后压缩 base64 上传。

## ADDED Requirements

### Requirement: 共同内容双向同步

#### Scenario: A 创建 couple 内容，B 接收
- **WHEN** A 在已绑定空间内创建任意共同可见内容并完成同步
- **THEN** B 在 20 秒内或手动刷新后可见该内容，`ownerName` 为 A 的昵称

#### Scenario: 未绑定时创建 couple 内容不丢失
- **WHEN** A 未绑定时创建共同可见内容
- **THEN** 内容保留在 outbox，绑定后下次同步自动补传并带 `spaceId`

## MODIFIED Requirements

### Requirement: Visibility 编码统一
客户端 `ContentEntity.visibility` 统一存小写 `"couple"`/`"private"`；`Visibility.from` 大小写不敏感。

### Requirement: 对方资料随空间自动同步
绑定后，`refreshLocal` 从 `space.get.members` 提取对方 `nickname` 与 `avatar` 写入 prefs；「我们」页对方名称与头像只读、来自云端。

## REMOVED Requirements
无。
