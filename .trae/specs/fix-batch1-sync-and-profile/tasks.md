# Tasks

- [x] Task 1: 修复 visibility 大小写不一致（同步链路根因）
  - [x] SubTask 1.1: `Visibility.from` 改为大小写不敏感（`equals(ignoreCase=true)`）
  - [x] SubTask 1.2: `CloudPayload.encodeRecord`：spaceId 判断改 `equals(ignoreCase=true)`，发送 `visibility.lowercase()`；`decodeRecord` 落库 `lowercase()`
  - [x] SubTask 1.3: `SyncPolicy.shouldDeferPush`：`visibility.equals("couple", ignoreCase=true)`
  - [x] SubTask 1.4: 服务端 `contentCreate`/`contentUpdate`：`rec.visibility?.toLowerCase() === 'couple'`；`syncPull` 查询 `LOWER(visibility) = 'couple'`
  - [x] SubTask 1.5: 单测：`encodeRecord` 在 visibility="COUPLE" 且 cloudSpaceId 非空时附带 spaceId；`shouldDeferPush` 对 "COUPLE" 且 spaceId 为空返回 true
  - [x] SubTask 1.6: 部署 sync 云函数，用真实账号复测：A 推 couple 内容 → B pull 能收到

- [x] Task 2: 绑定后对方昵称自动同步到「我们」页
  - [x] SubTask 2.1: `SpaceMirror.Member` 增加 `avatar` 字段；`parseRemote` 解析 `avatar`
  - [x] SubTask 2.2: `UserPreferences` 增加 `setPartnerNickname(name: String?)`
  - [x] SubTask 2.3: `CloudSpaceRepository.refreshLocal` 合并后取 partner 成员，写入 `prefs.setPartnerNickname` / `setPartnerAvatar`
  - [x] SubTask 2.4: 单测：`refreshLocal` 有 2 个成员时，partner nickname 写入 prefs（+ 单人空间不写）

- [x] Task 3: 头像云端同步
  - [x] SubTask 3.1: 新增 `users.updateAvatar` 云接口（校验登录，写 `users.avatar`，支持 base64 数据 URL，2MB 上限）
  - [x] SubTask 3.2: `CloudAuthRepository.updateAvatar(dataUrl)`
  - [x] SubTask 3.3: 「我们」页自头像选择后 `AvatarCompressor` 压缩为 ≤256px JPEG → base64 → 调上传；`isAvatarUploading` 禁用按钮
  - [x] SubTask 3.4: 对方头像改为只读（`editable=false`），显示 `prefs.partnerAvatar`
  - [ ] SubTask 3.5: 部署 auth 云函数，真实账号自测上传与读取（需用户执行部署）

- [x] Task 4: 出包与回归
  - [x] SubTask 4.1: 全量单测通过
  - [x] SubTask 4.2: 构建 v0.9.1-stage9-hotfix1 APK（versionCode 901）→ dist/S520J-v0.9.1-stage9-hotfix1-debug.apk
  - [ ] SubTask 4.3: 提交双机实机验收步骤并暂停

# Task Dependencies
- Task 1 独立（同步根因）
- Task 2 独立（昵称同步）
- Task 3 依赖 Task 2 的 prefs 写入通路
- Task 4 依赖 1/2/3
