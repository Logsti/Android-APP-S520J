# 第 6 轮 UI 修正 Tasks

对应 Spec：`../specs/2026-11-07-round6-ui-fixes/spec.md`
执行前必须获得用户「开始执行」批准。

## T1 心愿三态改列表（High）

文件：`app/src/main/java/com/s520j/app/ui/screens/heart/HeartScreen.kt`

- [x] `WishSection` 去掉 `HorizontalPager`，改 `selectedIndex` 状态。
- [x] tab 选中态绑定 `selectedIndex`。
- [x] 内容区改为同状态 `LazyRow`（62% 卡宽、每条心愿一张卡，不串其他状态）。
- [x] `WishCard` / `WishEmptyCard` 改 `fillMaxWidth()`。
- [x] 清理分页相关 import。
- [x] 验证：两条待回应 → 两张卡片；切 tab 无其他状态内容。

## T2 碰碰你恢复图 4 白色小卡（High）

文件：`HeartScreen.kt`

- [x] 恢复 `PokeCard`（白色 18dp 圆角小卡，棕色图标 + 标签），默认四卡等宽排布。
- [x] 自定义动作用 `PokeCard` + 右上角 `×`。
- [x] 保留自定义持久化与发送逻辑。
- [x] 清理无用 import。
- [x] 验证：外观与图 4 一致，重启后自定义动作仍在。

## T3 双五星榜领域模型与测试（High）

文件：`app/src/main/java/com/s520j/app/domain/gallery/StarBoard.kt`（新建）
文件：`app/src/test/java/com/s520j/app/domain/gallery/StarBoardTest.kt`（新建）

- [x] 先写失败测试：5 星 DISH 进榜、4 星不进、SHOP 不进、默认松记、两榜隔离。
- [x] 实现 `StarBoard`（`SONG`/`JIN`/`boardOf`/`ratingOf`/`isFiveStarDish`/`list`）。
- [x] `.\gradlew :app:testDebugUnitTest --rerun-tasks` 通过。

## T4 编辑页榜单选择与模型口径（High）

文件：`app/src/main/java/com/s520j/app/data/model/Models.kt`
文件：`app/src/main/java/com/s520j/app/ui/screens/module/ModuleEditScreen.kt`

- [x] `TRAVEL` 标签改「一起去过的城市」；`SHOP` 评分入口保持不动（仅不进榜）。
- [x] DISH 编辑页增加松记/锦记胶囊选择，载入已有 `extra["榜单"]`，默认松记。
- [x] 保存 DISH 时写入 `extra["榜单"]`。
- [x] 星级说明文案改为「只记录自己做的饭菜……探店不计入」。
- [x] 验证：编辑 DISH 选择锦记后，重进编辑页仍为锦记。

## T5 拾光页双榜与城市卡（High）

文件：`app/src/main/java/com/s520j/app/ui/screens/gallery/GalleryScreen.kt`
文件：`app/src/main/java/com/s520j/app/data/repository/ContentRepository.kt`

- [x] 移除单张「五星榜」卡。
- [x] 新增「松记五星榜」「锦记五星榜」两张卡，只列 5 星 DISH（最多 3 条），空榜文案。
- [x] 第四统计卡改「一起去过的城市」，统计 `TRAVEL`，点击进 `TRAVEL`。
- [x] DISH 种子补 `榜单`，两榜各有 5 星示例。
- [x] 验证：4 星菜与探店不出现；城市卡数字随 TRAVEL 记录变化。

## T6 相册选择器（High）

文件：`ModuleEditScreen.kt`

- [x] 改用 `PickMultipleVisualMedia(4)` + `PickVisualMedia.ImageOnly`。
- [x] 最多保留 4 张，缩略图与清空正常。
- [x] 删除 `OpenMultipleDocuments` 引用。
- [x] 验证：真机点击添加直接进入系统照片选择器。

## T7 全量验证与交付（High）

- [x] `C:\s520j-build` 下 `.\gradlew :app:testDebugUnitTest --rerun-tasks` 全绿。
- [x] `scripts/build_apk.ps1 -Tag "v6.0.0-round6"` 出包。
- [x] 静态扫描：`OpenMultipleDocuments`、`list.first()`、`collectedCount` 无残留。
- [x] 按 Spec 第 3 节 1–5 条逐项实机核对，记录结果后交用户审查。
- [x] 用户审查通过后再提交/打 tag（阶段门禁）。
