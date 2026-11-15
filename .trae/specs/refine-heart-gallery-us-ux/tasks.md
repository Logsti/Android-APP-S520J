# Tasks

- [x] Task 1: 心愿状态机（TDD 先行）
  - [x] 1.1 先写单测：`WishBoardTest` 覆盖 答应/拒绝/实现 的合法与非法迁移、视角分组、旧数据「提出人=我/TA/空」解析
  - [x] 1.2 扩展 `WishBoard`：新增 `被拒绝` 状态、`提出人` 解析函数、`accept/reject/realize` 守卫、按视角分组
  - [x] 1.3 `AppViewModel` 增加 `acceptWish/rejectWish`，`setWishStatus` 走守卫；`saveRecord` 为心愿补写 `提出人=当前 userId`
  - [x] 1.4 运行 `WishBoardTest` 通过

- [x] Task 2: 心愿 UI 重构（依赖 Task 1）
  - [x] 2.1 `HeartScreen.WishSection` 五个视角（待我回应/待对方回应/已答应/已实现/失效），横向可滚动胶囊 + 计数，默认选中规则
  - [x] 2.2 `WishCard` 按状态显示动作：答应/拒绝（二次确认）、等待对方、标记实现、只读徽标
  - [x] 2.3 校验：三张卡的按钮不会越权出现

- [x] Task 3: 问候卡单行 + 动态昵称
  - [x] 3.1 `HeartScreen` 传 `session.nickname` / `session.partnerNickname`（空值兜底「你」「TA」）
  - [x] 3.2 GreetingHero 问候语 20sp/18sp、`maxLines=1`、`softWrap=false`、省略号
  - [x] 3.3 校验：长昵称不换行、未绑定显示「你 & TA」

- [x] Task 4: 随手记
  - [x] 4.1 `Models.kt` 新增 `JOT(ModuleGroup.TACIT, "随手记", COUPLE, needsPhoto = true)`；全量修复由此产生的穷举 when / 硬编码列表
  - [x] 4.2 `QuickRecordTypes` 新增「随手记」→ `JOT`；`QuickRecordRouter` 迁移映射
  - [x] 4.3 `QuickRecordSection` 改为 3 列 × 2 行网格（6 入口）
  - [x] 4.4 `TacitScreen` 入口列表新增随手记
  - [x] 4.5 单测：6 个类型、随手记路由、迁移映射
  - [x] 4.6 校验：随手记不出现在心动页「最近的美好」

- [x] Task 5: 拾光一卡多图（依赖 无）
  - [x] 5.1 先写单测 `GalleryPhotoLayoutTest`：1→[0]、2→[0,3]、3→[1,2,3]、4→[0,1,2,3]
  - [x] 5.2 实现 `GalleryPhotoLayout.cells(count)`
  - [x] 5.3 `GalleryScreen.PhotoTile` 按格子渲染多图，空格保留背景，间距 2dp
  - [x] 5.4 校验：1/2/3/4 张的视觉排布符合规格

- [x] Task 6: 拾光多图浏览（依赖 Task 5）
  - [x] 6.1 抽出可复用「可缩放图片」composable（供单图与分页共用）
  - [x] 6.2 新增 `PhotoPagerDialog`：HorizontalPager + 缩放 + 页码 + 关闭按钮 + 返回键
  - [x] 6.3 `PhotoTile` 照片区点击打开查看器；右下角新增编辑图标进入记录编辑
  - [x] 6.4 校验：4 图可左右切换、缩放、关闭；编辑入口可用

- [x] Task 7: 我们页信息层级
  - [x] 7.1 头像资料卡移到标语下方（云同步卡移除）
  - [x] 7.2 `SettingRow` 支持可选 trailing；设置列表在「隐私与权限」后插入「云同步」行，副标题复用同步状态描述
  - [x] 7.3 校验：行尾刷新按钮触发 `syncNow()`，同步中状态正确

- [x] Task 8: 质量门禁
  - [x] 8.1 全量单测通过
  - [x] 8.2 `assembleDebug` 构建成功，产出 v1.1.0 APK（versionCode 1100）
  - [x] 8.3 提交实机验收步骤

# Task Dependencies

- Task 2 依赖 Task 1
- Task 6 依赖 Task 5
- Task 8 依赖 Task 1–7
- Task 3、Task 4、Task 5 相互独立，可并行
