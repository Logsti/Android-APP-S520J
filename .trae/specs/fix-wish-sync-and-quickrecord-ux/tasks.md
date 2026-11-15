# Tasks

- [x] Task 1: 修复心愿状态流转同步（核心）
  - [x] 1.1 先改/补单测 `WishBoardTest`：迁移后 `version` 保持不变；答应/拒绝/实现的守卫与答应人写入仍正确
  - [x] 1.2 `WishBoard.moved()` 与 `withStatus()` 去掉 `version + 1`，只改 `extra` 与 `updatedAt`
  - [x] 1.3 `AppViewModel.transitWish` 保存后触发一次同步（复用 `syncNow`），不阻塞错误提示
  - [x] 1.4 检查其它依赖 version 自增的测试（`CrossPageSyncTest` 等）并同步修正
  - [x] 1.5 跑单测通过

- [x] Task 2: 随手记入口迁移
  - [x] 2.1 `QuickRecordTypes` 新增 `primary`（5 项，不含 JOT），`all` 保持 6 项
  - [x] 2.2 `QuickRecordSection` 恢复单行 5 入口（删除 3×2 网格）
  - [x] 2.3 「快速记录」标题右上角新增随手记图标按钮（36dp 点区 / 20dp 图标），点击进入 JOT 编辑
  - [x] 2.4 校验：随手记仍在默契页，且不在「最近的美好」

- [x] Task 3: 拾光共同照片入口
  - [x] 3.1 由 `feed` 展平全部图片列表
  - [x] 3.2 「共同照片」StatCard 增加 onClick 打开 `PhotoPagerDialog`；空列表不打开
  - [x] 3.3 校验：点击后可从第一张滑到最后一张

- [x] Task 4: 云同步行等宽
  - [x] 4.1 行尾 48dp IconButton 替换为 24dp 图标（同步中 18dp 进度圈）
  - [x] 4.2 保持整行点击同步；同步中禁用
  - [x] 4.3 校验：与其他设置行边界、文字起始位置一致

- [x] Task 5: 质量门禁
  - [x] 5.1 全量单测通过
  - [x] 5.2 `assembleDebug` 构建成功，产出 v1.1.1 APK（versionCode 1101）
  - [x] 5.3 提交实机验收步骤

# Task Dependencies

- Task 2、Task 3、Task 4 相互独立，可并行
- Task 5 依赖 Task 1–4
