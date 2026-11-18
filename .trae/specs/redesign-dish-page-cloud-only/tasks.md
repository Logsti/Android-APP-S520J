# Tasks

- [x] Task 1: 云端取菜顺序改为「实时优先」：每次随机都先调菜谱大全 API，整页结果按既有规则入库云端池；配额不足或接口失败才退到云端池。
  - [x] 1.1 改造 `cloudbase/functions/_shared/dishHandlers.js` 的 pick 流程：非零分组各尝试 1 次实时抽页（单次点菜 ≤3 次调用），实时结果优先返回
  - [x] 1.2 保持整页 upsert、字段精简（菜名／一级／二级／分组／路径／来源／时间）、10 万道上限按最久未出现淘汰；点菜写入路径补 `trimPool()`
  - [x] 1.3 更新 `cloudbase/dish-rules.test.js`：实时优先、实时失败退池、整页 upsert 去重共 3 条新用例

- [x] Task 2: 删除客户端本机菜谱链路（数据只存云端）。
  - [x] 2.1 删除 `data/repository/DishSnapshotSync.kt` 与 `DishSnapshotPolicyTest.kt`
  - [x] 2.2 删除 `cached_dishes` 实体、`S520JDao.CachedDishDao`、`UserPreferences` 快照与同步时间字段
  - [x] 2.3 `S520JDatabase` 升到 v8，新增 `MIGRATION_6_8`／`MIGRATION_7_8`，仅 `DROP TABLE IF EXISTS cached_dishes`，保留其余数据与 outbox
  - [x] 2.4 移除 ViewModel／页面中的快照下载进度、断点续传与「菜谱下载中断」文案

- [x] Task 3: 降级链改为三级并统一文案：实时 → 云端菜谱池 → 内置 90 道；不再出现「本机缓存／已用本机菜品凑一桌」。

- [x] Task 4: 「点过的菜」改为纯云端 + 上限 1000 + 确认后写入。
  - [x] 4.1 `DishHistoryRepository` 去掉 DataStore 缓存读写与 `pendingSync` 补推，只与云端交互（进程内缓存仅作合并基线）
  - [x] 4.2 上限由 500 改为 1000（`DishHistory.LIMIT`），同名只更新时间
  - [x] 4.3 随机成功不立即记录；新增 `confirmTable()` 与 `ConfirmState`，仅在用户点「就吃这一桌」时写入云端
  - [x] 4.4 写入失败提示「这次没记上，点重试补记」，不落本机；新增 `DishHistoryTest` 4 条用例

- [x] Task 5: 页面排版重做（严格按已确认的效果图）。
  - [x] 5.1 四组合并进单张卡片四行：组名 + 二级分类筛选 chip + 步进器，行间分隔线，行高 ≥52dp
  - [x] 5.2 卡片底部汇总行：本桌 N 道 + 荤／素／汤／主食份数；右侧「新菜优先」开关
  - [x] 5.3 随机按钮通栏，「换一桌／再抽一次」合并为一个；结果列表改「菜名 + 分类标签」两行制 + 每道「换一道」
  - [x] 5.4 新增「就吃这一桌」确认按钮与「确认后才会计入点过的菜」提示；待确认／提交中／已确认／未记上状态可见
  - [x] 5.5 「点过的菜」改为紧凑 chips（FlowRow，最多 12 个 + N）+ 共 N 道 + 清空（二次确认）

- [x] Task 6: 默认头图替换为暖色插画：1080×616、质量 85，193KB（原 2560×1440 / 462KB），文件名与引用不变。

- [x] Task 7: 「关于」页文案：删除标题下小字；「应用定位」改为「致一周年的我们，共同生活记录本」+ 正文；清除「阶段一 · 本地 Mock」等过期描述。

- [x] Task 8: 验证与交付：客户端 41 类 / 302 用例 0 失败；云端 `node --test` 20 用例 0 失败；`assembleDebug` 成功，产物 `dist/S520J-v1.2.2-debug.apk`（22,255,776 字节，versionCode 1202）。

# Task Dependencies
- Task 5 depends on 效果图确认（已确认）
- Task 3 depends on Task 1、Task 2
- Task 5 depends on Task 4（确认按钮与写入时机联动）
- Task 8 depends on Task 1–7
- Task 1、2、4、6、7 之间无依赖，已并行完成
