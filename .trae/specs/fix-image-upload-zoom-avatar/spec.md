# 修复：图片上传失败 / 图片放大查看 / 头像重装不同步

## 背景

v1.0.0 实机验收中，步骤 1/3/4/5/6 通过，但发现 3 个回归/体验问题：

1. **图片上传频繁失败**：添加带图记录时常提示「部分图片上传失败，联网后可重新保存」，记录仍保存但图片丢失。
2. **图片无法点击放大**：记录中的图片以 64dp 缩略图展示，无点击放大入口，不符合交互习惯。
3. **自头像重装后不同步**：本机上传头像后对方能看到，但卸载重装登录后，自己的头像未从云端拉回。

## 问题 1：图片上传失败（v1.0.2 重新定位）

### 真实根因（已用真实接口实测确认）
- **CloudBase HTTP 访问服务的单次请求体上限约 100KB**：实测 70KB 原图（base64 95.6KB）成功；80KB 原图（base64 109KB）返回 `413 EXCEED_MAX_PAYLOAD_SIZE`。
- 客户端原先把图片压到 **250KB**，base64 后约 340KB，**远超网关上限**，因此大图必然被拒。
- 413 响应体是 `{"code":"EXCEED_MAX_PAYLOAD_SIZE",...}`，`code` 为字符串，与 `CloudResponse.code: Int` 不匹配，反序列化抛异常并被吞掉 → 前端只看到「上传失败」，拿不到原因。
- 另外：上传失败时用 `filterNotNull()` 会丢弃失败项，导致 `mediaIds` 与 `mediaRefs` **索引错位**，下次编辑 `zip` 复用会把 A 图配成 B 图的引用。
- 响应体不受限（实测 4 张批量拉取 328KB 正常），故 `media.fetch` 无需改动。

### 修复方案
1. **`PhotoCompressor`**：新增 68KB 压缩预算（base64 ≈ 91KB，+JSON 外壳 < 100KB）；逐级降边长 `1600→1280→…→480` 与质量 `72→…→30`，取第一张满足预算的 JPEG。
2. **`MediaRepository.upload`**：预算阶梯 `68KB→56KB→44KB`，任一档成功即返回；每档内仅对网络异常做 2 次退避重试。
3. **`MediaRepository.uploadAll`**：并发上限 2，避免弱网多路大请求互相拖慢。
4. **`AppViewModel.saveRecord`**：`mediaRefs` 与 `mediaIds` 严格等长同序，失败位置写占位符 `-`；下次编辑显式跳过占位符并重新上传，杜绝串图。
5. **`MediaRepository.ensureLocal`**：过滤空串与 `-` 占位符，不参与下载。
6. 失败提示改为「N 张图片上传失败，其余内容已保存；可在编辑页重新保存重试」。

### 涉及文件
- `app/.../util/PhotoCompressor.kt`
- `app/.../data/media/MediaRepository.kt`
- `app/.../ui/AppViewModel.kt`
- `app/src/test/.../util/PhotoCompressorTest.kt`（预算断言：base64 + 外壳 < 100KB）

## 问题 2：图片点击放大

### 根因
`ModuleEditScreen` 中照片区用 `AsyncImage` 渲染 64dp 缩略图，未绑定 `clickable`，也没有任何全屏图片查看器组件。

### 修复方案
1. 新建 `ImageViewerDialog` composable：
   - 全屏 `Dialog`，背景半透明黑色。
   - 用 `AsyncImage` 显示原图。
   - 支持双指缩放（`detectTransformGestures`，scale 0.5f–4f）、单指拖动、点击空白关闭。
2. 在 `ModuleEditScreen` 给每张缩略图加 `.clickable { viewerState = uri }`，在屏幕根部渲染 `ImageViewerDialog`。

### 涉及文件
- 新建 `app/.../ui/components/ImageViewerDialog.kt`
- `app/.../ui/screens/module/ModuleEditScreen.kt`

## 问题 3：自头像重装后不同步

### 根因
- 服务端 `publicUser()` 已返回 `avatar` 字段（data URL）。
- 客户端 `CloudAuthRepository.authenticate()`（login/register/refresh 共用）只提取 `id` 和 `nickname`，**忽略了 `avatar`**，未写入 `prefs.setMyAvatar()`。
- 卸载后 DataStore 清空，`MY_AVATAR_URI` 为空；登录流程不回填，故自头像消失。
- 此外没有任何启动时 `auth.me` 调用，跨设备改头像后本机也不会自动刷新。

### 修复方案
1. **`CloudAuthRepository.authenticate()`**：从响应 `user.avatar` 提取头像，调用 `prefs.setMyAvatar(avatar)`（null 则清空）。
2. **新增 `CloudAuthRepository.fetchMe()`**：调用 `auth.me`，返回 `{ nickname, avatar }`；更新 `prefs` 中的昵称与头像。
3. **`AppViewModel` 启动同步**：在现有 `cloudSpace.refreshLocal()` 附近调用 `fetchMe()`，保证跨设备改昵称/头像后本机同步。
4. `CloudAuthResult.Success` 增加 `avatar` 字段，便于上层判断是否需要本地落库（实际由 repository 内部落库，此字段留作扩展）。

### 涉及文件
- `app/.../data/cloud/CloudAuthRepository.kt`
- `app/.../ui/AppViewModel.kt`（启动时调用 fetchMe）

## 不做的事
- 不引入 Glide 等新图片库（沿用 Coil）。
- 不改动服务端 `media.upload` 逻辑（客户端重试即可）。
- 不做图片缓存 LRU（列入优化清单 P0，本次不做）。

## 验收标准
1. 选 3–4 张图保存：全部上传成功，记录带图保存；弱网下自动重试。
2. 打开任一带图记录，点击缩略图 → 全屏大图，双指可缩放，点击空白关闭。
3. A 设备上传头像 → 卸载 A → 重装登录 → 头像自动恢复；A 改头像后 B 端下一次同步可见。
4. 全量单测通过，构建 APK 成功。
