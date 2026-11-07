# 第 6 轮 UI 修正 Spec（含全量需求回顾）

状态：待用户批准（批准后才动代码）
日期：2026-11-07

## 0. 全量需求回顾台账（覆盖本会话全部可回溯需求）

| 编号 | 需求（来自历次对话） | 当前状态 / 本轮处理 |
|---|---|---|
| R1 | 首页问候语模块 1:1 复刻图 1 | 现有 `GreetingHero` 为文字版实现；**待参考图后校准** |
| R2 | 今日心动挑战按图 2 还原 | 现有 `ChallengeSection` 已按描述实现；**待参考图后校准** |
| R3 | 心愿待实现卡片按图 3，且必须保留「待回应 / 已答应 / 已实现」三态切换 | 三态保留；**本轮改为列表化，修串页与只显示一条** |
| R4 | 碰碰你下方小卡片按图 4 | 图 4（白色小卡）与用户后续明确要求（淡绿胶囊）冲突，**以最新要求为准：恢复淡绿胶囊** |
| R5 | 快速记录模块按图 5 还原 | 现有五入口已落地；**待参考图后校准** |
| R6 | 多端适配验证 320px–1920px | **待真机/预览环境与参考图**，本轮先保证布局不写死宽度 |
| R7 | 快速记录跨页数据同步（照片/做饭/探店/旅行/浪漫） | **已完成**（Taxonomy v2：保存即路由到对应模块 + 回执直达） |
| R8 | 心愿选「待回应」仍看到「已答应」；两条待回应只显示最新一条 | **本轮修复**（2.1） |
| R9 | 碰碰你恢复上一版淡绿背景（同快速记录分类图案） | **本轮修复**（2.2） |
| R10 | 五星榜拆「松记五星榜」「锦记五星榜」，只记自做饭菜并标星，探店不算 | **本轮修复**（2.3） |
| R11 | 点击添加照片未跳手机相册 | **本轮修复**（2.4） |
| R12 | 拾光页第四卡「收藏的瞬间」改为「一起去过的城市」 | **本轮修复**（2.5） |
| R13 | 五类型分类体系、照片墙分类由模块派生、删除文字类型、最近美好展示浪漫时刻、浪漫时刻置默契页末位 | **已完成**（Taxonomy v2） |
| R14 | 保存后结构化回执 +「去看看」直达对应页面 | **已完成** |
| R15 | 五星榜双方都可记录；榜单只展示 5 星自做饭菜；城市卡用「旅行 TRAVEL」；碰碰你只恢复外观、保留自定义持久化 | **本轮按此决策实现** |
| R16 | 图 1–5 参考图、被截断的第 7 条剩余内容 | **本会话无附件**；收到后追加 1:1 校准任务 |

说明：R1、R2、R4、R5 的「1:1 复刻」没有参考图无法量化验收，本轮不猜测改动，只保留现状并在收到图后单独校准；本轮代码只动 R8–R12、R15。

## 1. 本轮代码计划（5 项）

### 2.1 心愿：三态筛选 + 全量独立卡片

文件：`app/src/main/java/com/s520j/app/ui/screens/heart/HeartScreen.kt`

现状问题：
- `HorizontalPager` + `PageSize.Fixed(maxWidth * 0.62f)`，相邻状态页露出 → 在「待回应」里看到「已答应」。
- `WishCard(wish = list.first())`，一个状态只渲染最新一条。

改法：
1. `WishSection` 用 `var selectedIndex by remember { mutableStateOf(0) }` 替代 `pagerState`。
2. 三个 tab 的 `selected = selectedIndex == index`，点击只改 `selectedIndex`。
3. Tab 下改为「同状态横向卡片列表」（对照图 3，保留 62% 卡宽与露出下一张的视觉）：
   - `val list = groups[WishBoard.statuses[selectedIndex]].orEmpty()`；
   - 空 → `WishEmptyCard(status, onSeeAll)`；
   - 非空 → `LazyRow`，`items(list)` 每条渲染一张 `WishCard`，宽度 `maxWidth * 0.62f`、高 244dp。
4. `WishCard` 的 `Modifier.fillMaxSize()` → `Modifier.fillMaxWidth()`。
5. `WishEmptyCard` 改 `fillMaxWidth()` + `height(160.dp)`。
6. 清理 `HorizontalPager` / `PageSize` / `rememberPagerState` / `rememberCoroutineScope` / `launch` / `BoxWithConstraints` / `PaddingValues` 等不再使用的 import。

结果：只展示当前 tab 状态的数据；两条待回应 = 两张卡片。

### 2.2 碰碰你：恢复图 4 白色小卡

文件：`HeartScreen.kt`

- 恢复 `PokeCard`：白色 `Surface`、18dp 圆角、1dp 浅描边、88dp 高、上方棕色图标 + 下方 12sp 标签。
- 默认四个动作（拍一拍/抱抱/亲亲/想你）用 `Row` 等宽（`weight(1f)`）排布，对照图 4。
- 自定义动作用 `PokeCard`（宽 84dp）+ 右上角 `×` 删除。
- 保留 `vm.customPokes` / `addCustomPoke` / `removeCustomPoke` / `sendPoke` 与「＋ 自定义」对话框。

### 2.3 五星榜：松记 / 锦记 双榜

新增 `app/src/main/java/com/s520j/app/domain/gallery/StarBoard.kt`：

```kotlin
object StarBoard {
    const val SONG = "松记"
    const val JIN = "锦记"
    val boards = listOf(SONG, JIN)
    const val KEY = "榜单"
    const val RATING_KEY = "星级"

    fun boardOf(record: ContentRecord): String =
        record.extra[KEY]?.takeIf { it in boards } ?: SONG

    fun ratingOf(record: ContentRecord): Int =
        record.extra[RATING_KEY]?.toIntOrNull() ?: 0

    fun isFiveStarDish(record: ContentRecord): Boolean =
        record.module == ContentModule.DISH && ratingOf(record) == 5

    fun list(records: List<ContentRecord>, board: String): List<ContentRecord> =
        records.filter { isFiveStarDish(it) && boardOf(it) == board }
            .sortedByDescending { it.eventDate ?: it.createdAt }
}
```

- 榜单口径：`module == DISH` 且 `星级 == 5`；`SHOP` 永不计入。探店编辑页的评分入口保持不动（探店仍可自评，只是不进榜）。
- 编辑页 `ModuleEditScreen.kt`：读入时 `starBoard = StarBoard.boardOf(existing)`；`module == DISH` 时在星级卡下增加「记入榜单」胶囊（松记 / 锦记，默认松记）；保存写入 `extra[StarBoard.KEY]`；星级说明改为「只记录自己做的饭菜，给自己做的饭菜标星；探店不计入五星榜。」
- 拾光页 `GalleryScreen.kt`：移除单张「五星榜」，改为「松记五星榜」「锦记五星榜」两张卡；各显示该榜 5 星菜名最多 3 条与数量，空榜显示「还没有 5 星菜品」；点击整卡进 `DISH`。
- 种子 `ContentRepository.kt`：DISH 种子补 `榜单`，松记、锦记各至少 1 条 5 星示例。
- 测试 `StarBoardTest.kt`：5 星 DISH 进榜 / 4 星不进 / SHOP 5 星不进 / 未填默认松记 / 两榜隔离。

### 2.4 添加照片：系统相册选择器

文件：`ModuleEditScreen.kt`

```kotlin
val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(4)
) { uris ->
    uris.take(4).forEach { uri ->
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val value = uri.toString()
        if (value !in mediaUris) mediaUris.add(value)
    }
}
...
TextButton(onClick = {
    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
}) { ... }
```

新增 import：`androidx.activity.result.PickVisualMediaRequest`、`ActivityResultContracts.PickVisualMedia`；删除 `OpenMultipleDocuments`。
说明：`PickMultipleVisualMedia` 调起 Android 系统照片选择器（相册），不再是 DocumentsUI 文件选择器；最多保留 4 张。

### 2.5 拾光页城市卡

文件：`GalleryScreen.kt`
- 删除 `collectedCount`，改 `travelCount = records.count { it.module == ContentModule.TRAVEL }`。
- 第四卡：`StatCard("一起去过的城市", travelCount) { onOpenModule(ContentModule.TRAVEL) }`。
- `ContentModule.TRAVEL` 标签同步改为「一起去过的城市」，避免卡名与模块页标题不一致。
- 其余三卡不变。

## 3. 需求对照验收表

| 需求 | 验收动作 | 通过标准 |
|---|---|---|
| R8 心愿串页 | 造 2 条待回应 + 1 条已答应，停留在「待回应」 | 只见 2 张待回应卡，无已答应内容 |
| R8 全量展示 | 同上 | 2 条各自一张卡，非合并 |
| R9 碰碰你 | 观察默认动作与自定义动作 | 淡绿胶囊（PrimarySoft）；自定义可增删，重启保留 |
| R10 双榜 | 造松记 5 星菜、锦记 5 星菜、松记 4 星菜、探店 5 星 | 两榜各只显示对应 5 星菜；4 星与探店不出现 |
| R10 榜单归属 | 编辑 DISH 选「锦记」保存后重进 | 仍为锦记 |
| R11 相册 | 真机点「添加」 | 直接进入系统照片选择器（相册），可多选，最多 4 张 |
| R12 城市卡 | 查看拾光页第四卡 | 标题「一起去过的城市」，数字 = TRAVEL 条数，点击进 TRAVEL |
| R1/R2/R4/R5 | 收到参考图后 | 逐项 1:1 校准（本轮不做猜测性改动） |
| R6 多端 | 320 / 360 / 411 / 600 / 840 / 1280 / 1920 宽度预览 | 无横向溢出、卡片不重叠；本轮布局改动均为弹性宽度 |

## 4. 验证方式

- 单测：`C:\s520j-build` 下 `.\gradlew :app:testDebugUnitTest --rerun-tasks`（新增 `StarBoardTest`，全量须绿）。
- 构建：`scripts/build_apk.ps1 -Tag "v6.0.0-round6"` → `dist/S520J-v6.0.0-round6-debug.apk`。
- 静态扫描：确认 `HorizontalPager`（心愿区）、`list.first()`、`collectedCount`、`OpenMultipleDocuments` 均无残留。
- 布局检测：本轮改动的列表/胶囊/双卡均用 `fillMaxWidth` + 权重/FlowRow，先做 320 与 1920 两端预览核对。
- 实机按第 3 节逐条核对，记录结果后交用户审查。

## 5. 风险与回滚

- 照片选择器在 API < 30 由 Activity 库回退到兼容选择器，仍为相册语义。
- 双榜默认「松记」，历史 DISH 未填榜单时归松记，不迁移、不丢数据。
- 回滚范围：`HeartScreen.kt`、`GalleryScreen.kt`、`ModuleEditScreen.kt`、`Models.kt`、`ContentRepository.kt` + 新增 `StarBoard.kt` / `StarBoardTest.kt`。

## 6. 批准

请确认本 Spec 与 `tasks.md` 后回复「开始执行」；未批准前不改代码。
