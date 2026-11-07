# S520J 记录类功能逻辑梳理与统一分类体系设计

- 日期：2026-11-07
- 状态：设计已确认（Q1–Q11 全部收敛），待评审后转实施计划
- 范围：快速记录、拾光页、默契页及其数据归属与交互路径
- 非范围：云端同步/Outbox 语义、登录与空间冻结、首页其余模块（心愿、日历、碰碰你、今日挑战）的视觉与逻辑

---

## 1. 目标

1. 每条记录只有一个 `module` 归属，内容不再跨模块重复存储。
2. 分类完全由 `module` 派生，删除所有基于 `extra` 字符串的分类映射。
3. 同一概念只有一个名称，编辑器、标签、tab、列表标题共用同一常量源。
4. 采集（心动页快速记录）与查看（拾光页 / 默契页）形成闭环，保存后有明确回执与直达路径。

---

## 2. 现状全链路拆解

### 2.1 采集入口 → 落库模块 → 展示归宿

| 采集入口 | 落库模块 | 判别字段 | 实际展示归宿 |
|---|---|---|---|
| 心动·快速记录「照片」 | `QUICK_RECORD` | `extra["type"]="照片"` | 拾光照片墙（日常） |
| 心动·快速记录「做饭」 | `QUICK_RECORD` | `extra["type"]="做饭"` | 拾光照片墙（做饭） |
| 心动·快速记录「探店」 | `QUICK_RECORD` | `extra["type"]="探店"` | 拾光照片墙（探店） |
| 心动·快速记录「文字」 | `QUICK_RECORD` | `extra["type"]="文字"` | 默契「来自快速记录」 |
| 心动·快速记录「浪漫」 | `QUICK_RECORD` | `extra["type"]="浪漫"` | 拾光照片墙（纪念日）+ 默契「来自快速记录」 |
| 拾光·悬浮新增 | `PHOTO` | `extra["category"]` | 拾光照片墙 |
| 拾光·统计卡 | `DISH` / `SHOP` / `COLLECTED_MOMENT` | 星级 / 地址 | 各自独立列表 |
| 心动·最近的美好 | `RECENT_MOMENT` | 无 | 心动页「最近的美好」 |
| 默契·八个模块 | 8 个模块 | 各自字段 | 默契页对应入口 |

### 2.2 展示页口径

| 页面 | 数据来源 | 分类规则 |
|---|---|---|
| 拾光照片墙 | `PHOTO ∪ QUICK_RECORD(有图)` | 全部 / 日常 / 做饭 / 探店 / 旅行 / 纪念日（`PhotoFeed` 硬编码映射） |
| 拾光统计卡 | `feed.size` / `DISH` / `SHOP` / `COLLECTED_MOMENT` | 四套独立口径 |
| 默契「来自快速记录」 | `QUICK_RECORD` 全类型最近 3 条 | 无分类，直接显示 `type` |
| 默契八个入口 | 对应模块 | 模块即分类 |
| 心动「最近的美好」 | `RECENT_MOMENT` | 无分类 |

---

## 3. 问题诊断清单

| 编号 | 问题 | 证据 |
|---|---|---|
| P1 | 同一概念双存储（6 组）：做饭、探店、浪漫、照片、瞬间、文字 | `QUICK_RECORD` vs `DISH` / `SHOP` / `ROMANTIC_MOMENT` / `PHOTO` / `COLLECTED_MOMENT` / `NOTE` |
| P2 | 分类字符串两套且静默丢值 | 编辑页存 `"日常生活"`；照片墙枚举为 `"日常"`，`PhotoFeed.categoryOf` 的 `else -> DAILY` 静默回退 |
| P3 | 同一概念三种表述 | 存储 `"浪漫"` → 默契标签 `"浪漫时刻"` → 拾光分类 `"纪念日"` |
| P4 | 统计数字与列表口径不符 | 「共同照片」= `feed.size`（含快速记录带图），点击进入只含 `PHOTO` 的列表；做饭/探店统计不含快速记录来源 |
| P5 | 一条内容多处出现 | 带图探店记录同时出现在照片墙「全部」「探店」、默契「来自快速记录」、快速记录列表 |
| P6 | 「快速记录」语义双重 | 既是心动页采集动作，又是可打开的存储模块（默契页「查看全部」进入） |
| P7 | 采集后无回执，认知路径断裂 | 保存后无「归入哪个页面」提示；快速记录选「照片」时无分类选项 |
| P8 | 近义概念撞名 | 「最近的美好」`RECENT_MOMENT` 与「收藏的瞬间」`COLLECTED_MOMENT` 语义重叠、分属两页 |
| P9 | 数据模型缺陷 | `extra` 无类型且键名中文；分类为「模块 + 字符串」双轴；`ContentModule.needsPhoto` 与 `QuickRecordTypes.needsPhoto(type)` 两套门禁并存 |

---

## 4. 统一分类体系

### 4.1 唯一归属原则

> 一条记录只属于一个 `module`；一个展示分类只对应一个 `module`；所有分类文案来自同一个常量对象。

- 采集入口只负责**选择类型**，保存时路由到目标模块，不产生 `QUICK_RECORD` 数据。
- 展示页只读取目标模块，不再读取 `extra["type"]` 或 `extra["category"]`。

### 4.2 快速记录类型与路由表

| 类型（入口） | 目标模块 | 归类 | 展示页 |
|---|---|---|---|
| 照片 | `PHOTO` | 日常 | 拾光 · 照片墙「日常」 |
| 做饭 | `DISH` | 做饭 | 拾光 · 照片墙「做饭」+ 统计卡「一起做过的菜」 |
| 探店 | `SHOP` | 探店 | 拾光 · 照片墙「探店」+ 统计卡「一起去过的店」 |
| 旅行 | `TRAVEL`（新增） | 旅行 | 拾光 · 照片墙「旅行」 |
| 浪漫时刻 | `ROMANTIC_MOMENT` | 浪漫时刻 | 拾光照片墙「浪漫时刻」+ 心动「最近的美好」+ 默契「浪漫时刻」 |

- 「文字」类型删除；旧数据迁移到 `NOTE`（保留原可见性），不静默丢弃。
- 五种类型**均要求照片**，类型级 `needsPhoto` 门禁取消，统一使用模块级 `needsPhoto`。

### 4.3 命名统一表

| 概念 | 唯一名称 | 说明 |
|---|---|---|
| 日常照片 | 日常 | 原「日常生活」全部替换 |
| 一起做的菜 | 做饭 | 与 `DISH` 分类一致 |
| 一起去过的店 | 探店 | 与 `SHOP` 分类一致 |
| 旅行 | 旅行 | 与 `TRAVEL` 分类一致 |
| 浪漫记录 | 浪漫时刻 | 存储、标签、tab、默契入口统一 |
| 纪念日 | 纪念日 | 仅保留在 `IMPORTANT_DAY`（默契·重要日子），不再作为照片分类 |
| 快速记录 | 快速记录 | 仅指心动页采集入口，不作为模块列表出现 |

### 4.4 模块清单（17 个）

| 分组 | 模块 | 角色 |
|---|---|---|
| HEART | `QUICK_RECORD` | 采集标识，保留枚举但无 UI 引用、无数据写入 |
| HEART | `WISH` / `CALENDAR_EVENT` | 不变 |
| HEART | `RECENT_MOMENT` | 保留枚举的历史容器，无入口、无新数据 |
| GALLERY | `PHOTO` / `DISH` / `SHOP` / `COLLECTED_MOMENT` | 不变 |
| GALLERY | `TRAVEL` | **新增**，标签「一起去过的地方」，需照片、无评分 |
| TACIT | `IMPORTANT_DAY` / `PROMISE` / `DEED` / `RELATIONSHIP_RULE` / `LIKE` / `DISLIKE` / `NOTE` | 不变 |
| TACIT | `ROMANTIC_MOMENT` | 浪漫时刻唯一存储，入口移至默契页最后一位 |

### 4.5 页面职责矩阵

| 页面 | 展示内容 | 统计/分类 | 写入口 |
|---|---|---|---|
| 心动页 | 心愿、日历、最近的美好（`ROMANTIC_MOMENT` 最近 3 条）、快速记录五入口 | — | 快速记录五入口路由写入；心愿/日历原有入口 |
| 拾光页 | 照片墙（5 个模块带图记录统一聚合）、四张统计卡、五星榜 | 分类 tab：全部 / 做饭 / 探店 / 旅行 / 日常 / 浪漫时刻 | 悬浮新增 → `PHOTO`；tab 空状态 → 当前 tab 对应模块编辑器 |
| 默契页 | 八个模块入口，浪漫时刻置末；删除「来自快速记录」区块 | 模块即分类 | 各模块原有入口 |

### 4.6 拾光统计口径

| 卡片 | 数字来源 | 点击行为 |
|---|---|---|
| 共同照片 | 照片墙「全部」数量（含五个模块带图记录） | 将 tab 切回「全部」，不跳页 |
| 一起做过的菜 | `DISH` 条数 | 打开 `DISH` 列表 |
| 一起去过的店 | `SHOP` 条数 | 打开 `SHOP` 列表 |
| 收藏的瞬间 | `COLLECTED_MOMENT` 条数 | 打开 `COLLECTED_MOMENT` 列表 |

「五星榜」文案调整为「只记录菜品与餐厅的体验，不评价人和关系」，点击仍进 `DISH`。

### 4.7 `PhotoFeed` 重构

```kotlin
object PhotoFeed {
    const val ALL = "全部"
    const val COOKING = "做饭"
    const val SHOP = "探店"
    const val TRAVEL = "旅行"
    const val DAILY = "日常"
    const val ROMANTIC = "浪漫时刻"

    val categories = listOf(ALL, COOKING, SHOP, TRAVEL, DAILY, ROMANTIC)
    val sources = listOf(PHOTO, DISH, SHOP, TRAVEL, ROMANTIC_MOMENT)

    fun categoryOf(record: ContentRecord): String? = when (record.module) {
        PHOTO -> DAILY
        DISH -> COOKING
        SHOP -> SHOP
        TRAVEL -> TRAVEL
        ROMANTIC_MOMENT -> ROMANTIC
        else -> null
    }

    fun moduleOf(category: String): ContentModule?  // 供 tab 直达与空状态跳转
}
```

- 聚合条件：`module in sources && mediaIds.isNotEmpty()`，按 `createdAt` 倒序。
- 删除 `QuickRecordTypes` 字符串映射分支。

### 4.8 数据模型变更

| 项 | 变更 |
|---|---|
| `ContentModule` | 新增 `TRAVEL`；`QUICK_RECORD` / `RECENT_MOMENT` 保留但无 UI 引用 |
| `ContentRecord.extra` | 新增记录不再写 `type` / `category`；`星级` / `地址` / `状态` 等字段保持不变 |
| `QuickRecordTypes` | `all = [照片, 做饭, 探店, 旅行, 浪漫时刻]`；新增 `targetModuleOf(type)`；`needsPhoto` 恒为 `true` |
| `PhotoFeed` | 见 4.7 |
| `SeedData` | 两条 `RECENT_MOMENT` 预设移入 `ROMANTIC_MOMENT`；不再生成 `RECENT_MOMENT` / `QUICK_RECORD` |

### 4.9 历史数据迁移

一次性迁移，幂等，由 DataStore 布尔标记 `taxonomy_v2_migrated` 守卫：

1. 读取全部 `QUICK_RECORD`：
   - 照片 → `PHOTO`；做饭 → `DISH`；探店 → `SHOP`；旅行 → `TRAVEL`；浪漫/浪漫时刻 → `ROMANTIC_MOMENT`；文字 → `NOTE`；未知值 → `RECENT_MOMENT`。
   - 保留 `id`、`ownerId`、`ownerName`、`visibility`、`title`、`content`、`eventDate`、`mediaIds`、`createdAt`。
   - 移除 `extra["type"]` / `extra["category"]`，其余键保留。
2. 将既有 `RECENT_MOMENT` 记录整体移入 `ROMANTIC_MOMENT`。
3. 迁移后旧 `QUICK_RECORD` 行不再存在；重复执行结果不变。

---

## 5. 分阶段实施计划

### 阶段 1：模块归属调整

| 序号 | 任务 | 关键文件 |
|---|---|---|
| 1.1 | 新增 `TRAVEL` 模块（GALLERY，需照片、无评分） | `data/model/Models.kt` |
| 1.2 | `QuickRecordTypes` 改为 5 类并新增 `targetModuleOf` | `domain/heart/QuickRecordTypes.kt` |
| 1.3 | 新增 `QuickRecordRouter`（路由 + 迁移），先写失败测试 | `domain/heart/QuickRecordRouter.kt`（新） |
| 1.4 | `PhotoFeed` 改为模块派生 + 新 tab 顺序，重写测试 | `domain/heart/PhotoFeed.kt` |
| 1.5 | 心动页五入口直接打开目标模块，删除文字入口，新增旅行入口 | `ui/screens/heart/HeartScreen.kt` |
| 1.6 | 编辑页删除 `PHOTO` 分类卡片与 `extra["category"]` 写入 | `ui/screens/module/ModuleEditScreen.kt` |
| 1.7 | 默契页删除「来自快速记录」区块，浪漫时刻移至末位 | `ui/screens/tacit/TacitScreen.kt` |
| 1.8 | 拾光页改用新 `PhotoFeed`，统计卡与 tab 对接 | `ui/screens/gallery/GalleryScreen.kt` |
| 1.9 | `SeedData` 调整 + 启动时执行迁移 | `data/repository/ContentRepository.kt`、`AppViewModel.kt` |

**归属结果**：每个子模块有且只有一个归属页面与一个分类位置，`QUICK_RECORD` 退出存储层。

### 阶段 2：交互流程优化

| 序号 | 任务 | 说明 |
|---|---|---|
| 2.1 | 保存回执 | 保存成功提示「已记入 拾光 · 做饭」，附「去看看」直达目标模块；文案与分类常量同源 |
| 2.2 | 统计卡口径统一 | 「共同照片」切回「全部」tab；其余三张卡打开对应模块列表 |
| 2.3 | 照片墙 tab 直达 | 每个 tab 的「查看全部」打开当前分类对应模块列表（含 `TRAVEL`） |
| 2.4 | 空状态跳转 | 空 tab 的「去添加」打开当前分类对应模块编辑器，而非固定 `PHOTO` |
| 2.5 | 心动「最近的美好」 | 读取 `ROMANTIC_MOMENT` 最近 3 条；「查看全部」进 `ROMANTIC_MOMENT` 列表；空状态进编辑器 |
| 2.6 | 文案统一收口 | 所有分类文案取自 `PhotoFeed` / `QuickRecordTypes` 常量，禁止散落字符串 |

### 阶段 3：验证标准

**量化验收指标（全部满足才算通过）**

| 编号 | 指标 | 验证方法 |
|---|---|---|
| V1 | 归属唯一：任一记录 `module` 只有一个值；`QUICK_RECORD` / `RECENT_MOMENT` 新增数据为 0 | 单测 + 数据库查询 |
| V2 | 数量一致：『共同照片』数字 = 照片墙「全部」瓦片数；『一起做过的菜』=「做饭」tab 数；『一起去过的店』=「探店」tab 数，误差 0 | 实机 3 组对照 |
| V3 | 命名零残留：分类值/ UI 文案中「日常生活」「浪漫」作为独立分类 0 命中 | 全局搜索 + 人工核对 |
| V4 | 路径连贯：心动任一入口 → 保存 → 回执出现（≤3 秒）→ 点「去看看」直达对应分类，全程 ≤2 次点击 | 实机 5 条路径 |
| V5 | 无重复：同一 `id` 在同一页面列表内不重复；跨页重复仅允许「心动 3 条摘要 + 默契全量」这一种 | 实机核对 |
| V6 | 迁移完整：迁移前 `QUICK_RECORD` 条数 = 迁移后各目标模块新增条数之和；连续执行两次结果一致；`mediaIds` 全保留 | 单测（含幂等） |
| V7 | 回归：既有 81 条单测全绿 + 新增路由/聚合/迁移测试通过；`assembleDebug` 成功 | `.\gradlew :app:testDebugUnitTest --rerun-tasks`、`assembleDebug` |

**实机验证步骤（10 条）**

1. 心动页确认五入口为：照片、做饭、探店、旅行、浪漫时刻（无文字）。
2. 依次用五个入口各记一条（含 1 张照片），逐条确认回执中的页面名正确。
3. 点回执「去看看」，确认直达对应模块且新记录在列表首位。
4. 拾光照片墙确认 tab 顺序为：全部 / 做饭 / 探店 / 旅行 / 日常 / 浪漫时刻。
5. 逐 tab 核对数量与统计卡数字一致（V2）。
6. 点「共同照片」卡，确认回到「全部」tab 且不跳页。
7. 照片编辑器确认无「分类」卡片，保存后落「日常」。
8. 默契页确认无「来自快速记录」区块，浪漫时刻为最后一个入口。
9. 心动「最近的美好」显示刚记的浪漫时刻，点「查看全部」进 `ROMANTIC_MOMENT` 列表。
10. 覆盖安装（不卸载）后确认旧快速记录已按类型出现在对应模块，图片仍可显示。

---

## 6. 风险与非目标

| 项 | 说明 |
|---|---|
| 枚举兼容 | `QUICK_RECORD` / `RECENT_MOMENT` 保留枚举，避免旧库记录无法反序列化；后续版本可评估移除 |
| 迁移顺序 | 先搬 `RECENT_MOMENT` 再路由 `QUICK_RECORD`，避免旧文字记录被二次搬迁 |
| 图片可见性 | `mediaIds` 为持久 URI，迁移只改 `module`，不触碰 URI，无需重新取权限 |
| 非目标 | 不做跨设备/云端迁移；不改 Outbox 语义；不改首页其余模块视觉；不做分类筛选记忆 |
