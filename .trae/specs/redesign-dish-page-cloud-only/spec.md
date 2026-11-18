# 点菜页排版重做与数据全面上云 Spec

## Why
当前「今天吃啥」页与产品设定有三处偏离：菜谱被整库下载到手机（Room `cached_dishes` + 下载进度），与「数据全部存云端」冲突；四组份数各占一张卡，纵向浪费过半屏幕；「关于」页仍是占位文案且残留「阶段一 · 本地 Mock」等过期描述。

## What Changes
- **头图**：默认头图替换为暖色插画（1080 宽压缩后打包），保留「点我换图／恢复默认」。
- **排版**：荤／素／汤／主食合并进一张卡的四行（组名 + 二级分类筛选 + 步进器），下方合并「本桌 N 道 + 新菜优先」，随机按钮通栏；结果列表改两行紧凑制；新增「就吃这一桌」确认按钮。
- **取菜顺序**：每次点菜优先实时调用菜谱大全 API（返回整页按既有规则入库云端池），其次云端池，最后内置 90 道。**BREAKING**
- **删除本机菜谱链路**：`cached_dishes` 表、`DishSnapshotSync`、快照下载进度与断点续传、DataStore 快照字段。**BREAKING**
- **点过的菜**：仅云端存储，上限 500 → **1000**；只有点击「就吃这一桌」才写入，随机但未确认的菜不记录。**BREAKING**
- **关于页**：删除标题下方的小字「两个独立的人，一段共同的日子。」；「应用定位」改为「致一周年的我们，共同生活记录本」；清除过期 Mock 文案。

## Impact
- Affected specs: `add-dish-picker-and-fix-transient-feedback`（取其点菜域，数据层要求被本规格取代）
- Affected code:
  - 云端：`cloudbase/functions/_shared/dishHandlers.js`（取菜顺序）、`cloudbase/dish-rules.test.js`
  - 客户端数据：`data/repository/DishSnapshotSync.kt`（删除）、`data/local/Entities.kt`、`S520JDao.kt`、`S520JDatabase.kt`（v7→v8）、`data/prefs/UserPreferences.kt`、`data/cloud/DishHistoryRepository.kt`、`DishRandomRepository.kt`
  - 客户端 UI：`ui/screens/gallery/DishPickerScreen.kt`、`DishPickerViewModel.kt`、`ui/screens/us/AboutScreen.kt`、`res/drawable-nodpi/dish_header_default.*`
  - 测试：`app/src/test/.../food/*`、`data/repository/DishSnapshotPolicyTest.kt`（随源码删除）

## ADDED Requirements

### Requirement: 实时优先的取菜顺序
系统 SHALL 在每次随机点菜时按「实时菜谱大全 API → 云端菜品池 → 内置 90 道」的顺序取菜；实时返回的整页菜品按既有去重与字段规则入库云端池。

#### Scenario: 配额充足
- **WHEN** 用户点击随机配一桌且当日剩余额度高于保底 20 次
- **THEN** 随机挑一个分类关键词（取自 1164-2 分类树的二级/三级名），以 `cpName` 向 1164-1 抓最多 3 页，整页入库后按分组过滤展示，命中项标注来源为「实时」，不足部分用云端池补齐

#### Scenario: 关键词无结果
- **WHEN** 供应商返回「未找到相关菜谱」
- **THEN** 视为空页继续换关键词，不计失败、不写日志噪音，仍由云端池与内置菜品兜底

#### Scenario: 配额不足或接口失败
- **WHEN** 当日剩余额度低于保底或实时调用失败
- **THEN** 自动改用云端池，仍不足时使用内置 90 道，并在界面标注来源；不出现「本机缓存」层级

### Requirement: 确认后才记录点过的菜
系统 SHALL 仅在用户点击「就吃这一桌」后，把当前这一桌写入云端「点过的菜」。

#### Scenario: 随机后未确认
- **WHEN** 用户随机出结果但未点击确认就离开页面
- **THEN** 云端「点过的菜」不新增任何记录

#### Scenario: 确认写入失败
- **WHEN** 点击确认但云端写入失败
- **THEN** 提示「这次没记上，点重试补记」，不落本机缓存，再次点击可重试

### Requirement: 紧凑的四组排版
系统 SHALL 把荤／素／汤／主食四组合并进一张卡片内的四行，每行依次为组名、二级分类筛选、份数步进器，组间用分隔线区分。

#### Scenario: 小屏首屏
- **WHEN** 在 360dp 宽、标准字号的设备打开页面
- **THEN** 四组与随机按钮在首屏内完整可见，无横向滚动，触控区域不小于 44dp

### Requirement: 应用定位文案
系统 SHALL 在「关于」页展示正式格式的应用定位：标题「致一周年的我们，共同生活记录本」+ 定位正文。

#### Scenario: 打开关于页
- **WHEN** 用户进入「关于 S520J」
- **THEN** 标题下方不再显示「两个独立的人，一段共同的日子。」，「应用定位」区块显示新标题与说明文字

## MODIFIED Requirements

### Requirement: 点过的菜上限
记录上限由 500 条改为 **1000** 条，同名菜只更新时间；分页与「最近点过」取数逻辑不变（按时间倒序）。

### Requirement: 点菜页降级标注
界面只展示三种来源：实时／云端菜谱池／内置菜品；不再出现「正在下载菜谱 x/y」「下载中断」等本机快照提示。

## REMOVED Requirements

### Requirement: 本机菜谱快照
**Reason**: 与「数据全部存储云端、手机端不做本地存储」的产品设定冲突。
**Migration**: Room 7→8 迁移仅 `DROP TABLE cached_dishes`，其余本地缓存与未同步 outbox 保留；首启无需重新下载菜谱。

### Requirement: 点过的菜本机缓存与待补推队列
**Reason**: 记录只在云端，避免双写不一致。
**Migration**: 移除 `pendingSync` 与 DataStore 缓存读写；升级后首次进入页面从云端拉取（旧本机缓存直接丢弃）。
