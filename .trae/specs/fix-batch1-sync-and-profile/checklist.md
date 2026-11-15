# Checklist

## 同步链路（Task 1）
- [x] `Visibility.from("couple")` 返回 COUPLE，`from("COUPLE")` 也返回 COUPLE
- [x] `encodeRecord` visibility="COUPLE" + cloudSpaceId 非空 → 输出含 `spaceId` 且 `visibility`="couple"
- [x] `shouldDeferPush("COUPLE", null)` = true；`shouldDeferPush("COUPLE", "space-x")` = false
- [x] 服务端 `syncPull` 对存量 `visibility='COUPLE'` 数据仍能按 couple 拉取（LOWER 兜底）
- [x] 真实接口：A 推 couple 内容，B pull 返回 1 条且 space_id 正确

## 昵称同步（Task 2）
- [x] `SpaceMirror.Member` 含 avatar 字段并从 space.get 解析
- [x] 绑定后 `refreshLocal` 写入 partnerNickname，「我们」页对方名称不再是 "TA"
- [x] 解绑/单人空间时 partnerNickname 不被覆盖为脏值（单人空间不写 partner）

## 头像同步（Task 3）
- [x] `users.updateAvatar` 接口存在并写入 users.avatar（handlers.js 已加，待部署 auth 函数）
- [ ] 自头像上传成功后 space.get.members 返回该 avatar（待实机验证）
- [x] 对方头像为只读（editable=false），随 refreshLocal 自动更新
- [x] 上传过程有 loading 态（isAvatarUploading），失败有提示

## 质量门禁（Task 4）
- [x] 全量单测 0 失败
- [x] v0.9.1 APK 构建成功，versionCode=901 → dist/S520J-v0.9.1-stage9-hotfix1-debug.apk
- [ ] 双机验收：A 新增心愿/日历/默契记录 → B 20 秒内可见
- [ ] 双机验收：A 改昵称 → B「我们」页显示新昵称
- [ ] 双机验收：A 改头像 → B「我们」页显示新头像
