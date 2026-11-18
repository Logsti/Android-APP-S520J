# S520J 全项目通读 · 汇总 Findings

> 通读范围：根目录构建配置、`docs/`、`.trae/specs/`、`app/src`（145 个 Kotlin/XML）、`cloudbase`（db + functions + scripts）、`dist/` 产物。
> 明细笔记：[findings-android-data.md](findings-android-data.md)、[findings-android-ui.md](findings-android-ui.md)、[findings-cloudbase.md](findings-cloudbase.md)。
> 只读通读，未改动任何业务代码。

---

## 1. 项目定位

- 产品：S520J，仅情侣两人私人使用的 Android 共同生活记录 APP，不上架、手动装 APK。
- 技术栈：Kotlin + Jetpack Compose + Material3 自定义主题 + Room + DataStore + Hilt + Retrofit/kotlinx.serialization + WorkManager；单模块 `:app`，`com.s520j.app`，minSdk 26 / target 35 / compileSdk 35，JDK 17。
- 后端：腾讯云开发 CloudBase（Nodejs20.19 云函数 + PostgreSQL），通过网关 `exec-pgsql` 访问数据库，10 个云函数（auth/space/content/sync/media/notification/recycle/settings/backup/dishSync）。
- 需求基线：`S520J_Android_技术实现需求文档_v1.0.md`（功能/数据/权限）、`前端外观设计需求文档.txt`（视觉 §2.1 色板）。

## 2. 架构分层（实际落地）

```
Compose UI (ui/screens + ui/components + theme)
        ↓ 状态/命令
AppViewModel（1037 行，全局单一 VM，StateFlow + Channel 消息）
        ↓
domain 纯规则（food/heart/tacit/us/gallery，无 Android 依赖）
        ↓
data/repository（本地权威写入 + outbox）
data/cloud（云端认证/空间/点菜）  data/sync（push/pull/冲突/回收站）
data/local（Room v7：9 实体 / 7 DAO）
        ↓ HTTPS  /api/<group>  →  {action, payload, token}
CloudBase 云函数（_shared/entry 前缀路由 → handlers → exec-pgsql）
        ↓
PostgreSQL（17 张表 + RLS 拒绝直连，service_role 代理访问）
```

关键设计：
- 云端为权威源，本地 Room 作缓存 + 离线 outbox；`mediaIds`（本机路径）不出网，跨设备只交换 `mediaRefs`（云端 assetId）。
- 图片压缩到 ≤68KB、base64 存 PG `media_blobs`（未开通对象存储时的过渡方案）。
- 无系统推送：前台 20 秒轮询 `sync + event.pull` + WorkManager 15 分钟周期同步 + 本地通知。
- 可见范围仅两档：`private`（仅本人）/ `couple`（同空间双方）；服务端二次校验 owner/space/visibility/frozen。
- 同步游标：内容 `sync_cursor`（updated_at 毫秒）、事件 `event_cursor`、点菜快照 `last_seen_at:id` 复合游标。

## 3. 四大主页面实现状态

| 页面 | 路由 | 实现程度 | 主要偏差 |
|---|---|---|---|
| 心动 | `heart` | 七区块齐全（问候/挑战/快速记录/心愿/碰碰你/日历/最近美好） | 快速记录第 4 项为「旅行」非「文字」；挑战内容不可编辑 |
| 拾光 | `gallery` | 四入口 + 照片墙 + 五星榜 + FAB | 第 4 入口是「城市(TRAVEL)」非「收藏的瞬间」；收藏的瞬间可创建但无入口（数据黑洞）；分类「浪漫时刻」≠需求「纪念日」 |
| 默契 | `tacit` | 横滑圆入口 + 预览 3 条 + 独立模块页 | 实际 10 个入口（需求 8 个）；相处之道只有单一「双方已确认」标记，无双方独立确认 |
| 我们 | `us` | 资料卡 + 9 个设置组 + 关于只读 | 提醒设置仅本机一份（需求要求双方各自独立）；部分页面仍留有「本地 Mock」过期文案 |

19 条路由：4 主页面 + `gallery/photos`、`starboard`、`module/{module}`、`module/{module}/edit`、`space`、`settings/{account,reminder,appearance,preference,data}`、`sync/conflicts`、`privacy`、`notifications`、`about`、`recycle`。

## 4. 点菜（今天吃啥）专项现状

- 云端已完整：`dishes/dish_quota/dish_meta` 三表、ShowAPI 菜谱大全接入（1164-1/1164-2）、配额 100/日 + 保底 20、四级降级（实时→池→本机快照→内置 90 道）、`dishSync` 每日 03:30 增量。
- 客户端已具备：`domain/food/*`、`DishRandomRepository`、`DishSnapshotSync`、`DishHistoryRepository`、`DishHeaderStore`、`DishPickerScreen`（655 行）、`DishPickerViewModel`。
- **缺口**：`DishPickerScreen` 未注册任何路由，拾光页「今日」卡未替换为入口 → 整屏功能不可达（对应 spec Task 5.8 未完成）。

## 5. 规格（.trae/specs）完成度

| 规格 | tasks | checklist |
|---|---|---|
| add-dish-picker-and-fix-transient-feedback | 28/51 | 0/52 |
| add-photo-letter-pages-and-reset-accounts | 36/36 | 25/25 |
| build-s520j-android-app | 119/137 | 121/137 |
| fix-batch1-sync-and-profile | 20/22 | 13/17 |
| fix-image-upload-zoom-avatar | 0/15 | 0/9 |
| fix-wish-sync-and-quickrecord-ux | 23/23 | 10/10 |
| refine-couple-space-and-sync | 42/44 | 35/46 |
| refine-heart-gallery-us-ux | 38/38 | 14/14 |

注意：当前工作区有 15 个已修改文件 + 约 20 个未跟踪新文件（点菜域），`add-dish-picker...` 的 Task 4/5 实际代码多数已存在但 tasks.md 未勾选。

## 6. 最高优先级风险（跨层）

1. **点菜功能不可达**：`DishPickerScreen` 无路由（客户端）；`dishSync` 未进 `cloudbaserc.json`，`content` 副本缺 dish 路由 → 按现状部署无每日同步。
2. **`_shared` 副本过期**：9 个函数目录的 `_shared/handlers.js` 比全局旧（48863B vs 49060B），`dishSync/_shared` 缺失；部署前必须先跑 `prepare-functions.js`。
3. **数据库权限/凭据**：`cloudbaserc.json` 明文 service_role 级 API Key（有效期到 9999 年）重复 9 处；`init-pg-role.js` 硬编码 host/port。
4. **数据安全**：outbox 与 content 写入不在同一事务；服务端 4xx 直接丢 outbox（内容永不上云）；`fallbackToDestructiveMigration` 未覆盖版本即清库。
5. **权限语义**：相处之道「双方确认」只有单布尔；`content.list` 的 limit 无上限；登录无失败限流、错误文案可枚举账号。
6. **验证缺口**：`SyncRepository`/`MediaRepository`/`BackupService`/`AppViewModel`/所有 Composable 无测试；无 Room 迁移测试（`exportSchema=false`）。
7. **误导性文案**：`PrivacyScreen`、`RecycleBinScreen`、`AboutScreen` 仍写「本地 Mock」「接入云端后补全」，与现状矛盾。

## 7. 工程与交付

- 构建：`scripts/build_apk.ps1 -Tag vX.Y.Z` → `dist/S520J-<tag>-debug.apk` + `build-info.txt`；`dist/` 现存 31 个历史 APK（v0.1.0 → v6.4.0）。
- 工具链：`scripts/setup_toolchain.ps1` 便携安装 JDK17 + cmdline-tools（脚本内路径写死 `C:\Users\song9\...`，当前机器不命中）；工程要求 buildTools 36.0.0 但脚本装 35.0.0。
- 数据库初始化顺序：`001_schema` → `002_rls` → `003_couple_live` → `004_media_sync` → `006_dish_pool`（`005_reset_accounts_once` 为破坏性脚本，仅手动）。
- 发布流程见 `docs/运维手册.md` §7：全量单测 → 出包 → badging 校验 → 双机验收 → commit + tag。

## 8. 文档索引

- 需求：`S520J_Android_技术实现需求文档_v1.0.md`、`前端外观设计需求文档.txt`
- 交付/运维：`docs/交付说明.md`、`docs/运维手册.md`
- 历史设计：`docs/superpowers/specs/2026-11-07-content-taxonomy-design.md`、`docs/superpowers/plans/2026-11-07-content-taxonomy-implementation.md`、`docs/superpowers/specs/2026-11-07-round6-ui-fixes/`
- 视觉稿：`docs/mockups/gallery-today-eat-what.html`
