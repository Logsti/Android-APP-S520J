# 修复心愿同步 + 快速记录/拾光/我们页交互微调

## Why

心愿「答应/拒绝」只改了本机数据库，刷新后被云端旧数据覆盖；随手记入口占用第 6 个快速记录位，破坏了原有 5 宫格布局；拾光「共同照片」卡只是数字不可点；云同步行的操作按钮比其它行宽，视觉不齐。

## What Changes

1. **修复心愿状态流转无法同步**（核心 bug）：状态迁移不再本地自增 `version`，避免乐观锁基线错误导致冲突回滚；迁移成功后立即触发一次同步。
2. **随手记入口迁移**：从快速记录第 6 个入口移到「快速记录」标题右上角图标；快速记录恢复 5 个入口单行排列。
3. **拾光「共同照片」变为浏览入口**：点击顶部第一张统计卡，全屏浏览照片墙中的全部照片。
4. **我们页云同步行对齐**：行尾按钮从 48dp IconButton 改为与其它行箭头等宽的 24dp 图标，行宽/内容宽度与其它设置行一致。

## Impact

- 影响页面：心动（心愿卡片、快速记录）、拾光、我们。
- 影响代码：`WishBoard.kt`、`AppViewModel.kt`、`HeartScreen.kt`、`QuickRecordTypes.kt`、`GalleryScreen.kt`、`UsScreen.kt` 及对应单测。
- 不改云端接口与数据库结构；不改同步协议。

---

## Requirement 1：心愿状态真正落库并同步

### 现状与根因（已核对代码）
- 心愿卡片点击答应/拒绝 → `AppViewModel.transitWish`（`AppViewModel.kt` L517-536）→ `WishBoard.accept/reject/realize` → `contentRepository.save(updated)`（本地 upsert + 写 outbox）。
- `WishBoard.moved()`（`WishBoard.kt` L121-130）把 `version` 自增 `record.version + 1`。
- 同步推送用本地 `version` 作为乐观锁基线（baseVersion）。本地已是 N+1，而云端仍是 N → 服务端判定冲突 → 用云端版本覆盖本地 → 刷新后恢复为未操作状态。
- 这与之前「编辑记录不同步」的根因完全同类：**客户端不能自增 version，version 由服务端递增**。

### 修复方案
1. `WishBoard.moved()` 与 `withStatus()` 不再修改 `version`，只更新 `extra` 与 `updatedAt`。
2. `transitWish` 保存后触发一次同步（复用已有的 `syncNow()` 逻辑），让状态尽快到达对方；失败不阻塞 UI。
3. 迁移守卫保持不变：只有非提出人能答应/拒绝；只有「已答应」能标记实现。
4. 更新单测：
   - 断言迁移后 `version` 不变（回归测试，防止再次自增）。
   - 保留合法/非法迁移与视角分组断言。
5. 校验同步链路：`extra["状态"]/["答应人"]/["提出人"]` 随记录 payload 同步，无需额外字段。

### 验收
- A 提出心愿，B 在「待我回应」点答应 → B 页面立即变「已答应」；触发同步后 A 拉取变「已答应」。
- 拒绝 → 进入「失效」，双方一致。
- 刷新、杀进程重进、切换页面后状态不回退。

---

## Requirement 2：随手记改为标题角标入口

### 目标
- 快速记录恢复为 **5 个图标单行**：照片 / 做饭 / 探店 / 旅行 / 浪漫时刻。
- 「随手记」改为「快速记录」标题右侧的小图标按钮，点击进入随手记编辑。
- 随手记仍归属默契页，不进「最近的美好」。

### 实现
1. `QuickRecordTypes`：新增 `primary`（5 项，不含 JOT），保留 `all`（6 项）供路由/迁移使用。
2. `HeartScreen.QuickRecordSection`：
   - 恢复单行 `Row` + 5 个 `weight(1f)` 入口，删除 3×2 网格。
   - `HeartSectionHeader(title = "快速记录", action = { 随手记图标按钮 })`：36dp 可点区域、20dp `Icons.Outlined.EditNote`、`contentDescription = "随手记"`，点击 `onOpenEditor(ContentModule.JOT)`。
3. 「随手记」的图标风格与标题右侧「全部」等 action 保持一致（次要色、胶囊点击态）。

### 验收
- 快速记录一行 5 个图标，宽度均匀，文案不截断。
- 点标题右侧图标可直接进入随手记编辑；保存后出现在默契页。
- 心动页「最近的美好」无随手记。

---

## Requirement 3：拾光「共同照片」卡作为全部照片入口

### 目标
点击顶部第一张统计卡（共同照片 N 条记录）→ 全屏浏览照片墙的全部照片。

### 实现
- `GalleryScreen`：
  - 由 `feed` 展平出全部图片：`feed.flatMap { it.mediaIds.take(GalleryPhotoLayout.MAX) }`。
  - `StatCard("共同照片", feed.size, Modifier.weight(1f)) { if (images.isNotEmpty()) viewingImages = images }`。
  - 复用现有 `PhotoPagerDialog`（左右滑动 + 缩放 + 页码）。
- 该入口统计的是全量照片墙，与当前分类/搜索过滤无关（卡片显示的就是全量计数）。
- 无照片时点击无反应（或轻提示），不打开空查看器。

### 验收
- 点「共同照片」卡 → 打开全部照片，左右滑动可浏览每一张。
- 关闭后回到拾光页，滚动位置不变。

---

## Requirement 4：云同步行与其它设置行等宽

### 现状
`UsScreen.SettingRow` 在有 `trailing` 时用其替代右侧箭头；当前云同步的 trailing 是 48dp `IconButton`，比其它行的 24dp 箭头宽，导致该行文字区域与视觉宽度不一致。

### 实现
- 云同步行的 trailing 改为 **24dp 图标**（`Icons.Filled.Refresh`，20dp）或 18dp `CircularProgressIndicator`，不使用 `IconButton` 的 48dp 最小尺寸。
- 整行本身已可点击触发 `vm.syncNow()`，图标只作状态提示与显式操作；同步中禁用点击。
- 保持与其它行一致的左右内边距、行高和文字对齐。

### 验收
- 云同步行的卡片边界、文字起始位置与「共同空间」「账号与安全」「隐私与权限」一致。
- 点击行或图标都能立即同步；同步中显示进度态。

---

## 验收标准

1. 心愿答应/拒绝/实现均能落库并同步到对方，刷新不回退。
2. 快速记录为 5 个单行图标；右上角随手记图标可进入随手记。
3. 拾光「共同照片」卡可打开全部照片浏览。
4. 云同步行宽度与其它设置行一致。
5. 全量单测通过，`assembleDebug` 成功。
