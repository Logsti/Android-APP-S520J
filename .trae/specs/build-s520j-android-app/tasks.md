# Tasks — S520J Android 原生 APP 实施计划

> 本文档即项目「实施计划文档」。包含：开发阶段划分、每阶段交付内容、**时间节点与里程碑**、**可逐条执行的实机测试步骤**、以及必须遵守的验证门禁流程。

## 一、协作流程与门禁规则（强制，不得跳过）

1. **计划确认**：本计划经用户确认后才进入编码。
2. **阶段编码**：按 Task 0 → Task 9 顺序推进；同阶段内无依赖的子任务可并行。
3. **阶段自检**：每个阶段编码完成后，先做该阶段的代码审查 + 单元测试全绿 + 关键路径自测 + 构建 APK。
4. **暂停并请求实机验证（第 1 次）**：提交 APK 路径 + 该阶段「实机测试步骤」，然后**停止开发**，等待用户实机测试。
5. **问题修复与全量回归**：用户反馈任何问题后 → 定位 → 修复 → 对**整个 APP 全部代码**重新做完整审查、回归测试与功能验证（不只是改动点）→ 确认无问题后**再次发起实机验证请求（第 2 次）**。
6. **阶段通过**：用户实机验证通过后 → 勾选 tasks.md 与 checklist.md 对应项 → **先执行一次 Git 提交做版本保存**（提交信息格式 `stage-N: <阶段名> (vX.Y.Z-stageN) 实机验证通过`）→ 打 Git tag → 进入下一阶段。

禁止事项：
- 未完成阶段验证就进入下一阶段；
- 未做 Git 版本保存就进入下一阶段；
- 未完成全量回归就再次发起验证请求；
- 把「编译通过」当作「验证通过」。

> Git 仓库：`c:\Users\LINKUNSONG\Desktop\Ai编程\APP\Project_S520J\Project_S520J\.git`（已初始化，`dist/*.apk`、`build/`、`.gradle/`、`local.properties` 已在 `.gitignore` 中排除）。

---

## 二、代码生成策略：TDD（红-绿-重构）

**铁律**：没有先失败的测试，就不写生产代码。

每个行为（新功能、缺陷修复、行为变更、重构）按以下循环推进：

1. **RED**：先写一个最小测试，明确「应该发生什么」，名字必须描述行为。
2. **验证 RED**：执行 `gradlew testDebugUnitTest`，确认测试**因功能缺失而失败**（不是编译报错、不是拼写问题）。
3. **GREEN**：只写让该测试通过的最少代码，不添加任何未被测试要求的功能。
4. **验证 GREEN**：全部测试通过，输出无失败无警告。
5. **REFACTOR**：仅在绿灯状态下清理重复与命名，不新增行为。
6. **重复**：下一个行为回到第 1 步。

配套约定：

- 测试位置：`app/src/test/java/...`（JVM 单测，纯逻辑）；涉及 Room/Compose/权限的放 `app/src/androidTest/...`。
- 测试依赖已就绪：JUnit 4 + `kotlinx-coroutines-test`。
- **存量代码基线**：阶段 1 已有的纯逻辑（密码规则、字段编解码、绑定状态机、回收站/冻结判定）先补「特征测试」作为回归基线；此后任何新增或修改的行为一律测试先行。
- **缺陷修复**：先写一个能复现该缺陷的失败测试，再修复；测试即防回归证据。
- 允许跳过 TDD 的例外（仅限配置与样式，需在阶段说明中列明）：Gradle/清单/资源 XML、纯文案与配色 token、启动器图标资源。业务逻辑代码无例外。

---

## 三、时间节点与里程碑

### 3.1 时间口径说明

- 计划确认日：**2026-11-06**（本计划提交确认当天，T+0）。
- 「参考工期」仅统计**我方编码 + 自检 + 出包**，**不含用户实机验证的等待时间**，也不含因反馈返工而追加的时间。
- 每个阶段的「计划完成日」为 T+0 起算的自然日；由于第 6 步要求「验证通过才可推进」，**实际完成日 = 计划完成日 + 该阶段实机验证与返工的等待时长**。
- 工期与日期为排期参考，不作为承诺，用于让你判断进度是否明显偏离。

### 3.2 里程碑总表

| 阶段 | 里程碑 | 交付内容 | 参考工期 | 计划完成日 | 时间节点（进入条件 → 退出条件） | APK tag | 当前状态 |
|---|---|---|---|---|---|---|---|
| 0 | M0 工具链就绪 | 免管理员工具链 + 一键构建脚本 | 已完成 | 2026-11-06（已完成） | 无 → `scripts/build_apk.ps1` 可产出 APK | — | 已完成 |
| 1 | M1 可跑骨架 | 工程骨架、设计 token、主题、底部导航、通用组件、Room/DataStore 基座、导航壳与入口、六个次级页面 | 已完成 | 待用户验证通过 | M0 通过 → 四页可切换且配色符合前端文档 §2，注册/登录/换机恢复三条流程有明确反馈 | `v0.1.0-stage1` → `v0.1.1-fix1` → `v0.1.2-fix2` → `v0.1.3-fix3` → `v0.1.4-fix4` | 代码完成，`v0.1.4-fix4` 待第四轮实机验证 |
| 2 | M2 账号闭环 | 注册、登录、换机恢复、密码哈希、会话抽象、离线态、修改密码入口 | 已完成 | 待用户验证通过 | M1 验证通过 → 三条流程可用且有明确成功/失败提示；密码修改 UI 可达；离线时注册/登录提示需联网 | `v0.2.0-stage2` | 代码完成，`v0.2.0-stage2` 待实机验证 |
| 3 | M3 绑定闭环 | 共同空间创建、绑定码、双方确认绑定、七态状态机、解绑冻结与重新绑定 | 2 天 | 2026-11-10 | M2 验证通过 → 七态可复现，解绑后只读可导出 | `v0.3.0-stage3` | 已完成（2026-11-07 实机通过，tag `v0.3.0-stage3`） |
| 4 | M4 心动页交付 | 问候卡、今日挑战、快速记录、心愿、碰碰你（DIY）、共同日历、最近的美好；五模块按参考图 1–5 还原；快速记录与拾光/默契实时同步 | 3 天 | 2026-11-13 | M3 验证通过 → 页面顺序正确，日历可点选日期，DIY 可增删，五模块视觉按参考图还原，跨页同步生效，主子页切换无位移 | `v0.4.0-stage4` | 已完成（2026-11-07 实机通过，tag `v0.4.0-stage4`） |
| 5 | M5 拾光页交付 | 顶部文案与日期卡、四模块独立管理页、照片墙、分类筛选、五星榜、FAB | 2 天 | 2026-11-07 | M4 验证通过 → 四模块可独立增删改查，双列照片墙 | `v6.2.0-stage5` | 已完成（实机验证通过） |
| 6 | M6 默契页交付 | 八圆图标入口、八个独立管理页、相处之道双方确认、重要日子年度重复 | 3 天 | 2026-11-07 | M5 验证通过 → 八模块可独立进入且 CRUD 齐全 | `v6.3.0-stage6` | 已完成（实机验证通过） |
| 7 | M7 我们页交付 | 情侣资料卡、六设置模块、隐私两档、提醒设置、应用设置、通知中心、关于（只读） | 2 天 | 2026-11-20 | M6 验证通过 → 六模块可达，字体大小实时生效 | `v6.4.0-stage7` | 进行中（编码完成，待实机验证） |
| 8 | M8 云同步就绪 | CloudBase 云函数与数据库、服务端权限校验、outbox 同步、冲突快照、回收站清理、媒体上传下载 | 5 天 | 2026-11-25 | M7 验证通过 **且** 用户提供 CloudBase 环境 ID 与密钥 → 双设备同步一致，越权被拒 | `v0.8.0-stage8` | 进行中（8A 云底座 + 8B 同步完成，8C 媒体待做；待本轮实机验证） |
| 9 | M9 交付发布 | 备份导出（JSON + 图片 ZIP，不加密）、全量回归、安全与性能、交付文档、运维手册 | 3 天 | 2026-11-28 | M8 验证通过 → 双设备全量回归通过，交付物齐全 | `v1.0.0` | 未开始 |

**关键风险节点**：
- M1 尚未通过（图标与注册/登录点击无反馈问题的第二轮验证结果未回）。
- M8 需要你提供腾讯云开发 CloudBase 环境 ID 与访问密钥，未提供则本阶段不启动（避免伪实现）。这是唯一可能长时间阻塞的节点。

---

## 四、阶段详细任务与实机测试步骤

> 「时间节点」每阶段固定三要素：进入条件 / 计划完成日 / 退出条件。
> 「实机测试步骤」为提交给你执行的操作脚本，每步写明操作与预期结果，便于逐条勾选反馈。

## Task 0: 自举命令行构建工具链（一次性，免管理员）

- **进入条件**：无
- **计划完成日**：2026-11-06（已完成）
- **退出条件**：`scripts/build_apk.ps1` 能在 Windows 命令行产出可安装 APK

- [x] SubTask 0.1: 下载 Microsoft OpenJDK 17 便携包，解压到 `C:\Users\song9\.local\share\android-toolchain\jdk17`，验证 `java -version`
- [x] SubTask 0.2: 下载 Android command-line tools，解压后用 `sdkmanager` 安装 `platforms;android-35`、`build-tools;35.0.0`、`platform-tools`
- [x] SubTask 0.3: 接受 SDK 许可（改为直接写入 `licenses/` 哈希文件，避免交互式 `--licenses`），验证 `adb version`
- [x] SubTask 0.4: 编写 `scripts/build_apk.ps1 -Tag <tag>`：设置 JAVA_HOME/ANDROID_HOME → `gradlew assembleDebug` → 拷贝产物到 `dist/` 并按 tag 命名 → 写 `dist/build-info.txt`
- [x] SubTask 0.5: 冒烟构建并校验 APK 可安装启动（`aapt2 dump badging` 通过）

**实机测试步骤**：本阶段不产出用户可验证的 APP，无需实机测试。

---

## Task 1: 阶段 1 — 工程骨架与视觉基础（`v0.1.0-stage1`）

- **进入条件**：M0 完成
- **计划完成日**：待用户第二轮验证通过（代码已完成）
- **退出条件**：四页可切换且配色符合前端文档 §2，注册/登录/换机恢复三条流程有明确反馈，桌面图标为参考图风格

- [x] SubTask 1.1: Gradle 工程与 `libs.versions.toml`（Compose BOM、Material3、Hilt、Room、DataStore、Navigation Compose、Coil、Retrofit/OkHttp/Kotlinx Serialization、WorkManager），`minSdk 26`
- [x] SubTask 1.2: 设计 token：`Color.kt`（严格取前端文档 §2.1）、`Type.kt`、`Shape.kt`、`Dimens.kt`
- [x] SubTask 1.3: `S520JTheme`（浅色为主、全局字体缩放）+ 430dp 最大宽度居中容器
- [x] SubTask 1.4: 导航骨架：四路由 + 固定底部导航（暖白半透明、安全区、选中鼠尾草绿/未选中灰绿）
- [x] SubTask 1.5: 四个主页面骨架（心动/拾光/默契/我们），仅拾光显示悬浮 FAB
- [x] SubTask 1.6: 通用组件：`SoftCard`、`SectionHeader`、`VisibilityChip`、`EmptyState`、`PrimaryPillButton`、`SoftTextField`、`BackBar`、`DetailRow`
- [x] SubTask 1.7: Room 基座：实体（id/spaceId/ownerId/visibility/createdAt/updatedAt/deletedAt/version）、`S520JDatabase`、DAO、`ContentRepository`（23 条 Mock 种子数据）
- [x] SubTask 1.8: DataStore 偏好（字体大小、问候语、会话）+ Hilt `AppModule`
- [x] SubTask 1.9: 导航壳与入口：`S520JNavHost.kt`（`MainShell`）+ `MainActivity.kt`
- [x] SubTask 1.10: 次级页面：共同空间、隐私、设置、通知中心、关于 S520J、回收站
- [x] SubTask 1.11: 构建 APK 并首轮实机验证（`v0.1.0-stage1`）
- [x] SubTask 1.12: 修复首轮反馈（启动器图标按参考图重做、三张登录页点击无反馈）+ 全量回归 + 重新出包 `v0.1.1-fix1`
- [x] SubTask 1.12b: 阶段自检发现「消息总线在登录态切换瞬间会丢消息」→ 改为 Channel 缓存投递，全量审查 + 回归构建通过，重新出包 `v0.1.2-fix2`（versionCode 102）
- [x] SubTask 1.12c: 补齐 `FieldCodec`（字段编解码）与 `BindState`（七态标签）特征测试基线，`gradlew testDebugUnitTest` 19 条用例全绿
- [x] SubTask 1.13: 第二轮实机验证（等待用户确认 `v0.1.2-fix2` 通过后打 tag）
- [x] SubTask 1.14: 用户反馈三项修改 → 账号 ID 从自动生成改为用户手动设置 → 全量回归构建 `v0.1.3-fix3`（versionCode 103）
- [x] SubTask 1.14b: 用户反馈四项修改 → ① 注册页顺序调整为「昵称 → 账号 ID → 设置密码 → 确认密码」；② AuthScreen Column 加 `.imePadding()` 修复键盘顶不起确认密码方框；③ UI 文案层「恢复码」全量改为「密码」（登录/换机恢复/错误提示/Snackbar），内部实现保留 `RecoveryCodeRule`/`recoveryHash` 命名；④ 删除「关于 S520J」SectionHeader 的 subtitle「只读信息」与卡片右侧 `TagPill("只读")` → 全量回归构建 `v0.1.4-fix4`（versionCode 104）
- [x] SubTask 1.15: 第四轮实机验证（已随 `v0.3.0-stage3` 累计回归通过，2026-11-07 确认）

**实机测试步骤（阶段 1，APK：`dist\S520J-v0.1.4-fix4-debug.apk`）**

> 安装前请先卸载旧版本（已安装 `v0.1.3-fix3` 时直接覆盖安装也可，versionCode 从 103 升到 104）。

1. 卸载旧版本后安装 APK → **预期**：安装成功，桌面图标为手握心形插画。
2. 打开 APP → **预期**：进入认证页，背景暖白 `#F8F6EE`，三个 Tab「注册 / 登录 / 换机恢复」。
3. 注册页顺序检查 → **预期**：自上而下 昵称 → 账号 ID → 设置密码 → 确认密码 → 注册按钮，顺序对用户更自然。
4. 点击「确认密码」方框 → **预期**：键盘弹出时页面上移，确认密码输入框完整可见不被键盘盖住。
5. 注册：昵称「小明」+ 账号 ID `SONGFORHEART` + 密码 `Abc12345` → **预期**：提示「注册成功，账号 ID：SONGFORHEART」，进入主界面。
6. 注册：两次密码不一致 → **预期**：提示「两次输入的密码不一致」。
7. 注册：密码含 `@` → **预期**：提示「密码只能包含字母和数字」。
8. 登录页 → **预期**：标题为「账号 ID + 密码登录」，label 为「密码」（不再出现「恢复码」字样）。
9. 换机恢复页 → **预期**：文案为「在新设备输入账号 ID 与密码」，label 为「密码」。
10. 「我们 → 关于 S520J」入口 → **预期**：SectionHeader 不再显示「只读信息」，卡片右侧不再有「只读」标签。
11. 底部四栏切换 / 仅拾光有 FAB / 拾光模块可独立进入 / 关于页只读无编辑按钮 → 与此前相同，回归确认未退化。

---

## Task 2: 阶段 2 — 账号与密码闭环（`v0.2.0-stage2`）

- **进入条件**：M1 实机验证通过
- **计划完成日**：2026-11-06（已完成编码 + 回归 + 出包）
- **退出条件**：注册/登录/换机恢复三条流程可用；密码修改 UI 可达；离线时注册/登录提示需联网；单元测试全绿

- [x] SubTask 2.1: 注册：昵称 → 用户手动设置账号 ID（8–20 位字母数字、uppercase 存储、唯一性校验）→ 自设密码（8–32 位字母数字、区分大小写），成功后直接进入登录态并明确展示账号 ID
- [x] SubTask 2.2: 登录「账号 ID + 密码」；密码修改需验证旧密码（SettingsScreen → 账号与安全 → 修改密码 Dialog，三个 PasswordTextField：旧密码 / 新密码 / 确认新密码，本地校验 + Repository 校验双重拦截）
- [x] SubTask 2.3: 密码加盐哈希（PBKDF2WithHmacSHA256，10000 轮，256 bit），数据库中永不出现明文；APP 内无查看明文入口
- [x] SubTask 2.4: 换机恢复：输入账号 ID + 密码 → 调用同一 login 流程，恢复会话 + 种子内容
- [x] SubTask 2.5: 会话抽象（SessionProfile / DataStore 持久化 / loggedIn 状态机驱动 MainActivity 分流）
- [x] SubTask 2.6: 离线态：新建 NetworkStateTracker（ConnectivityManager + Hilt 单例）暴露 `isConnected: Flow<Boolean>`；AppViewModel.register/login 前检查，离线时发 Snackbar「网络不可用，请检查后重试」；本地内容 CRUD 不联网
- [x] SubTask 2.7: 单元测试：ChangePasswordTest 8 条 + RecoveryCodeRuleTest 9 条 + FieldCodecTest 10 条 + CoupleStartDateTest 若干 → 合计 28 条，0 失败；新增 mockk / room-testing 依赖
- [x] SubTask 2.8: 阶段门禁：全量审查 + 回归 + 构建 `v0.2.0-stage2`（versionCode 200）+ 提交实机验证请求
- [x] SubTask 2.9: 实机验证通过后打 tag（已随 `v0.3.0-stage3` 累计回归通过）
- [x] SubTask 2.10（反馈修复，已随 v0.3.0 实机确认）：`v0.2.1-fix-settings` 设置页修复 → `v0.2.2-fix-nav` 导航修复 → `v0.2.2-fix-settings` 再次修复 → `v0.2.3-independent-settings` 设置页拆分为独立页面（账号安全/隐私/数据管理/提醒/偏好/关于，`SettingsScreen.kt` 已删除）。

**实机测试步骤（阶段 2，APK：`dist\S520J-v0.2.0-stage2-debug.apk`）**

> versionCode 从 104 升到 200，必须**卸载旧版后重新安装**（数据库 schema 无变化，但 Room 实例会从旧版本继承，建议干净安装排除旧数据干扰）。

1. 全新安装 → 注册页：昵称「小明」+ 账号 ID `SONGFORHEART` + 密码 `Abc12345` → **预期**：提示「注册成功，账号 ID：SONGFORHEART」，直接进入主界面。
2. 退出登录 → 登录页账号 ID 填 `SONGFORHEART`，密码 `Abc12345` → **预期**：登录成功回到主界面。
3. 登录页密码填 `abc12345`（大小写不同）→ **预期**：提示密码不正确，不登录。
4. 登录页账号 ID 填不存在的 `00000000` → **预期**：提示账号不存在。
5. 退出登录 → 注册页填相同账号 ID `SONGFORHEART` → **预期**：提示「该账号 ID 已被占用，请换一个」。
6. 进入「我们 → 设置 → 账号与安全」→ **预期**：显示当前账号 ID（只读），有「修改密码」入口；未登录时该入口禁用。
7. 点「修改密码」→ 弹出 Dialog；旧密码填对、新密码填 `Abc12345`（和旧密码相同）→ **预期**：提示「新旧密码不能相同」，不保存。
8. 旧密码填错、新密码填 `Xyz98765` → **预期**：提示「旧密码不正确」。
9. 旧密码填对、新密码含 `@` → **预期**：提示「密码只能包含字母和数字」。
10. 旧密码填对、新密码两次一致 → **预期**：提示「密码已更新」。退出后用旧密码登录失败，用新密码 `Xyz98765` 登录成功。
11. 遍历全部页面 → **预期**：找不到任何查看明文密码的入口（包括 Settings / About / 调试菜单）。
12. 打开手机飞行模式 → 进入注册页 → 点注册 → **预期**：提示「网络不可用，请检查后重试」。
13. 飞行模式下在主界面新增一条内容 → **预期**：可正常保存到本地（内容 CRUD 不联网检查）。
14. 飞行模式下退出登录 → 登录页点登录 → **预期**：提示「网络不可用」。

---

## Task 3: 阶段 3 — 共同空间与绑定（`v0.3.0-stage3`）

- **进入条件**：M2 实机验证通过
- **计划完成日**：2026-11-10
- **退出条件**：七态可复现，双方各确认一次才绑定，解绑后只读可导出，重新绑定恢复历史内容

- [x] SubTask 3.1: 创建共同空间（名称、简介）+ 绑定码生成（24 小时有效期 + 刷新）
- [x] SubTask 3.2: 输入绑定码 → 查看空间信息 → 双方各确认一次（Mock 阶段以本机模拟对方成员完成第二次确认）
- [x] SubTask 3.3: 七态状态机：未绑定/已生成邀请/等待对方加入/绑定申请中/已绑定/绑定失败/冻结归档（绑定申请与解绑申请分离，`pendingAction` 区分）
- [x] SubTask 3.4: 解绑发起与确认 → 冻结归档（只读门禁 + JSON 导出）；重新绑定解冻且历史内容保留
- [x] SubTask 3.5: 状态机与权限单元测试：SpaceStateMachineTest 21 条（含 24h 边界、空间已满、双确认、只读/可导出）+ ExportBuilderTest 8 条
- [x] SubTask 3.6: 阶段门禁：全量审查 + 回归 + 构建 `v0.3.0-stage3`（versionCode 300）+ 实机验证通过（2026-11-07 已打 tag `v0.3.0-stage3`）

**阶段 3 实现说明（2026-11-06 审计后落地）**

- 交付物：`dist/S520J-v0.3.0-stage3-debug.apk`；全量单测 58 条 0 失败；Room v1→v2 增量迁移（保留旧测试数据）。
- 单机 Mock 边界：本机只有一份本地库，无法真正双端同时在线；因此“对方输入绑定码 / 对方确认”以页面内明确标注的「本地演示」入口模拟，跨设备真实加入留到阶段 8 云端接入。
- 只读门禁：冻结归档后 `AppViewModel` 的内容写操作统一拦截，模块列表/编辑、拾光 FAB、回收站恢复与彻底删除同步隐藏；主导航顶部展示只读提示条。
- 导出：数据管理页与冻结卡片均可通过系统文件选择器导出 JSON（含回收站内容）；图片 ZIP 留阶段 9。
- 本机构建环境：工作区路径含中文，AGP 需 `android.overridePathCheck=true`（已写入 gradle.properties）；`assembleDebug` 可直接在原路径执行，但 Gradle Test Worker 在中文路径下会 `ClassNotFoundException`（Gradle/Windows 已知问题），执行单测请经 ASCII 目录联接 `C:\s520j-build`（指向本工程）后再运行 `gradlew :app:testDebugUnitTest`。

**实机测试步骤（阶段 3，APK：`dist\S520J-v0.3.0-stage3-debug.apk`）**

> versionCode 300。可直接覆盖安装 v0.2.x（Room 1→2 已提供迁移）；若历史数据异常可卸载重装。

1. 进入「我们 → 共同空间」，填名称「我们的家」与简介后保存 → **预期**：状态从「未绑定」变为「已生成邀请」，显示 6 位绑定码与到期时间。
2. 点「已分享给对方，开始等待」→ **预期**：状态变为「等待对方加入」。
3. 在「本地演示：模拟对方输入绑定码」输入上方绑定码并提交 → **预期**：状态变为「绑定申请中」，出现「我方确认」与「模拟对方确认」两个按钮。
4. 先点「我方确认」→ **预期**：我方显示已确认、对方仍待确认，状态保持「绑定申请中」；再点「模拟对方确认」→ **预期**：状态变为「已绑定」，空间信息可见。
5. 在绑定码输入框输入错误码（如 `AAAAAA`）→ **预期**：出现「绑定失败：绑定码不存在，请核对后重试」卡片，可关闭/重试。
6. 输入已过期绑定码 → **预期**：提示「绑定码已过期，请让对方重新生成」，空间进入「绑定失败」态。
7. 已绑定后点「发起解绑」→「我方确认解绑」→「模拟对方确认」→ **预期**：状态变为「冻结归档」，顶部出现只读提示条；模块列表无新增/删除按钮，拾光无 FAB，回收站无恢复/彻底删除，保存内容时提示「空间已冻结归档，当前为只读状态」。
8. 冻结状态下进入「我们 → 数据管理 → 导出 JSON」（或共同空间卡片「导出归档内容」）→ **预期**：系统文件选择器可保存，JSON 含空间、会话与全部记录（含回收站）。
9. 点「重新绑定」→ **预期**：状态回到「已绑定」，此前共同内容仍在，只读提示条消失、新增/编辑恢复可用。
10. 回归：底部四栏切换、登录/退出、字体缩放等既有功能未退化。

---

## Task 4: 阶段 4 — 心动页（`v0.4.0-stage4`）

- **进入条件**：M3 实机验证通过
- **计划完成日**：2026-11-13
- **退出条件**：页面顺序正确，日历可完整选日期并增删事项，DIY 可增删，五模块视觉贴合参考图，快速记录照片/文字跨页实时同步，主子页切换无位移，全程无惩罚性文案

- [x] SubTask 4.1: 顶部问候渐变卡（28dp 圆角、鼠尾草绿到暖沙色；日期星期、时段问候 + 双方昵称、相伴天数、寄语；点击可编辑）
- [x] SubTask 4.2: 今日心动挑战白卡（24dp 圆角与柔和阴影、双色标签、17sp 文案、整宽 46dp 绿色胶囊「标记完成」、右上「换一换」、双方分别确认，按天持久化，无惩罚与负面警告）
- [x] SubTask 4.3: 快速记录五圆形入口（浅绿底 + 深绿图标，标签为 照片/做饭/探店/文字/浪漫时刻），按类型决定是否显示照片上传
- [x] SubTask 4.4: 心愿横滑白卡（书签、衬线标题、提出/答应副标题、桃色日期标签、状态标签、整宽「标记实现」；待回应/已答应/已实现三态切换与筛选，右上「全部」入口，增删改查含状态编辑）
- [x] SubTask 4.5: 碰碰你四小卡（18dp 圆角、细边框、深棕线稿图标；拍一拍/抱抱/亲亲/想你 + DIY 增删持久化 + 发送反馈 + 未读通知）
- [x] SubTask 4.6: 共同日历（完整月份、星期标题、今日高亮、事项标记、前后月、返回今天）+ 事项 CRUD + 选日预填
- [x] SubTask 4.7: 最近的美好纵向卡片 → 详情/编辑
- [x] SubTask 4.7a: 心动页视觉精修（对照参考图 1–5）：问候渐变卡、挑战白卡、心愿横滑白卡（保留待回应/已答应/已实现三态切换与筛选）、碰碰你四小卡、快速记录五圆形入口
- [x] SubTask 4.7b: 跨页数据同步：快速记录 `mediaIds` 持久化 + 持久 URI 读取权限；拾光照片墙合并 PHOTO + QUICK_RECORD 并真实渲染；默契页新增「来自快速记录」文字区块
- [x] SubTask 4.7c: 页面切换位移修复：底栏改为固定高度占位，主页面与子页面内容区高度恒定
- [x] SubTask 4.7d: 多端适配核对 320dp–1920dp：430dp 居中、圆形入口自动收缩、心愿卡按容器比例计算、小屏自动降字号
- [x] SubTask 4.8: 阶段门禁：全量审查 + 回归 + 构建 `v0.4.0-stage4` + 提交实机验证请求 → 用户 2026-11-07 实机验证通过，已 Git 提交并打 tag `v0.4.0-stage4`

**实机测试步骤（阶段 4）**

1. 打开「心动」页 → **预期**：自上而下依次为 渐变问候卡 → 今日挑战 → 快速记录 → 心愿 → 碰碰你 → 共同日历 → 最近的美好。
2. 编辑问候语并保存 → **预期**：文案更新，相伴天数计算正确。
3. 今日挑战点「换一换」→ **预期**：挑战内容变化；点「标记完成」→ **预期**：按钮提示已标记，可再点「替 TA 标记」同步对方状态，全程无扣分/警告文案。
4. 心愿卡片左右滑动或点击三个状态标签 → **预期**：切换到 待回应/已答应/已实现 并正确筛选；点卡片进入编辑；点「标记实现」→ 状态变为已实现。
5. 碰碰你点「抱抱」→ **预期**：有发送成功反馈并生成未读通知；点「＋ 自定义」新增动作 → 出现在自定义小卡，点右上「×」可删除。
6. 依次点五个快速记录入口 → **预期**：均能进入对应记录页；「照片/做饭/探店/浪漫」显示照片上传，纯文字类型不显示。
7. 从快速记录上传照片并保存 → 打开「拾光」页 → **预期**：照片出现在照片墙，且按分类正确筛选、可点开对应记录。
8. 从快速记录只提交文字 → 打开「默契」页 → **预期**：「来自快速记录」区块实时显示该条标题与内容，并可点开。
9. 从四个主页面进入任一子模块 → **预期**：主页面内容不发生整体位移、无闪烁抖动，返回后滚动位置保持。
10. 共同日历点某一天 → **预期**：下方列表切换为该日事项；点前后月箭头 → 正确切换月份；点「返回今天」→ 回到当月当天。
11. 在某日新增一条事项后 → **预期**：该日期出现事项标记，点击可见。
12. 最近的美好点任一卡片 → 进入详情，可编辑并保存。

---

## Task 5: 阶段 5 — 拾光页（`v6.2.0-stage5`）

- **进入条件**：M4 实机验证通过
- **计划完成日**：2026-11-15
- **退出条件**：四模块可独立增删改查，双列照片墙可用，私密锁标记正确，五星榜不含人格评价

- [x] SubTask 5.1: 顶部文案「拾起生活里的微光。」+ 副标题 + 仅本页显示的日期卡片
- [x] SubTask 5.2: 四张统计卡（共同照片仅统计照片墙数量、不做跳转；做过的菜/去过的店/去过的城市进入独立管理页，可增删改查）
- [x] SubTask 5.3: 照片墙：双列、大圆角、分类标签、柔和背景、私密锁标记；数据源合并「照片」模块与快速记录带图记录（按时间倒序），分类筛选覆盖快速记录类型
- [x] SubTask 5.4: 分类筛选（全部/做饭/探店/旅行/日常/浪漫时刻）+ 搜索
- [x] SubTask 5.5: 五星榜（松记、锦记），仅展示 5 星自做饭菜，探店不计入，禁止评价人格与关系
- [x] SubTask 5.6: 悬浮 FAB 新增入口，按内容类型决定是否显示照片上传
- [x] SubTask 5.7: 阶段门禁：全量审查 + 回归 + 构建 `v6.2.0-stage5` + 实机验证通过，已提交并打 tag

**实机测试步骤（阶段 5）**

1. 打开「拾光」页 → **预期**：顶部显示「拾起生活里的微光。」与副标题，日期卡片只在此页出现。
2. 点「共同照片」统计卡 → **预期**：不跳转，仅统计照片墙数量；依次点「一起做过的菜 / 一起去过的店 / 一起去过的城市」→ **预期**：均进入独立管理页，每页可新增/编辑/删除。
3. 照片墙观察 → **预期**：双列排布、大圆角；隐私记录显示锁标记。
4. 点分类标签（全部/做饭/探店/旅行/日常/浪漫时刻）→ **预期**：列表按分类过滤正确。
5. 在搜索框输入关键词 → **预期**：列表按标题/内容过滤。
6. 查看「松记五星榜 / 锦记五星榜」→ **预期**：两榜各自只显示对应榜单的 5 星自做饭菜，4 星与探店不出现，页面无对外貌或性格的评价项。
7. 点右下角 FAB → **预期**：新增入口打开；选择「共同照片/做饭/探店/旅行」等带照片类型时可上传照片，选择「文字笔记」时不出现上传控件。

---

## Task 6: 阶段 6 — 默契页（`v6.3.0-stage6`）

- **进入条件**：M5 实机验证通过
- **计划完成日**：2026-11-18
- **退出条件**：八模块独立可进入且 CRUD 齐全，相处之道需双方确认，「共同认可」标记正确

- [x] SubTask 6.1: 八圆形图标入口（横滑、统一尺寸、选中鼠尾草绿）+ 随选中切换的预览区；浪漫时刻置末位，无「来自快速记录」区块（按 Taxonomy v2）
- [x] SubTask 6.2: 八个独立管理页：重要日子、彼此的承诺、为彼此做过、浪漫时刻、相处之道、TA 的喜欢、TA 不喜欢、我们的笔记
- [x] SubTask 6.3: 统一能力：列表、新增、搜索、详情、编辑、删除、可见范围、空状态、独立滚动
- [x] SubTask 6.4: 相处之道双方确认机制（双方确认后才标记「共同认可」）
- [x] SubTask 6.5: 重要日子年度重复 + 提醒 + 照片 + 故事；承诺状态与兑现时间
- [x] SubTask 6.6: 阶段门禁：全量审查 + 回归 + 构建 `v6.3.0-stage6` + 实机验证通过，已提交并打 tag

**实机测试步骤（阶段 6）**

1. 打开「默契」页 → **预期**：八个圆形图标可左右横滑，尺寸统一，选中项为鼠尾草绿。
2. 依次点八个图标 → **预期**：每个都进入独立管理页，均有列表/新增/搜索/空状态。
3. 在任一模块新增一条记录并保存 → **预期**：列表出现该记录，可点进详情并编辑、删除（删除有二次确认）。
4. 在「相处之道」新增约定 → **预期**：初始状态为「待对方确认」，不显示「共同认可」。
5. 对方确认后 → **预期**：标记为「共同认可」。
6. 新增一条「重要日子」并设置年度重复 → **预期**：列表显示下次日期，可添加照片与故事。
7. 在「彼此的承诺」新增承诺并修改状态为「已兑现」→ **预期**：显示兑现时间。

---

## Task 7: 阶段 7 — 我们页（`v6.4.0-stage7`）

- **进入条件**：M6 实机验证通过
- **计划完成日**：2026-11-20
- **退出条件**：六设置模块入口可达，字体大小实时生效，关于页只读

- [x] SubTask 7.1: 情侣资料卡片（双方头像可上传并持久化、昵称居头像正下方、爱心连接、已绑定显示共同空间名称 / 未绑定显示「未绑定共同空间」）
- [x] SubTask 7.2: 六设置模块入口（共同空间、隐私与权限、数据管理、提醒设置、应用设置、关于 S520J）
- [x] SubTask 7.3: 隐私与权限：仅自己可见 / 共同可见，默认仅自己（已持久化并作用于新建内容）
- [x] SubTask 7.4: 提醒设置：仅本机一份时间与开关，无免打扰（已持久化）
- [x] SubTask 7.5: 应用设置：主题、字体大小、语言（V1 仅简体中文与浅色）、个性化偏好
- [x] SubTask 7.6: 通知中心（已读/未读、全部已读、进入 APP 时计算待提醒项）
- [x] SubTask 7.7: 关于 S520J 只读页（版本、更新日期、作者 LKS、帮助与反馈，无编辑入口）
- [x] SubTask 7.8: 阶段门禁：全量审查 + 回归 + 构建 `v6.4.0-stage7` + 实机验证通过，已提交并打 tag

**实机测试步骤（阶段 7）**

1. 打开「我们」页 → **预期**：副标题为「愿每一个平凡的日子，都有彼此相伴的温柔。」；情侣卡中间有爱心，点击任一头像可从相册上传并持久保存；两个昵称分别在各自头像正下方；底部已绑定时显示「共同空间 · 空间名称」，未绑定时显示「未绑定共同空间」。
2. 依次点八个条目 → **预期**：全部可达且可返回；右侧附加信息与标题同一行、右对齐紧贴箭头；「关于 S520J」版本号显示为胶囊徽标。
3. 在「应用设置」把字体调大三档 → **预期**：全 APP 文字实时变大且无布局溢出（重点看心愿卡与日历）。
4. 在「隐私与权限」确认默认可见范围为「仅自己可见」，再到任一模块新建一条记录 → **预期**：新记录默认即「仅自己可见」，可在编辑页切换为「共同可见」，且返回后设置仍保留。
5. 在「提醒设置」设置本机提醒时间与开关 → **预期**：只有一份本机设置，退出重进后保留，无「免打扰」选项、无双方独立设置。
6. 进入通知中心 → **预期**：可区分已读/未读，点「全部已读」后未读计数归零。
7. 进入「关于 S520J」→ **预期**：显示版本号、更新日期、作者 LKS、帮助与反馈，无任何编辑/删除入口。

---

## Task 8: 阶段 8 — 云同步与媒体（`v0.8.0-stage8`）

> **前置条件（已就绪）**：腾讯云开发 CloudBase 环境已开通（个人版 PG 模式）。环境 ID 与 API Key 只存本机 `local.properties`（已 gitignore），不写入仓库与 APK。
>
> **口径变更（2026-11-08）**：实际环境为 **PostgreSQL（PG 模式）**，原「集合」模型改为「关系表 + 表级 GRANT + 行级 RLS」。数据访问通过 PostgREST / `exec-pgsql` 管理接口，云函数内二次校验 space_id / owner_id / visibility。

- **进入条件**：M7 实机验证通过 且 CloudBase 环境与密钥已就绪
- **计划完成日**：2026-11-25
- **退出条件**：双设备同步一致，越权访问被服务端拒绝，弱网可重试，照片按需下载

- [x] SubTask 8A.1: CloudBase 环境接入与云函数骨架（auth / space / content / sync / media / recycle / notification / settings / backup）
- [x] SubTask 8A.2: PG 模式建表与索引（users、auth_sessions、spaces、space_members、bind_codes、bind_requests、各内容表、media_assets、recycle_bin、conflict_snapshots、sync_logs、backup_jobs）
- [x] SubTask 8A.3: 服务端权限校验（space_id / owner_id / visibility）：表级 GRANT + 行级 RLS 策略 + 云函数二次校验
- [x] SubTask 8B.1: 客户端 outbox + `sync_cursor` + 增量拉取（updated_at / version）
- [x] SubTask 8B.2: 冲突处理：字段合并 → 冲突快照（30 天）→ 用户选择版本；离线删除优先
- [x] SubTask 8B.3: 回收站 30 天自动清理（WorkManager）、恢复、双方确认彻底删除；冻结归档豁免
- [ ] SubTask 8C.1: 照片压缩、分片上传、断点续传、预签名 URL 下载原图
- [x] SubTask 8C.2: 弱网与同步状态可视化（同步中 / 失败重试）
- [ ] SubTask 8.4: 阶段门禁：双设备全量审查 + 回归 + 构建 `v0.8.0-stage8` + 提交实机验证请求（通过后打 tag）

**8A/8B 实现说明（2026-11-08）**

- 云底座：CloudBase 个人版 PG 模式，环境 `lin-run-forward-d3fjemwrbf5f4738`（上海）；14 张表 + 索引 + 表级 GRANT + RLS 全部上线，`anon`/`authenticated` 无表权限，仅 `service_role`。
- 云函数：9 个 Nodejs20.19 Event 函数（`auth/space/content/sync/media/recycle/notification/settings/backup`），通过 `exec-pgsql` 管理接口访问 PG，环境变量只下发到云端。
- HTTP 访问服务：默认域名 + 9 条 `/api/*` 路由，客户端匿名直调，函数内自有 token 鉴权。
- 端到端验证：登录 → auth.me → space.create → content.create → sync.pull → content.update → 版本冲突 → 删除 → 回收站 → 恢复 → settings.put/get 全部通过。
- 客户端：`CloudApi` / `CloudAuthRepository` / `SyncRepository` / `SyncWorker`；Room v3 新增 `sync_conflicts`，v4 补充本机快照列；设置页新增「云端同步」区块与「冲突处理」页。
- 全量单测：21 个测试类 / 142 条用例，0 失败（含新增 CloudPayload 5、SyncApply 5、RecyclePolicy 4、ConflictResolver 5）。
- 未完成：8C.1 媒体上传仍返回 501，因此本轮实机验证只覆盖同步与回收站（下方 1–7 条），照片相关 8–9 条留待 8C 完成后验证。

**实机测试步骤（阶段 8 本轮，APK：`dist\S520J-v0.8.0-stage8-debug.apk`，versionCode 800）**

> 需两台设备（A/B）各自注册账号，并用绑定码完成「共同空间」绑定后再测；
> Room v3→v4 已提供迁移，可直接覆盖安装，若历史数据异常可卸载重装。

1. A 设备新增一条「共同可见」记录 → B 设备打开 APP（或下拉/在「我们-应用设置-云端同步」点「立即同步」）→ **预期**：B 能看到该记录。
2. A 设备新增一条「仅自己可见」记录 → B 设备同步 → **预期**：B 完全看不到。
3. A 设备断网后新增 2 条、编辑 1 条、删除 1 条 → 恢复网络 → **预期**：自动同步完成，B 端结果与 A 一致。
4. 同步过程中断开网络 → **预期**：「云端同步」显示「同步失败」或「未连接云端」，点「立即同步」重试后成功。
5. 双方同时编辑同一条记录的同一字段 → **预期**：设置页显示「待处理冲突 N 条」→ 进入「冲突处理」可看到本机/云端两版，选择「保留本机」后自动重新上传，B 端最终与本机一致。
6. 删除一条共同内容 → 回收站可见 → 恢复 → **预期**：回到原模块。
7. 一方发起彻底删除 → **预期**：需另一方确认后才真正消失（当前版本以本地 Mock 确认流程呈现）。

---

## Task 8.5: 阶段 8 实机问题修复（创建闪退 + 键盘遮挡，`v0.8.1-stage8-hotfix1`）

- **进入条件**：SubTask 8.4 实机反馈存在阻塞问题（2026-11-08 两台设备复测）
- **退出条件**：创建共同空间不再闪退、绑定码输入框不被键盘遮挡、双机绑定成功；实机通过后提交并打 tag

**用户实机反馈（2026-11-08）**

1. 进入「共同空间」页点击「创建」后，APP 立即闪退。
2. 输入对方绑定码时键盘弹出，页面不随之上移，输入框仍被键盘遮挡（只能看到「输入对方绑定码」的一半字样）。

**根因分析（静态定位，无 adb 设备可抓取 logcat）**

- 闪退：`AppViewModel.createSpace` 在 `viewModelScope.launch` 内直接调用 `CloudSpaceRepository.create`，该链路包含 3 次云端调用 + Room 镜像写入 + DataStore 写空间 id，**缺少顶层异常兜底**；任一环节抛出异常即逃逸协程 → 进程崩溃。同类隐患：`refreshSpaceFromCloud` / `refreshBindCode` / `submitBindCode` / `confirmBindAsOwner` / `CloudSpaceRepository.refreshLocal`。另外 `resp.data?.jsonObject` 在非对象类型时会抛 `IllegalArgumentException`，且位于 `try` 之外。
- 键盘遮挡：`MainActivity` 启用 `enableEdgeToEdge()`，`Scaffold` 使用 `contentWindowInsets = WindowInsets(0,0,0,0)`，而 `PageContainer` 仅 `verticalScroll`、**未加 IME 内边距**。键盘弹出时滚动视口高度不变且延伸至键盘下方，`bringIntoView` 判定「已在视口内」因此无效。

**修复方案（决定项）**

- [x] SubTask 8.5.1：`app/src/main/java/com/s520j/app/ui/components/Kit.kt` → `PageContainer` 的 `Box` 追加 `Modifier.imePadding()`；全局生效，覆盖共同空间、编辑、设置等所有带输入框页面，滚动视口随键盘缩短。
- [x] SubTask 8.5.2：`app/src/main/java/com/s520j/app/ui/screens/us/SpaceScreen.kt` → 保留 `BringIntoViewRequester`，聚焦后 200ms 与 600ms 各触发一次 `bringIntoView()`，兼容键盘弹出动画时长；`BindCodeInputCard` 输入框补 `Modifier.fillMaxWidth()`。
- [x] SubTask 8.5.3：`app/src/main/java/com/s520j/app/data/cloud/CloudSpaceRepository.kt` → 新增私有守卫（`guarded` + `asObject`），异常统一转 `CloudSpaceResult.Failure`（`IOException` 视为离线）；`create / refreshCode / join / confirmBind / refreshLocal` 全部兜底，非法 `data` 负载不再抛异常。
- [x] SubTask 8.5.4：`app/src/main/java/com/s520j/app/ui/AppViewModel.kt` → `createSpace / refreshSpaceFromCloud / refreshBindCode / submitBindCode / confirmBindAsOwner` 增加 `try { } catch (t: Throwable)` 兜底（`CancellationException` 重新抛出）；失败写入 `_bindFailure` 并提示，文案形如「创建失败：<异常信息>」，便于截图定位真实原因。
- [x] SubTask 8.5.5：失败可见性沿用既有 `BindFailureCard`（`current == null` 时展示 `bindFailure`），不新增 UI 组件。

**TDD（先失败后通过）**

- [x] SubTask 8.5.T1：新增 `app/src/test/java/com/s520j/app/data/cloud/CloudSpaceRepositoryTest.kt`（mockk，纯 JVM）5 条：创建成功镜像、本地库写入异常不抛出、非法 `data` 负载不抛出、刷新失败不抛出、本地刷新异常不抛出（前 3 条修复前为 RED）。
- [x] SubTask 8.5.T2：全量单测 23 个测试类 / 153 条 / 0 失败（从 `C:\Users\LINKUNSONG\s520j-link` 执行 `gradlew :app:testDebugUnitTest`）。

**验收（实机）**

- [x] SubTask 8.5.6：已构建 `dist\S520J-v0.8.1-stage8-hotfix1-debug.apk`（versionCode 801 / versionName `v0.8.1-stage8-hotfix1`，aapt2 badging 校验通过，2026-11-08 23:26）并提交实机步骤（见下方）。
  1. 两台手机覆盖安装；A 进入「共同空间」点「创建」→ **预期**不再闪退并出现绑定码卡片；若仍失败，页面显示「创建失败：…」原因文案（截图回传）。
  2. B 点「输入对方绑定码」并输入 → **预期**键盘弹出时页面上移、输入框完整可见；提交后提示「已提交绑定申请」。
  3. A/B 各点一次「我方确认」→ **预期**绑定成功，双方重进页面显示已绑定、2 名成员。
- [ ] SubTask 8.5.7：实机通过后 Git commit + tag `v0.8.1-stage8-hotfix1`。

---

## Task 8.6: 阶段 8 实机问题修复第二轮（创建失败 + 键盘遮挡未生效，`v0.8.2-stage8-hotfix2`）

- **进入条件**：8.5 出包后实机复测仍失败（2026-11-09）
  1. 创建共同空间失败，页面显示 `Element class kotlinx.serialization.json.JsonNull is not a JsonObject`；
  2. 绑定码输入框仍被键盘挡住，只能看到一半的「输入对方绑定码」。

**失败根因**

- `CloudAuthRepository.fetchSpace()` / `createSpace()` 使用 `JsonElement?.jsonObject` 扩展强转；`space.get` 在无空间时返回 `{"space": null}`，此时元素是 `JsonNull`（非 Kotlin null），`jsonObject` 直接抛 `IllegalArgumentException`，经 `guarded` 转成 `Failure` 展示到 UI。
- 键盘避让只依赖 `imePadding()` + `bringIntoView()`；绑定码输入卡位于页面最底部，滚动视口收窄后仍不足以完全露出输入框。

**修复方案**

- [x] SubTask 8.6.1：`app/src/main/java/com/s520j/app/data/cloud/CloudAuthRepository.kt` → 移除 `jsonObject` 强转，改为 `private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject`，`authenticate / ensureAccessToken / fetchSpace / createSpace` 全部改用安全解析。
- [x] SubTask 8.6.2：`app/src/main/java/com/s520j/app/ui/components/Kit.kt` → `PageContainer` 新增 `scrollState: ScrollState = rememberScrollState()` 参数，交由页面控制滚动状态。
- [x] SubTask 8.6.3：`app/src/main/java/com/s520j/app/ui/screens/us/SpaceScreen.kt` → 页面级 `pageScroll` 外提并传入 `PageContainer`；`BindCodeInputCard` 新增 `onInputFocused` 回调，聚焦后 350ms 执行 `animateScrollTo(maxValue)`（输入卡恒为页面最后一块内容，滚到底即完整可见），同时保留两次 `bringIntoView()` 兜底。
- [x] SubTask 8.6.4：`BindFailureCard` 文案由「绑定失败」改为「操作失败」，避免创建失败时误导。

**TDD（先失败后通过）**

- [x] SubTask 8.6.T1：新增 `app/src/test/java/com/s520j/app/data/cloud/CloudAuthRepositoryTest.kt` 2 条：`space.get` 返回 `{"space":null}` 时自动走 `space.create` 并返回新空间 ID；`space` 字段为 null 时不抛异常。修复前 RED（异常文本与实机截图逐字一致），修复后 GREEN。
- [x] SubTask 8.6.T2：全量单测 24 个测试类 / 155 条 / 0 失败（从 `C:\Users\LINKUNSONG\s520j-link` 执行 `gradlew :app:testDebugUnitTest`）。

**验收（实机）**

- [x] SubTask 8.6.5：已构建 `dist\S520J-v0.8.2-stage8-hotfix2-debug.apk`（versionCode 802 / versionName `v0.8.2-stage8-hotfix2`，2026-11-09）并提交实机步骤（见下方）。
  1. 两台手机覆盖安装；A 进入「共同空间」→ 填空间名称 → 点「创建共同空间」→ **预期**不再出现 JsonNull 报错，出现绑定码卡片。
  2. B 点「输入对方绑定码」输入框 → **预期**键盘弹出后页面自动滚到底，输入框与按钮完整可见。
  3. B 输入 A 的绑定码提交 → A/B 各点一次「我方确认」→ **预期**绑定成功，双方重进页面显示已绑定。
- [ ] SubTask 8.6.6：实机通过后 Git commit + tag `v0.8.2-stage8-hotfix2`（连同 8.5.7 一并提交）。

---

## Task 8.7: 共同空间创建逻辑重构（取消自动建空间，`v0.8.3-stage8-hotfix3`）

- **进入条件**：实机反馈（2026-11-09）登录后进入「共同空间」已被自动创建「昵称+的小屋」，用户无法自行创建并获得绑定码。

**失败根因**

- `CloudAuthRepository.ensureSpace()` 在 `space.get` 返回 `{"space": null}` 时会自动调用 `space.create`，名称固定为「昵称的小屋」。
- `SpaceScreen` 仅在 `current == null` 时展示创建表单，自动创建后 `current != null` 且 `bindState == UNBOUND`，创建入口被永久隐藏，也没有绑定码卡片。

**修复方案（交互模型）**

- 未绑定态（无空间 / UNBOUND）固定展示：状态提示「共同空间未绑定」+ ①创建共同空间（创建即生成 24 小时绑定码）+ ②输入对方绑定码。
- 已生成绑定码 / 等待对方 / 待确认 / 已绑定 / 已冻结沿用原有分支。

**实现**

- [x] SubTask 8.7.1：`CloudAuthRepository.ensureSpace()` → `resolveSpaceId()`，只查询不创建；删除 `createSpace()`。
- [x] SubTask 8.7.2：`CloudSpaceRepository.doCreate()` → 无空间时调用 `space.create`（使用用户填写的名称与简介），已有单人空间时调用 `space.update` 改名，随后生成绑定码；新增 `writeSpace()` 统一解析。
- [x] SubTask 8.7.3：`CloudSpaceRepository.refreshLocal()` → 云端返回 `{"space": null}` 时清空本地镜像与 `cloudSpaceId`，页面回到未绑定态。
- [x] SubTask 8.7.4：`SyncRepository.syncNow()` 改用 `resolveSpaceId()`，同步不再顺手创建空间。
- [x] SubTask 8.7.5：`SpaceStateMachine.canCreate()` 允许 `null / UNBOUND / FAILED`。
- [x] SubTask 8.7.6：`SpaceScreen` 未绑定分支重排：未绑定提示卡 → 创建表单（按钮「创建并生成绑定码」）→「或使用对方的绑定码」；`BindFailureCard` 简化为「操作失败 + 知道了」。

**TDD**

- [x] SubTask 8.7.T1：`CloudAuthRepositoryTest` 改为「云端没有空间时不自动创建（不调用 `space.create`）」+「已有空间返回 id」。
- [x] SubTask 8.7.T2：`CloudSpaceRepositoryTest` 新增「无空间时调用 `space.create` 且名称为用户输入」「已有单人空间时改名不重复创建」「云端无空间时刷新清空本地镜像」。
- [x] SubTask 8.7.T3：`SpaceStateMachineTest` 创建条件改为 UNBOUND/FAILED/null 允许。
- [x] SubTask 8.7.T4：全量单测 24 个测试类 / 157 条 / 0 失败。

**验收（实机）**

- [x] SubTask 8.7.7：已构建 `dist\S520J-v0.8.3-stage8-hotfix3-debug.apk`（versionCode 803，2026-11-09）。
  1. 覆盖安装后进入「共同空间」→ **预期**显示「共同空间未绑定」+ 创建表单 + 绑定码输入（不再自动出现「昵称的小屋」）。
  2. A 填名称 →「创建并生成绑定码」→ **预期**出现「我的绑定码」卡片（24 小时有效）。
  3. B 输入 A 的绑定码 → A/B 各确认一次 → **预期**绑定成功。
  4. 历史遗留的单人空间：直接创建会改名并生成新码；直接加入对方空间会自动归档旧空间。
- [ ] SubTask 8.7.8：实机通过后 Git commit + tag（`v0.8.2-stage8-hotfix2` + `v0.8.3-stage8-hotfix3` 一并提交）。

---

## Task 8.8: 阶段 9 批次一 — 空间资料 + 解绑闭环 + 双向同步（`v0.9.0-stage9-space-sync`）

- **进入条件**：8.7 双机绑定已可用；用户提出 8 项改进需求，经 spec 评审拆为两批，本批为批次一
- **对应 spec**：`.trae/specs/refine-couple-space-and-sync/`
- **硬性约束**：不改动已可用的绑定流程（创建 / 绑定码 / 加入 / 双方确认绑定）

**实现**

- [x] SubTask 8.8.1：云函数 `space.update` 改为任一有效成员可改名称/简介；新增 `space.rejectUnbind`（仅被申请方，置 `rejected`）与 `space.cancelUnbind`（仅发起方，置 `cancelled`）；`space.unbindRequest` 对已有 `unbind_pending` 去重复用。
- [x] SubTask 8.8.2：`SpaceMirror.merge` 支持 `unbind_pending` → `REQUEST_PENDING + PendingAction.UNBIND`（含 `unbindRequestedBy`、`bindRequestId`），此前客户端完全忽略该状态，是「对方收不到解绑」的根因之一。
- [x] SubTask 8.8.3：`CloudSpaceRepository` 新增 `updateInfo / requestUnbind / confirmUnbind / rejectUnbind / cancelUnbind`，统一 `guarded` 与离线降级；改名失败不落本地。
- [x] SubTask 8.8.4：`AppViewModel` 解绑改为云端优先 + `refreshLocal()`；新增 `updateSpaceInfo / confirmUnbind / rejectUnbind / cancelUnbind`，删除本地模拟确认。
- [x] SubTask 8.8.5：`SpaceScreen` 空间信息新增「编辑资料」弹窗（双方可用）；解绑区分「我发起的（可撤回）」与「对方发起的（确认 / 拒绝）」；移除「模拟对方确认」。
- [x] SubTask 8.8.6：同步可见化：`SyncPolicy`（20 秒节流 + 异常条文案 + 未绑定时延迟推送 `couple` 内容）；`AppViewModel` 前台 20 秒静默轮询、进入页面同步、退后台停止；三个主页面下拉刷新；「我们」页新增云同步卡；顶部离线/失败可点击重试状态条。
- [x] SubTask 8.8.7：修复同步真实缺陷：未绑定时 `couple` 内容无 `spaceId` 被云端 400 后 outbox 直接丢弃，导致内容永久不上传；现改为保留 outbox、绑定后再推。
- [x] SubTask 8.8.T1：新增/扩展单测：`SpaceMirrorTest` 9 条、`CloudSpaceRepositoryTest` 15 条、`SyncPolicyTest` 11 条；全量 25 个测试类 / 179 条 / 0 失败。
- [x] SubTask 8.8.8：构建 `dist\S520J-v0.9.0-stage9-space-sync-debug.apk`（versionCode 900，badging 校验通过）。
- [ ] SubTask 8.8.9：部署云函数（被 CloudBase CLI 未登录阻塞，待用户完成一次 `tcb login`）。
- [ ] SubTask 8.8.10：双机实机验收通过后 Git commit + tag（连同 8.6.6 / 8.7.8 一并提交）。

**实机测试步骤（阶段 9 批次一，双机 A/B）**

1. A 进入「我们 → 共同空间」→ 空间信息卡点「编辑资料」→ 改名并保存 → **预期**：A 立即显示新名称；B 在 20 秒内（或下拉刷新 / 点「立即同步」）看到新名称。B 同样可编辑。
2. A 点「发起解绑」并确认 → **预期**：A 显示「解绑申请中 · 已向 TA 发起解绑」，按钮变为「撤回解绑申请」；B 在 20 秒内显示解绑申请卡片，可选择「确认解绑」或「拒绝解绑」。
3. B 点「拒绝解绑」→ **预期**：双方回到「已绑定」，A 收到「已拒绝解绑」提示。
4. 再次由 A 发起解绑 → B 点「确认解绑」→ **预期**：双方在 20 秒内都变为「冻结归档」，顶部只读提示条出现。
5. A 发起解绑后在 B 处理前点「撤回解绑申请」→ **预期**：双方回到「已绑定」，B 的解绑卡片消失。
6. A 新增一条「共同可见」记录（如拾光-做过的菜）→ **预期**：B 在 20 秒内或下拉刷新后看到；A 在未绑定期间创建的共同内容，绑定后也会补传（不再丢失）。
7. 「我们」页顶部云同步卡 → **预期**：显示上次同步相对时间、待上传条数、立即同步按钮与结果文案；下拉三主页面均可触发同步。
8. 两台手机同时断网 → **预期**：顶部出现「当前离线，云端同步暂停 · 点击重试」；恢复网络后 20 秒内自动消失。
9. 回归：创建空间 / 生成绑定码 / 输入绑定码 / 双方确认绑定 的流程与 v0.8.3 完全一致。

---

## Task 9: 阶段 9 — 备份导出与交付（`v1.0.0`）

- **进入条件**：M8 实机验证通过
- **计划完成日**：2026-11-28
- **退出条件**：双设备全量回归通过，导出可在系统文件选择器保存，交付物齐全

- [x] SubTask 9.1: 手动备份、恢复、清理；JSON + 图片 ZIP 导出（不加密），系统文件选择器保存（`BackupService` + `BackupCodec`，数据管理页已接入）
- [x] SubTask 9.2: 全量回归：外观、功能、隐私、账号与同步、非功能（无横向溢出、键盘不遮挡、空状态、断点续传）——静态审查完成，问题见优化清单
- [x] SubTask 9.3: 安全校验与性能优化（首屏、列表滚动、图片内存）——已识别非懒加载列表、图片缓存无上限等问题，列入优化清单
- [x] SubTask 9.4: 交付物：完整源码、Gradle 配置、云函数代码、数据库初始化脚本、存储桶配置说明（`docs/交付说明.md`）
- [x] SubTask 9.5: Windows 构建说明（`gradlew assembleRelease` + keystore 签名）与运维手册（`docs/运维手册.md`）
- [ ] SubTask 9.6: 构建最终 `v1.0.0` APK 并提交最后一轮实机验证（已出包 `dist/S520J-v1.0.0-debug.apk`，versionCode 1001，待实机验收）

**实机测试步骤（阶段 9）**

1. 执行导出 → **预期**：系统文件选择器弹出，可保存到任意位置；产物含 JSON + 图片 ZIP，未加密，可直接解压查看。
2. 执行手动备份 → 清理本地数据 → 从备份恢复 → **预期**：内容完整还原。
3. 遍历四个主页与全部二级页 → **预期**：无横向溢出、键盘不遮挡输入框、横滑不影响纵向滚动。
4. 双设备全量回归 → **预期**：注册/登录/绑定/同步/导出全流程通过。
5. 查看交付文档与运维手册 → **预期**：包含 Windows 构建步骤、控制台操作、管理员重置密码脚本、用量告警、备份导出、故障排查。

---

# Task Dependencies

- Task 1 依赖 Task 0（工具链未就绪则无法出 APK）
- Task 2 依赖 Task 1 实机验证通过
- Task 3 依赖 Task 2 实机验证通过
- Task 4、5、6、7 依赖 Task 3，可并行开发（共享 `space_id` 上下文与通用组件），但**每个阶段单独出包并单独等待实机验证**
- Task 8 依赖 Task 2–7 全部通过，且需 CloudBase 环境就绪
- Task 9 依赖 Task 8

# 每阶段固定门禁清单（对所有阶段生效）

- [ ] 该阶段全部子任务完成且任务框已勾选
- [ ] TDD 合规：该阶段新增/修改的每个行为都有测试，且均为「先失败 → 后通过」
- [ ] `gradlew testDebugUnitTest` 全绿，输出无失败无警告
- [ ] 全量代码审查（含阶段前代码，重点检查回归风险）
- [ ] 关键路径自测通过（无崩溃、无静默失败、所有可点控件有反馈）
- [ ] `scripts/build_apk.ps1 -Tag <阶段 tag>` 构建成功且 `aapt2 dump badging` 校验通过
- [ ] 阶段验收清单（见 checklist.md 对应分组）逐条自查通过
- [ ] 已向用户提交 APK 路径与「实机测试步骤」，并暂停开发等待实机反馈
- [ ] 用户反馈问题全部修复且已完成全量回归后，才再次发起验证请求
- [ ] 用户实机验证通过后，**先执行一次 Git 提交做版本保存**，再打 Git tag
