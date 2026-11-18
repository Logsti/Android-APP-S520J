# Android 领域层 + UI 层读码笔记

> 范围：`app/src/main/java/com/s520j/app/domain/**`、`ui/**`（AppViewModel、navigation、screens、components、theme、util）、`res/values/{colors,strings,themes}.xml`、`app/src/test/java/com/s520j/app/{domain,ui}`。
> 只读通读，未修改任何业务源码。行号以当前工作区为准。
> 对照基线：`S520J_Android_技术实现需求文档_v1.0.md`（功能/数据/权限）、`前端外观设计需求文档.txt`（视觉 §2.1~§2.4、页面 §4~§7）。

---

## 1. 路由表（S520JNavHost.kt）

`MainShell` 统一 `Scaffold`：底部固定高度占位（子页也不跳动）→ `ReadOnlyNotice`（冻结归档）→ `SyncStatusBar`（离线/失败）→ 碰一碰横幅 → `NavHost(startDestination="heart")`。
登录门在 `MainActivity.kt:33`：`session.loggedIn ? MainShell(vm) : AuthScreen(vm)`，`AuthScreen` 本身没有 route。

| # | route（含参数） | 目标 Composable | 一句话职责 | 底栏主页面 |
|---|---|---|---|---|
| 1 | `heart` | `HeartScreen` | 心动首页：问候卡、今日挑战、快速记录、心愿、碰碰你、共同日历、最近的美好 | ✅ |
| 2 | `gallery` | `GalleryScreen` | 拾光首页：标题/日期卡、四统计入口、照片墙（分类+搜索）、五星榜、FAB 新增 | ✅ |
| 3 | `gallery/photos` | `SharedPhotosScreen` | 把所有带图记录展平成「共同照片」双列网格，点开大图翻页 | ❌ |
| 4 | `starboard` | `StarBoardScreen` | 五星榜：「全部 / 松记五星榜 / 锦记五星榜」三 Tab | ❌ |
| 5 | `tacit` | `TacitScreen` | 默契首页：横向圆形入口 + 选中模块最近 3 条预览 | ✅ |
| 6 | `us` | `UsScreen` | 我们：通知铃、情侣资料卡、设置分组、退出登录 | ✅ |
| 7 | `module/{module}` | `ContentModule.from(...)`；`LETTER` → `LetterListScreen`，其余 → `ModuleListScreen` | 模块独立管理页：标题、搜索、列表、新增、删除；情书页额外有「写给/来自」筛选与多图 | ❌ |
| 8 | `module/{module}/edit?recordId={recordId}&preset={preset}` | `ModuleEditScreen` | 新增/编辑/删除单条记录；`recordId` 空=新增（默认值 `""`），`preset` 仅日历事项用于带入日期 | ❌ |
| 9 | `space` | `SpaceScreen` | 共同空间：创建/绑定码/双方确认/解绑/冻结/重绑/归档导出 | ❌ |
| 10 | `settings/account` | `AccountSecurityScreen` | 账号 ID 展示 + 修改密码 | ❌ |
| 11 | `settings/reminder` | `ReminderSettingsScreen` | 三类提醒开关 + 三个固定提醒时间 | ❌ |
| 12 | `settings/appearance` | `AppSettingsScreen` | 字体缩放、主题/语言只读说明、云同步状态、冲突入口 | ❌ |
| 13 | `sync/conflicts` | `ConflictResolutionScreen` | 冲突快照二选一（保留本机/云端） | ❌ |
| 14 | `settings/preference` | `PreferenceSettingsScreen` | 首页问候语编辑 | ❌ |
| 15 | `settings/data` | `DataManagementScreen` | 回收站入口、ZIP 导出、手动备份/恢复/删除、清理本机 | ❌ |
| 16 | `privacy` | `PrivacyScreen` | 默认可见范围（两档）与照片继承规则说明 | ❌ |
| 17 | `notifications` | `NotificationCenterScreen` | 通知中心 + 今日待提醒汇总 + 全部已读 | ❌ |
| 18 | `about` | `AboutScreen` | 关于 S520J（只读：版本、更新日期、作者 LKS、反馈） | ❌ |
| 19 | `recycle` | `RecycleBinScreen` | 回收站：恢复 / 彻底删除 | ❌ |

补充：
- 路由字符串不是需求文档写的 `/heart`，实际无前导斜杠；`module/{module}` 传入的是**枚举名**（如 `DISH`、`ROMANTIC_MOMENT`）。
- `ModuleEditScreen` 的 `preset` 只对 `CALENDAR_EVENT` 生效（`ModuleEditScreen.kt:84-90`）。
- `DishPickerScreen`（今天吃啥）**没有任何路由或调用点**，见风险清单。
- 底栏 `S520JBottomBar` 只在 4 个主路由显示（`S520JNavHost.kt:165`），选中鼠尾草绿、未选中灰绿、`Surface` 94% 半透明（`S520JBottomBar.kt:58`），符合需求 3.1。

---

## 2. AppViewModel（1037 行）

### 2.1 状态字段清单

| 字段 | 类型 | 含义 |
|---|---|---|
| `localResetReady` | `StateFlow<Boolean>` | 一次性本地数据迁移完成，完成前不渲染主界面 |
| `session` | `StateFlow<SessionProfile>` | userId/nickname/accountId/partnerNickname/coupleStartAt/loggedIn |
| `fontScale` | `StateFlow<Float>` | 全局字体缩放，覆盖 `LocalDensity.fontScale` |
| `reminder` | `StateFlow<ReminderSetting>` | 本机提醒时间 + 三类开关 |
| `defaultVisibility` | `StateFlow<Visibility>` | 新建内容默认可见范围（默认 PRIVATE） |
| `myAvatar` / `partnerAvatar` | `StateFlow<String?>` | 头像 data URL / 本地 URI；空则显示昵称首字 |
| `isAvatarUploading` | `StateFlow<Boolean>` | 头像上传中禁用入口 |
| `todayReminders` | `StateFlow<ReminderCounts>` | `contentRepository.observeAll() + prefs.reminderSetting` 组合出的当日三项计数 |
| `greeting` | `StateFlow<String>` | 首页寄语（默认「今天也要好好吃饭，好好想你。」） |
| `challenge` | `StateFlow<ChallengeState>` | 今日挑战：按天重置、换一换偏移、双方完成标记 |
| `customPokes` | `StateFlow<List<String>>` | 自定义碰碰你动作（默认 4 个由领域层拼接） |
| `pokeBanner` | `StateFlow<CoupleEvent?>` | 对方碰一碰/挑战达成的应用内横幅 |
| `space` | `StateFlow<SpaceEntity?>` | 当前共同空间（含绑定状态机字段） |
| `isReadOnly` | `StateFlow<Boolean>` | 冻结归档只读 |
| `bindFailure` | `StateFlow<String?>` | 本次登录内最近一次绑定/空间操作失败原因 |
| `notifications` | `StateFlow<List<NotificationEntity>>` | 应用内通知（Room） |
| `unreadCount` | `StateFlow<Int>` | 未读数 |
| `syncState` | `StateFlow<SyncState>` | phase/lastSyncAt/message/pending 等 |
| `pendingSyncCount` | `StateFlow<Int>` | outbox 待上传条数 |
| `syncConflicts` | `StateFlow<List<SyncConflictEntity>>` | 冲突快照 |
| `backups` | `StateFlow<List<BackupInfo>>` | 本机备份列表 |
| `messages` | `Flow<UiMessage>`（`Channel.BUFFERED`） | 顶栏 Snackbar 提示，可带「去看看」动作与目标模块 |
| `contents(module)` / `allContents()` / `recycleBin()` | 返回 Flow | 内容查询入口（不缓存，交给 Compose `collectAsState`） |

### 2.2 命令方法分组

- **认证**：`register`、`login`（云端失败且 `offline` 时回退本机缓存登录）、`changeRecoveryCode`（先云端后本机，云端失败不改本机）、`logout`（冻结空间 → 取消周期同步 → 云端/本机登出）、`seedIfNeeded`（迁移 + 种子 + 首次全量同步）。
- **空间**：`createSpace`、`refreshSpaceFromCloud`、`updateSpaceInfo`、`shareInvite`、`refreshBindCode`、`submitBindCode`、`clearBindFailure`、`confirmBindAsOwner`、`confirmBindAsPartner`、`requestUnbind`、`confirmUnbind`、`rejectUnbind`、`cancelUnbind`、`rebind`、`bindState()`。
- **内容**：`saveRecord`（图片先上传换 `mediaRefs`，失败位置填占位符保持索引对齐）、`setWishStatus`/`acceptWish`/`rejectWish`、`deleteRecord`（软删 30 天）、`restoreRecord`、`purgeRecord`。
- **同步**：`syncNow`、`onAppForeground`/`onAppBackground`、`syncOnEnter`、`maybeSync`、`syncCoupleLive`、`resolveConflict`。
- **通知**：`notify`、`markAllRead`、`markRead`、`shouldAskNotifyPermission`、`markNotifyPermissionAsked`、`dismissPokeBanner`。
- **备份导出**：`refreshBackups`、`createBackup`、`restoreBackup`、`deleteBackup`、`clearLocalData`、`exportZip(uri)`、`exportJson(onReady)`。
- **设置/偏好**：`setFontScale`、`setGreeting`、`setReminder`、`setDefaultVisibility`、`setMyAvatar`（`setPartnerAvatar` 已废弃空实现）。
- **心动**：`rotateChallenge`、`toggleChallengeSelf`（本地先标记且不可撤销 → 云端；云端不可用只提示）、`addCustomPoke`、`removeCustomPoke`、`sendPoke`（云端失败落本机未读通知）。

### 2.3 20 秒轮询同步机制

1. `S520JNavHost.kt:98-109` 用 `LifecycleEventObserver`：`ON_START → vm.onAppForeground()`，`ON_STOP → vm.onAppBackground()`。
2. `onAppForeground`（`AppViewModel.kt:314-322`）启动单个 `foregroundSyncJob`，循环 `maybeSync(force=false)` → `delay(SyncPolicy.AUTO_SYNC_INTERVAL_MS)`；**先进后等**，所以回前台立刻尝试一次。重复 ON_START 由 `isActive` 去重。
3. `maybeSync`（`AppViewModel.kt:335-343`）：`SyncPolicy.shouldAutoSync(lastSyncAttemptAt, now, force)` 节流（20s、时钟回拨保护），然后 `syncRepository.syncNow(automatic=true)` → `cloudSpace.refreshLocal()` → `syncCoupleLive()`；`syncNow()` 手动入口会把 `lastSyncAttemptAt` 置为 now。
4. `syncCoupleLive`（`AppViewModel.kt:349-379`）：拉当日挑战并 `mergeCloud`（云端权威、保留本地偏移）；拉双人事件，`insertIfAbsent` 成功才落通知 + `coupleNotifier.notifyEvent`，横幅只显示最后一条新事件。
5. 另外 `SyncRepository.schedulePeriodic()` 注册 WorkManager 15 分钟周期同步（仅联网约束），与前台 20 秒轮询并存。
6. 子页面进入时 `syncOnEnter()` 再补一次，同样受 20 秒节流。

---

## 3. 每个 screen 文件一行职责

### auth/
| 文件 | 行数 | 职责 |
|---|---|---|
| `AuthScreen.kt` | 204 | 注册/登录双 Tab（昵称+账号 ID+密码+确认密码），非路由页，由登录态切换 |

### gallery/
| 文件 | 行数 | 职责 |
|---|---|---|
| `GalleryScreen.kt` | 491 | 拾光首页：标题/日期卡、四统计入口、照片墙（6 分类 + 搜索 + 双列）、五星榜卡、FAB 新增类型弹窗 |
| `SharedPhotosScreen.kt` | 97 | 「共同照片」：`PhotoFeed.sharedPhotos` 展平所有图片，双列网格 + 翻页大图 |
| `StarBoardScreen.kt` | 141 | 五星榜三 Tab 列表，空态给「去记录」 |
| `DishPickerScreen.kt` | 655 | 「今天吃啥」：头图、荤/素/汤/主食份数与二级分类、随机结果、点过的菜（**当前无入口**） |
| `DishPickerViewModel.kt` | 240 | 点菜 UI 状态 + 云端抽页 + 本机快照/内置库降级 + 历史同步 |

### heart/
| 文件 | 行数 | 职责 |
|---|---|---|
| `HeartScreen.kt` | 1132 | 心动全部七区块（问候/挑战/快速记录/心愿/碰碰你/日历/最近美好）+ 寄语弹窗 + 自定义碰碰你 |
| `MonthCalendar.kt` | 166 | 完整月份日历：星期头、网格、今日高亮、选中、事项圆点、前后月、今天 |

### module/
| 文件 | 行数 | 职责 |
|---|---|---|
| `ModuleListScreen.kt` | 174 | 通用模块列表：搜索、可见范围/状态标签、点卡片编辑、删除确认、空态 |
| `ModuleEditScreen.kt` | 570 | 通用新增/编辑页：标题/正文/日期/可见范围/照片（≤4）/评分与榜单/模块专属字段/删除 |

### tacit/
| 文件 | 行数 | 职责 |
|---|---|---|
| `TacitScreen.kt` | 208 | 默契首页：横滑圆形入口（实际 10 个）+ 选中模块最近 3 条预览 + 双方确认提示 |
| `LetterListScreen.kt` | 231 | 「一封情书」专用列表：写给/来自筛选、方向标签、多图、删除 |

### us/
| 文件 | 行数 | 职责 |
|---|---|---|
| `UsScreen.kt` | 571 | 我们首页：通知铃、情侣资料卡（头像可换/爱心/绑定状态）、9 个设置行、退出 |
| `SpaceScreen.kt` | 721 | 共同空间全状态机 UI：未绑定/邀请/等待/申请中（绑定或解绑）/已绑定/冻结/失败 + 导出 |
| `AccountSecurityScreen.kt` | 173 | 账号 ID、修改密码弹窗（8–32 位校验） |
| `PrivacyScreen.kt` | 161 | 默认可见范围单选 + 照片继承/权限校验说明 |
| `ReminderSettingsScreen.kt` | 153 | 重要日期/日历/任务三开关 + 08:00/12:00/20:00 时间胶囊 |
| `AppSettingsScreen.kt` | 140 | 字体 0.85×~1.30× 滑杆、主题/语言只读、同步状态、立即同步、冲突入口 |
| `PreferenceSettingsScreen.kt` | 58 | 首页问候语编辑保存 |
| `DataManagementScreen.kt` | 214 | 回收站入口、ZIP 导出、备份创建/恢复/删除、清理本机 + 长说明 |
| `NotificationCenterScreen.kt` | 154 | 今日提醒汇总卡、通知列表、全部已读 |
| `RecycleBinScreen.kt` | 156 | 回收站列表、恢复、彻底删除确认 |
| `ConflictResolutionScreen.kt` | 135 | 冲突卡片对比本机/云端正文，二选一 |
| `AboutScreen.kt` | 102 | 只读关于页：版本、更新日期、作者 LKS、反馈邮箱、定位 |

---

## 4. 各页面结构 vs 需求文档

### 4.1 心动（需求 4.1 顺序：挑战→快速记录→心愿→碰碰你→日历→最近美好，顶部问候）

实现顺序（`HeartScreen.kt:145-263`）：
1. `GreetingHero`：渐变卡（HeroStart→HeroEnd）、日期+星期、`TimeGreeting.line`、相伴天数大数字、寄语；点击弹窗编辑寄语。✅
2. `今日心动挑战`：两标签 + 文案 + 整宽按钮（未完成/已标记/已完成）+ 温柔提示 + 「换一换」。✅；**无「编辑挑战内容」**（需求 4.3 要求可编辑）⚠️。
3. `快速记录`：标题右侧两个圆形角标（随手记、一封情书）+ 5 个圆形入口（照片/做饭/探店/旅行/浪漫时刻）。与需求 4.4「照片/做饭/探店/文字/浪漫时刻」不符（旅行替代文字）⚠️。
4. `心愿待实现`：5 个视角胶囊（待我回应/待对方回应/已答应/已实现/失效）+ 横向卡片（62% 宽、244dp 高）+ 答应/拒绝/标记实现；拒绝二次确认。✅；空态「去许一个心愿」实际跳到列表页而非编辑器 ⚠️。
5. `碰碰你`：4 个默认动作白色小卡 + 自定义 FlowRow（可删）+ 「＋自定义」；发送后进入通知中心未读。✅
6. `共同日历`：`MonthCalendar` + 所选日事项列表 + 「＋在这天添加」/「管理日历事项」。✅（需求 4.7 的完整月份/切换/今天/标记齐全）
7. `最近的美好`：`ROMANTIC_MOMENT` 最近 3 条纵向卡（图标占位、标题、正文、日期、可见范围）。基本 ✅；无照片缩略图 ⚠️。

### 4.2 拾光（需求 5.x）

- 顶部：`拾起生活里的微光。` + 副标题 + 日期卡（仅本页）✅。
- 四统计入口（`GalleryScreen.kt:139-147`）：共同照片 → 独立页；一起做过的菜 → DISH；一起去过的店 → SHOP；**一起去过的城市 → TRAVEL**。需求第 4 个是「收藏的瞬间」❌（`COLLECTED_MOMENT` 仅存在于新增弹窗）。
- 照片墙：`SectionHeader`+「查看全部」、6 分类横滑胶囊、搜索框、双列 0.86 比例卡、私密锁、编辑角标、多图四宫格、点击大图。✅
- 分类（`PhotoFeed.categories`）：全部/做饭/探店/旅行/日常/**浪漫时刻**，需求为「纪念日」⚠️。
- 五星榜：单入口卡片（展示前 3 名 + 说明），点入 3 Tab 页；需求为「小锦鲤/许愿树」两个独立榜，实现命名「松记/锦记」⚠️。
- 右下角 FAB：仅拾光页，只读时隐藏 ✅。

### 4.3 默契（需求 6.x）

- 圆形入口横滑 `LazyRow`、56dp 圆形、选中鼠尾草绿、下方预览 3 条 ✅ 交互形态。
- 入口数量 **10 个**：重要日子、彼此的承诺、为彼此做过、相处之道、TA 的喜欢、TA 不喜欢、我们的笔记、浪漫时刻、随手记、一封情书；需求要求 **8 个**，且「浪漫时刻」应排第 4 ❌。
- 预览卡显示标题/正文/日期或「下次 X」/状态/可见范围；点击进入模块列表而非记录详情（`onOpenRecord` 参数未使用）⚠️。
- 底部固定提示「相处之道需要双方都确认，才算共同认可。」✅（但确认实现见风险）。

### 4.4 我们（需求 7.x）

- 顶部情侣资料卡：双方头像/首字、昵称、中间爱心、渐变底（PrimarySoft→Sand）、绑定状态/空间名、上传提示 ✅。
- 设置分组（`UsScreen.kt:207-296`）：共同空间、账号与安全、隐私与权限、云同步、数据管理、提醒设置、应用设置、个性化偏好、关于 S520J（带版本徽标）+ 退出登录。
  - 需求 7.3 六模块全部存在 ✅；额外 3 项（账号与安全/云同步/个性化偏好）⚠️（超出但合理）。
  - 未出现「空间与隐私」大标题 ✅。
- 隐私与权限：两档单选（仅自己/共同），未提供「指定授权」（外观文档 7.3 提到，但技术文档最终确认只两档，按技术文档 ✅）。
- 数据管理：备份/恢复/导出/清理/回收站 ✅。
- 提醒设置：三开关 + 时间 ✅；「各自独立设置」未实现（单机一份）⚠️。
- 应用设置：字体可调、主题浅色、语言简体中文只读 ✅（V1 范围一致）。
- 关于 S520J：只读、作者 LKS ✅；但含「阶段一 · 本地 Mock」等过期文案 ⚠️。

---

## 5. 领域规则逐文件

### food/
**BuiltinDishes.kt**：90 道内置菜，字段 `name/level1/level2/group/source`，与云端 `dishes.js` 1:1；`byGroup(group)`、`byGroup(group, sub)`（子分类无命中时回退整组）。

**DishTaxonomy.kt**
- `DishGroup.order = [MEAT, VEG, SOUP, STAPLE]`（展示/请求顺序）。
- 映射：`soupTags={汤,汤羹}`；`stapleTags={米面类,饼,馒头,馄饨,粥,面条,饭,饺子,包子,三明治}`；`meatLevel1={肉类,水产}`；`vegLevel1={蔬菜水果,蛋奶豆制品}`；其余（烘焙甜品饮料/干果/腌咸）→ null（不参与随机）。
- `groupForTags`：多标签按 **汤 ＞ 荤 ＞ 素 ＞ 主食** 取唯一组。
- `fallbackCategories`：四组二级分类兜底，顺序刻意对齐云端 `groupsFromTaxonomy`（主食里「米面类」排最后）。
- `categoriesFrom(raw)`：逐组取云端，空组回退兜底；`optionsFor` = 全部 + 该组；`subCategoryOf` 空值归一为「全部」。

**DishPicker.kt**
- 状态：`counts`（四组默认各 1）、`subs`（默认全部）、`preferNew=true`、`table/haystack/avoid/note/degraded/quotaRemaining/offline`。
- 约束：`MAX_PER_GROUP=4`、`MAX_EXCLUDE=500`；`validation` 总数 0 → 「至少选择 1 道菜」。
- `request`：仅非零组；`exclude = 手动排除 + avoid + 当前桌`；`preferNew` 时带最近点过（去重并剔除已排除）。
- `swapRequest`：只为目标组抽 1 道，排除 avoid+整桌。
- `applyRemote`：去空名、按名去重、按四组顺序重排，结果同时作为候选池。
- `fillLocal`：逐组按需从本机候选（缓存快照/内置库）随机取，`shuffled(random)` 后去重；**不改份数**；凑不齐时 note「菜品范围有限，只凑出 x/y 道」，`degraded=true quota=0`。
- `swapFromPool`：同组、不在桌、不在 avoid、符合二级分类；池里没有返回 null 由调用方补抽。
- `applySwapResult`：取第一个同组且不在桌的新菜；`replace` 会把被换下的菜加入 `avoid`、把新菜加入 `haystack`。
- `newTable`：清空结果 + 整桌入 `avoid`，实现「换一桌尽量不重复」。

**StarBoard.kt**：榜 `松记/锦记`，`tabs=全部+两榜`；`boardOf` 读 `extra["榜单"]`，非法/缺省回落「松记」；`ratingOf` 读 `extra["星级"]`；只有 `DISH && 5 星` 进榜（探店不算）；排序 `eventDate ?: createdAt` 倒序。

### gallery/StarBoard.kt 同上（目录名是 gallery，算法见上）。

### heart/
**CalendarSupport.kt**：`cells(month)` 周日开头，`leadingBlanks = dayOfWeek.value % 7`，总格数向上补齐整周，空格为 null；`eventCounts`/`eventsOn` 以 `eventDate` 毫秒按系统时区转 `LocalDate`，无日期记录忽略。

**DailyChallenge.kt**（`ChallengeState`）
- 字段 `epochDay/offset/selfDone/partnerDone`；`bothDone = selfDone && partnerDone`。
- `forDay(stored, today)`：同一天沿用，否则重置 `(today,0,false,false)` → 跨天清零。
- `promptIndex`：`floorMod(epochDay + offset, promptCount)`，保证稳定轮转不越界。
- `rotate` 只加 offset；`toggleSelf/Partner` 翻转；`markSelfDone` 幂等置 true（UI 端不可撤销）。
- `mergeCloud`：采用云端双方标记、保留本机 offset；若云端 `epochDay` 变了则重建（昨天达成不会带到今天）。
- `encode/decode`：`epochDay|offset|bool|bool` 四段，非法直接 null。

**GalleryPhotoLayout.kt**：1 张→`[0]`；2 张→`[0,3]` 对角线；3 张→`[1,2,3]`（上排居中+下排两张）；≥4→`[0,1,2,3]`；`MAX=4`。

**PhotoFeed.kt**：分类 `全部/做饭/探店/旅行/日常/浪漫时刻`；来源 `PHOTO/DISH/SHOP/TRAVEL/ROMANTIC_MOMENT`；分类由 module 派生；`feed` 要求 `mediaIds` 非空并按 `createdAt` 倒序；`sharedPhotos` 展平，key=`记录id#序号`；`search` 标题或正文忽略大小写；`moduleOf` 供分类直达。

**PokeCatalog.kt**：默认 `拍一拍/抱抱/亲亲/想你`；名称 trim 后空→EMPTY、>8 字→TOO_LONG、与默认或已有重复→DUPLICATE；`encode` 换行拼接、`decode` trim+去重；`all` 默认在前、自定义去重追加。

**QuickRecordRouter.kt**：只处理 `QUICK_RECORD`；按 `extra["type"]` 映射并移除 `type/category`；`migrate` 先把 `RECENT_MOMENT` 整体搬入 `ROMANTIC_MOMENT`，再路由快速记录；旧「文字」→NOTE、旧「浪漫」→ROMANTIC_MOMENT、未知→RECENT_MOMENT，保留 id/时间/图片/可见性。

**QuickRecordTypes.kt**：6 类型（照片/做饭/探店/旅行/浪漫时刻/随手记）；`primary` 5 项不含随手记；`needsPhoto` 对所有合法类型均为 true；`targetModuleOf` 一一映射，未知 null。

**TimeGreeting.kt**：5-8 早上好 / 9-11 上午好 / 12-13 中午好 / 14-17 下午好 / 18-22 晚上好 / 其余夜深了；`line = prefix，nickname & partner`。

**WishBoard.kt**（心愿状态机）
- 四业务状态：待回应/已答应/已实现/被拒绝；五展示视角：待我回应/待对方回应/已答应/已实现/失效。
- 提出人归一：新数据 userId；旧「我」/空 → `ownerId`（空则 selfId）；旧「TA」→ `\u0000partner` 占位保证不等于任何 userId。
- 迁移守卫：`accept/reject` 仅「待回应」且操作者非提出人；`realize` 仅「已答应」；非法迁移返回原对象（UI 用 `updated === record` 判断并提示）。
- 答应写入 `extra["答应人"]=actorId`；展示 `accepterLabel` 自己→「我」否则对方昵称。
- 迁移只写 `extra + updatedAt`，**不改 version**（version 是云端乐观锁基线，自增会导致推送被判冲突）。
- `withStatus` 为历史 API，不校验迁移。

### tacit/
**ImportantDay.kt**：`extra["每年重复"]=="是"` 视为年度重复；不重复返回原 `eventDate`；重复则取今年同月日，已过则明年；2/29 在非闰年通过「向后找最近闰日」处理（注释说落 2/28，实际会跳到下一个闰年，见风险）。

**LetterBoard.kt**：方向以创建者视角存 `TO/FROM`；新增按用户选择，编辑永远保留原方向（`directionForSave`）；`viewerDirection` 非创建者反转；`directionLabel` 输出「写给 X / 来自 X」；`letters` 只收 LETTER，按查看者方向筛选，`eventDate ?: createdAt` 倒序再按 createdAt 倒序。

**Promise.kt**：默认「进行中」；`fulfilledAt` 仅在「已兑现」时返回（防止残留旧时间）。

**RelationshipRule.kt**：`extra["确认"]=="双方已确认"` → 「共同认可」，否则「待对方确认」。**只有一个布尔标记，没有双人分别确认的记录**。

### us/
**ReminderDigest.kt**：`counts(records, setting, todayEpochDay)`；系统时区把 today 转当天 0 点；重要日子（年度重复用 `nextOccurrence` 是否落到今天，否则 eventDate==今天）、日历事项（今天）、任务（PROMISE 未兑现且 eventDate==今天）；各自受开关控制，`total` 求和。
**ReminderSetting.kt**：默认 `20:00` + 三项全开；`enabledCount`；Codec 编码 `time|1|0|1`，非法时间回落 20:00，缺失/非法开关一律按开启；`TIME_OPTIONS = 08:00/12:00/20:00`。

---

## 6. 组件与设计 token

### 6.1 Kit.kt 组件清单

| 组件 | 关键点 |
|---|---|
| `PageContainer` | 铺满 + 背景色 + `imePadding` + `widthIn(max=430.dp)`；可选 `PullToRefreshBox` 下拉刷新 |
| `SoftCard` | 20dp 圆角、1dp Divider 60% 边框、1dp 阴影、16dp 内边距；可点击 |
| `SectionHeader` | 衬线大标题 + 副标题 + 右侧文字动作 |
| `VisibilityChip` | 私密=锁+沙色，共同=人形+浅绿 |
| `TagPill` | 999dp 胶囊标签 |
| `EmptyState` | 居中说明 + 可选动作 |
| `PrimaryPillButton` | 鼠尾草绿胶囊主按钮 |
| `SoftTextField` | 14dp 圆角、定制聚焦色 |
| `BackBar` | 返回箭头 + 标题 + 可选 action |
| `DetailRow` | 左标签右值 |

其余组件：`RecordPhotoGrid`（按 `GalleryPhotoLayout` 摆 1~4 图）、`PhotoPagerDialog`（黑底 HorizontalPager + 缩放 + 计数）、`ImageViewerDialog/ZoomableImage`（0.5×~4× 缩放、放大后可拖、点击空白关闭）、`SnackbarFeedback`（`SnackbarTiming`：普通 2s、带动作 4s、横幅 2s，`Indefinite`+定时 dismiss）。

### 6.2 Token 与需求 §2.1 色板对照

`Color.kt` 声明「色值唯一来源：docs《前端外观设计需求文档》§2.1」，逐项核对：

| 用途 | 需求值 | 代码 token | 结果 |
|---|---|---|---|
| 页面背景 | #F8F6EE | `Background` | ✅ |
| 卡片背景 | #FFFEFA | `Surface` | ✅ |
| 主色 | #7D9676 | `Primary` | ✅ |
| 浅绿色 | #D9E5D2 | `PrimarySoft` | ✅ |
| 沙色 | #EEE4CE | `Sand` | ✅ |
| 主要文字 | #344033 | `TextPrimary` | ✅ |
| 次要文字 | #858778 | `TextSecondary` | ✅ |
| 分割线 | #E8E2D5 | `Divider` | ✅ |
| 辅助粉色 | #F2E2D9 | `AccentPink` | ✅ |

额外 token（需求未列、为心动页参考图补充）：`HeroStart #A9BD93`、`HeroEnd #D8CFA7`、`WarmBrown #6B4E3D`、`DeepGreen #4F6B45`、`ChipPeach #F5E3D2`、`PureWhite #FFFFFF`。

- **Dimens**：`PageMaxWidth=430dp`（需求 8.3「约 430dp」✅）、左右 20dp、卡片间距 14dp、区块 22dp、卡片内边距 16dp、底栏 64dp、FAB 54dp。
- **Shape**：`extraSmall10/small14/medium20/large26/extraLarge30`，`CardCorner=20`、`LargeCardCorner=26`；对应需求 2.3「普通 18~24、大型 24~30」✅。
- **Type**：标题/数字用 `FontFamily.Serif`，正文系统无衬线；`displayLarge 30sp`、`headlineMedium 24sp`、`titleLarge 20sp`、`body 15/13sp`、`labelSmall 11sp`；`S520JNumberStyle` 34sp 用于天数纪念 ✅ 对应需求 2.2。
- **Theme**：仅 `lightColorScheme`，`fontScale` 通过覆盖 `LocalDensity.fontScale` 全局生效，无深色主题（需求 V1 浅色 ✅）。
- **res**：`colors.xml` 仅 3 项（背景/主色/启动图标），`strings.xml` 仅 `app_name`，`themes.xml` 用 `android:Theme.Material.Light.NoActionBar` + 透明状态栏/导航栏。所有页面文案均硬编码在 Kotlin，无字符串资源化。

---

## 7. 测试覆盖矩阵（app/src/test）

| 测试文件 | 用例数（约） | 测试意图 |
|---|---|---|
| `domain/food/DishPickerTest` | 14 | 份数 0~4 夹取、四组全 0 禁用、默认各 1、改份数/分类清空结果、请求只带非零组、preferNew 排除、云端结果排序、池内换菜同组不重复、补抽替换、换一桌避让、离线凑桌及凑不齐说明、来源标签 |
| `domain/food/DishTaxonomyTest` | 8 | 二级分类→四组、甜品/干果/腌咸不参与、多标签优先级、四组兜底顺序、云端空组逐组回退、选项含「全部」、自建库与映射一致 |
| `domain/gallery/StarBoardTest` | 8 | 五星自做饭菜进榜、4 星/无星/探店不进、未填默认松记、两榜隔离、按日期倒序、全部分类合并、空列表、Tab 顺序 |
| `domain/heart/ChallengeStateTest` | 4 | 完成不可撤销、云端合并同日保留偏移、跨天重建、同日沿用/跨天重置 |
| `domain/heart/HeartLogicTest` | 17 | 挑战跨天/偏移/文案轮转/编解码、心愿分组、月份网格、事项计数、碰碰你默认与自定义增删编码 |
| `domain/heart/CrossPageSyncTest` | 4 | 问候时段、问候含双方昵称、标记实现不改 version、未知状态回落 |
| `domain/heart/GalleryPhotoLayoutTest` | 7 | 1/2/3/4 图格子、0 图、超 4 张、格子序号合法 |
| `domain/heart/PhotoFeedTest` | 14 | 6 分类顺序、模块派生、反查模块、五来源+必须有图、筛选、空/标题/内容/叠加搜索、共同照片展平/倒序/顺序/key/空 |
| `domain/heart/QuickRecordRouterTest` | 8 | 已知类型路由并清字段、旧文字→笔记保可见性、未知→历史容器、非快速记录跳过、迁移保留 id/时间/图片、随手记→默契、幂等 |
| `domain/heart/QuickRecordTypesTest` | 5 | 六类型顺序且无「文字」、主入口五项不含随手记、六类型都要照片、类型→唯一模块、归一化 |
| `domain/heart/WishBoardTest` | 15 | 答应/拒绝/实现守卫、提出人不能自答、非法迁移、终态不可变、视角拆分与计数、默认视角、旧「我/TA」兼容、新 userId、答应人展示、withStatus 不改 version、四状态含被拒绝 |
| `domain/tacit/ImportantDayTest` | 4 | 不重复原样、年度取今年或明年、今年未过取今年、已过取明年 |
| `domain/tacit/LetterBoardTest` | 6 | 新增默认 TO、双方视角相反且昵称正确、空昵称 TA 兜底、筛选按查看者方向并倒序、编辑不翻转、LETTER 不进照片墙 |
| `domain/tacit/PromiseTest` | 3 | 默认进行中、已兑现返回时间、非已兑现忽略时间 |
| `domain/tacit/RelationshipRuleTest` | 3 | 未确认/已确认/缺字段默认 |
| `domain/us/ReminderDigestTest` | 5 | 三类汇总、年度重复当天计入、已兑现不计、非今日不计、关开关不计 |
| `domain/us/ReminderSettingTest` | 6 | 空数据默认全开、空串回退、编解码、开关计数、非法时间回退、三个固定档 |
| `ui/components/SnackbarTimingTest` | 3 | 普通 2s、带动作 4s、横幅 2s |

覆盖缺口：`AppViewModel`、`S520JNavHost`、任何 Screen/Composable 的 UI 行为（无 Compose UI/仪器测试）；`DishPickerScreen`/`DishPickerViewModel` 的网络/降级分支；`GalleryScreen` 四统计入口；`SpaceScreen` 全状态流转；`ModuleEditScreen` 字段回填。

---

## 8. 风险与技术债清单

| # | 位置 | 问题 |
|---|---|---|
| 1 | `GalleryScreen.kt:146` | 第四张统计卡是「一起去过的城市」（TRAVEL），需求 5.3/9.2 要求「收藏的瞬间」，入口与需求不符。 |
| 2 | `GalleryScreen.kt:275` + `PhotoFeed.kt:22-28` | 「收藏的瞬间」可从新增弹窗写入 `COLLECTED_MOMENT`，但既无列表入口也不在照片墙来源中，保存后记录在 UI 中不可达（数据黑洞）。 |
| 3 | `PhotoFeed.kt:20` | 照片墙分类用「浪漫时刻」，需求 9.2 分类为「纪念日」，文案/筛选口径不一致。 |
| 4 | `TacitScreen.kt:65-76` | 默契入口实际 10 个（含随手记/一封情书），需求明确 8 个；且「浪漫时刻」排在最后而非第 4 位。 |
| 5 | `TacitScreen.kt:81` | `onOpenRecord` 形参从未使用，预览卡只能进模块列表，无法直达记录详情。 |
| 6 | `DishPickerScreen.kt:76` | 「今天吃啥」整屏与 `DishPickerViewModel` 未被任何路由/页面引用，功能不可达（仅测试覆盖领域层）。 |
| 7 | `HeartScreen.kt:98-104` | 挑战文案 5 条硬编码在 UI 文件，且缺「编辑挑战内容」（需求 4.3）。 |
| 8 | `QuickRecordTypes.kt:15` | 快速记录五入口为 照片/做饭/探店/旅行/浪漫时刻，需求 4.4 为 照片/做饭/探店/文字/浪漫时刻；「随手记/情书」另作角标，信息架构与文档不一致。 |
| 9 | `ModuleEditScreen.kt:110-130` | 表单只在 `loaded==false` 时回填一次，编辑期间云端更新不回填；长编辑后保存会覆盖对方修改（页面间数据不同步）。 |
| 10 | `RelationshipRule.kt:7-16` + `ModuleEditScreen.kt:416-433` | 只用单个「确认=双方已确认」字符串，任一方点「确认」即变共同认可，没有双方独立确认记录，违反需求 9.3「双方确认后才标记」。 |
| 11 | `ImportantDay.kt:34-43` | 注释称 2/29 非闰年落到 2/28，实际函数会向后找闰年返回 2/29（可能整年跳过），注释与行为不一致。 |
| 12 | `ReminderSetting.kt:3` + `ReminderDigest.kt:26-29` | 提醒设置仅本机一份，未实现需求 9.4/10「双方各自独立设置」；且 `setting.time` 只用于展示，不参与任何计算，提醒时间形同虚设。 |
| 13 | `PrivacyScreen.kt:93`、`RecycleBinScreen.kt:55,124` | 仍写「本阶段为本地 Mock / 仅保存在本机」，与已接入云端同步的现状矛盾，会误导用户。 |
| 14 | `AboutScreen.kt:56,59,85,94` | 写死更新日期「2026 年 11 月 6 日」、「阶段一 · 本地 Mock」、反馈邮箱及「接入云端后会补全同步」说明，版本迭代后必然过期。 |
| 15 | `AppSettingsScreen.kt:122` | 文案「后台每 15 分钟自动同步一次」只提 WorkManager，未说明前台 20 秒轮询，与实际行为不符。 |
| 16 | `HeartScreen.kt`（1132 行）、`SpaceScreen.kt`（721）、`UsScreen.kt`（571）、`ModuleEditScreen.kt`（570）、`DishPickerScreen.kt`（655） | 超大文件/超大 Composable，单文件承载多区块与私有组件，改动风险高、复用差。 |
| 17 | `HeartScreen.kt:735`、`ModuleEditScreen.kt:568`、`UsScreen.kt:354`、`MonthCalendar.kt:147,157` | 直接硬编码颜色（`0xFFB3261E`、`0xFFE55757`、`Color.White`），违反 `Color.kt:5-8` 「任何页面不得硬编码颜色」的约定。 |
| 18 | `AuthScreen.kt:124` | 注册提示「账号 ID（8–20 位字母数字）」，而密码/恢复码规则是 8–32 位（`RecoveryCodeRule`），提示与校验口径需核对。 |
| 19 | `res/values/strings.xml:3` | 只有 `app_name`，全部中文文案散落在 Kotlin 中，无法集中校对/做多语言，也容易与需求原文漂移（第 3、13、14 条即实例）。 |
| 20 | `AppViewModel.kt:178` + `AuthScreen.kt:54` + `S520JNavHost.kt:111` | 同一个 `Channel<UiMessage>` 有两个 collector（登录页/主壳），登录切换窗口内消息可能被另一个页面消费，出现提示位置错位。 |
| 21 | `AppViewModel.kt:562-570` | `deleteRecord` 只落本地软删，不触发 `syncNow`，删除要等下一轮 20 秒轮询/手动同步才到对方，与心愿流转的即时同步策略不一致。 |
| 22 | `GalleryScreen.kt:155` | 「查看全部」用当前分类映射单一模块；分类为「全部」时只进 `PHOTO` 模块，与按钮语义不符。 |
| 23 | `HeartScreen.kt:802-804` | 心愿空态按钮「去许一个心愿」实际执行 `onSeeAll` → 模块列表，比直达编辑器多一步，文案与行为不一致。 |
| 24 | `AppViewModel.kt:682-723` | `confirmBindAsOwner` 被 UI 统一用于双方确认（命名误导），`confirmBindAsPartner` 无任何调用点，属演示遗留死代码。 |
| 25 | `StarBoard.kt:20` | 未填/非法 `extra["榜单"]` 静默归入「松记」，用户无法察觉归属被改写。 |
| 26 | `S520JNavHost.kt:439` | 注释与注解挤在同一行（`*/@Composable`），可读性问题（非功能缺陷）。 |

> 最高优先级建议：先修 #2/#4/#6（功能不可达/入口与需求冲突）、#9/#10（数据覆盖与确认语义）、#13/#14（误导性文案）。
