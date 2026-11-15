# Tasks: 修复图片上传/放大/头像同步

## Task 1: PhotoCompressor 单次开流（消除二次读取失败）
- [ ] `PhotoCompressor.toBase64` 改为先读字节到 ByteArray，再 decodeByteArray 取 bounds + 解码
- [ ] 单测：验证对同一 URI 只开一次流，大图仍能压到 ≤250KB

## Task 2: MediaRepository 上传重试 + 并行
- [ ] `upload()` 增加 2 次指数退避重试（仅网络异常），失败 Log.w
- [ ] `uploadAll()` 改为 `async/awaitAll` 并行
- [ ] 单测：模拟一次网络异常后重试成功

## Task 3: AppViewModel 上传文案优化
- [ ] `saveRecord` 中区分网络失败 vs 处理失败的 toast

## Task 4: ImageViewerDialog 组件
- [ ] 新建 `ImageViewerDialog.kt`：全屏、双指缩放、拖动、点击关闭
- [ ] `ModuleEditScreen` 缩略图加 clickable 打开查看器

## Task 5: 登录响应回填自头像
- [ ] `CloudAuthRepository.authenticate` 提取 `user.avatar` → `prefs.setMyAvatar`
- [ ] `CloudAuthResult.Success` 增加 avatar 字段

## Task 6: 启动时 fetchMe 同步自头像+昵称
- [ ] 新增 `CloudAuthRepository.fetchMe()` 调 `auth.me`
- [ ] `AppViewModel` 启动同步链路中调用，更新昵称+头像到 prefs

## Task 7: 全量单测 + 构建 APK
- [ ] 全量单测通过
- [ ] `assembleDebug` 构建成功
- [ ] 提交实机验收步骤
