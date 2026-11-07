# 记录类统一分类体系实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让每条记录只归属一个模块，分类完全由模块派生，快速记录成为纯采集器并在保存后直达对应页面。

**Architecture:** 领域层新增 `QuickRecordRouter` 承担「采集类型 → 目标模块」与历史数据迁移；`PhotoFeed` 改为模块派生；UI 层五入口直接打开目标模块编辑器，拾光照片墙按模块聚合五个来源。数据迁移由 DataStore 布尔标记守卫，幂等且保留 id/时间/图片/可见性。

**Tech Stack:** Kotlin、Jetpack Compose、Hilt、Room、DataStore、JUnit4。

**项目约定（覆盖技能默认的每步提交）：** 本仓库为阶段门禁制，用户实机验证通过前不提交、不打 tag。所有任务完成后统一执行一次 `git status` 交由用户确认，禁止在任务中途 commit。

**测试环境：** 单测必须在 ASCII 联接目录执行（中文路径会导致 Test Worker ClassNotFound）：

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --rerun-tasks
```

---

## 文件结构

| 文件 | 操作 | 职责 |
|---|---|---|
| `app/src/main/java/com/s520j/app/data/model/Models.kt` | 修改 | 新增 `TRAVEL` 模块 |
| `app/src/main/java/com/s520j/app/domain/heart/QuickRecordTypes.kt` | 修改 | 五类型常量 + `targetModuleOf` |
| `app/src/main/java/com/s520j/app/domain/heart/QuickRecordRouter.kt` | 新建 | 采集类型路由 + 历史迁移（纯函数） |
| `app/src/main/java/com/s520j/app/domain/heart/PhotoFeed.kt` | 修改 | 模块派生分类 + 五来源聚合 |
| `app/src/main/java/com/s520j/app/data/local/S520JDao.kt` | 修改 | 新增 `listAll()` |
| `app/src/main/java/com/s520j/app/data/repository/ContentRepository.kt` | 修改 | 迁移入口 + SeedData 调整 |
| `app/src/main/java/com/s520j/app/data/prefs/UserPreferences.kt` | 修改 | `taxonomy_v2_migrated` 标记 |
| `app/src/main/java/com/s520j/app/ui/AppViewModel.kt` | 修改 | 迁移触发、结构化消息与保存回执 |
| `app/src/main/java/com/s520j/app/ui/navigation/S520JNavHost.kt` | 修改 | 编辑器直达回调、Snackbar 动作跳转 |
| `app/src/main/java/com/s520j/app/ui/screens/heart/HeartScreen.kt` | 修改 | 五入口直达、最近的美好改读浪漫时刻 |
| `app/src/main/java/com/s520j/app/ui/screens/module/ModuleEditScreen.kt` | 修改 | 删除照片分类卡与 `type/category` 写入 |
| `app/src/main/java/com/s520j/app/ui/screens/tacit/TacitScreen.kt` | 修改 | 删除快速记录区块、浪漫时刻置末 |
| `app/src/main/java/com/s520j/app/ui/screens/gallery/GalleryScreen.kt` | 修改 | 新 PhotoFeed、统计口径、tab 直达 |
| `app/src/test/java/com/s520j/app/domain/heart/QuickRecordTypesTest.kt` | 新建 | 五类型与路由测试 |
| `app/src/test/java/com/s520j/app/domain/heart/QuickRecordRouterTest.kt` | 新建 | 路由与迁移测试 |
| `app/src/test/java/com/s520j/app/domain/heart/PhotoFeedTest.kt` | 新建 | 分类派生与聚合测试 |
| `app/src/test/java/com/s520j/app/domain/heart/HeartLogicTest.kt` | 修改 | 移除旧快速记录类型断言 |
| `app/src/test/java/com/s520j/app/domain/heart/CrossPageSyncTest.kt` | 修改 | 移除旧照片墙断言 |

---

### Task 1: 新增 TRAVEL 模块与五类快速记录

**Files:**
- Modify: `app/src/main/java/com/s520j/app/data/model/Models.kt`
- Modify: `app/src/main/java/com/s520j/app/domain/heart/QuickRecordTypes.kt`
- Test: `app/src/test/java/com/s520j/app/domain/heart/QuickRecordTypesTest.kt`
- Modify: `app/src/test/java/com/s520j/app/domain/heart/HeartLogicTest.kt`

- [ ] **Step 1: 写失败测试**

新建 `QuickRecordTypesTest.kt`：

```kotlin
package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickRecordTypesTest {

    @Test
    fun `五个采集类型按顺序排列且不再包含文字`() {
        assertEquals(listOf("照片", "做饭", "探店", "旅行", "浪漫时刻"), QuickRecordTypes.all)
        assertFalse(QuickRecordTypes.all.contains("文字"))
    }

    @Test
    fun `五种类型均要求照片`() {
        QuickRecordTypes.all.forEach { assertTrue(it, QuickRecordTypes.needsPhoto(it)) }
        assertFalse(QuickRecordTypes.needsPhoto(null))
        assertFalse(QuickRecordTypes.needsPhoto("未知"))
    }

    @Test
    fun `类型路由到唯一目标模块`() {
        assertEquals(ContentModule.PHOTO, QuickRecordTypes.targetModuleOf("照片"))
        assertEquals(ContentModule.DISH, QuickRecordTypes.targetModuleOf("做饭"))
        assertEquals(ContentModule.SHOP, QuickRecordTypes.targetModuleOf("探店"))
        assertEquals(ContentModule.TRAVEL, QuickRecordTypes.targetModuleOf("旅行"))
        assertEquals(ContentModule.ROMANTIC_MOMENT, QuickRecordTypes.targetModuleOf("浪漫时刻"))
        assertNull(QuickRecordTypes.targetModuleOf("文字"))
        assertNull(QuickRecordTypes.targetModuleOf(null))
    }

    @Test
    fun `类型归一化去除空白并拒绝未知值`() {
        assertEquals("旅行", QuickRecordTypes.normalize(" 旅行 "))
        assertNull(QuickRecordTypes.normalize("文字"))
    }
}
```

同时删除 `HeartLogicTest.kt` 中「快速记录」整段旧测试（`// ---------- 快速记录 ----------` 到该测试方法结束，即原 179–193 行）。

- [ ] **Step 2: 运行测试确认失败**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --tests "com.s520j.app.domain.heart.QuickRecordTypesTest" --rerun-tasks
```

预期：编译失败或断言失败（`QuickRecordTypes.all` 仍是五旧类型、没有 `targetModuleOf`、没有 `ContentModule.TRAVEL`）。

- [ ] **Step 3: 新增 TRAVEL 模块**

在 `Models.kt` 的 GALLERY 组内、`SHOP` 之后插入：

```kotlin
    TRAVEL(ModuleGroup.GALLERY, "一起去过的地方", Visibility.COUPLE, needsPhoto = true),
```

最终 GALLERY 段为：

```kotlin
    PHOTO(ModuleGroup.GALLERY, "共同照片", Visibility.COUPLE, needsPhoto = true),
    DISH(ModuleGroup.GALLERY, "一起做过的菜", Visibility.COUPLE, needsPhoto = true, supportsRating = true),
    SHOP(ModuleGroup.GALLERY, "一起去过的店", Visibility.COUPLE, needsPhoto = true, supportsRating = true),
    TRAVEL(ModuleGroup.GALLERY, "一起去过的地方", Visibility.COUPLE, needsPhoto = true),
    COLLECTED_MOMENT(ModuleGroup.GALLERY, "收藏的瞬间", Visibility.COUPLE, needsPhoto = true),
```

- [ ] **Step 4: 重写 QuickRecordTypes**

```kotlin
package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule

/** 快速记录五入口：仅作采集类型，保存时路由到唯一归属模块。 */
object QuickRecordTypes {
    const val PHOTO = "照片"
    const val COOKING = "做饭"
    const val SHOP = "探店"
    const val TRAVEL = "旅行"
    const val ROMANTIC = "浪漫时刻"

    val all: List<String> = listOf(PHOTO, COOKING, SHOP, TRAVEL, ROMANTIC)

    /** 五种类型都必须带照片；类型级门禁只用于入口校验。 */
    fun needsPhoto(type: String?): Boolean = normalize(type) != null

    fun normalize(type: String?): String? = type?.trim()?.takeIf { it in all }

    /** 采集类型 → 唯一归属模块；未知类型返回 null。 */
    fun targetModuleOf(type: String?): ContentModule? = when (normalize(type)) {
        PHOTO -> ContentModule.PHOTO
        COOKING -> ContentModule.DISH
        SHOP -> ContentModule.SHOP
        TRAVEL -> ContentModule.TRAVEL
        ROMANTIC -> ContentModule.ROMANTIC_MOMENT
        else -> null
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --tests "com.s520j.app.domain.heart.QuickRecordTypesTest" --tests "com.s520j.app.domain.heart.HeartLogicTest" --rerun-tasks
```

预期：2 个测试类全绿。此时 `HeartScreen.kt` 仍引用 `QuickRecordTypes.TEXT`，会编译失败 —— 若出现该错误，先临时在 Task 5 中修复，本步以 `QuickRecordTypesTest` 能编译为前提；若无法编译，先完成 Task 5 的图标/入口改动再回跑本步。

---

### Task 2: QuickRecordRouter 路由与迁移

**Files:**
- Create: `app/src/main/java/com/s520j/app/domain/heart/QuickRecordRouter.kt`
- Test: `app/src/test/java/com/s520j/app/domain/heart/QuickRecordRouterTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuickRecordRouterTest {

    @Test
    fun `已知类型路由到目标模块并清理分类字段`() {
        val cases = mapOf(
            "照片" to ContentModule.PHOTO,
            "做饭" to ContentModule.DISH,
            "探店" to ContentModule.SHOP,
            "旅行" to ContentModule.TRAVEL,
            "浪漫" to ContentModule.ROMANTIC_MOMENT,
            "浪漫时刻" to ContentModule.ROMANTIC_MOMENT
        )
        cases.forEach { (type, expected) ->
            val routed = QuickRecordRouter.route(
                quickRecord(
                    type = type,
                    extra = mapOf("type" to type, "category" to "日常", "星级" to "4")
                )
            )
            assertNotNull(type, routed)
            assertEquals(type, expected, routed!!.module)
            assertEquals(mapOf("星级" to "4"), routed.extra)
        }
    }

    @Test
    fun `旧文字类型迁移到笔记并保留原可见性`() {
        val routed = QuickRecordRouter.route(quickRecord(type = "文字", visibility = Visibility.PRIVATE))
        assertEquals(ContentModule.NOTE, routed!!.module)
        assertEquals(Visibility.PRIVATE, routed.visibility)
    }

    @Test
    fun `未知类型落入历史容器`() {
        val routed = QuickRecordRouter.route(quickRecord(type = "不明"))
        assertEquals(ContentModule.RECENT_MOMENT, routed!!.module)
    }

    @Test
    fun `非快速记录不参与路由`() {
        val photo = quickRecord(type = "照片").copy(module = ContentModule.PHOTO)
        assertNull(QuickRecordRouter.route(photo))
    }

    @Test
    fun `迁移保留 id 时间与图片并搬空历史容器`() {
        val quick = quickRecord(type = "做饭", mediaIds = listOf("uri-1"), createdAt = 100L, version = 3)
        val recent = quickRecord(type = "照片").copy(
            id = "recent-1",
            module = ContentModule.RECENT_MOMENT,
            createdAt = 50L
        )
        val photo = quickRecord(type = "照片").copy(
            id = "photo-1",
            module = ContentModule.PHOTO,
            createdAt = 20L
        )

        val migrated = QuickRecordRouter.migrate(listOf(quick, recent, photo))

        assertEquals(3, migrated.size)
        assertEquals(ContentModule.DISH, migrated[0].module)
        assertEquals("rec-做饭", migrated[0].id)
        assertEquals(100L, migrated[0].createdAt)
        assertEquals(3, migrated[0].version)
        assertEquals(listOf("uri-1"), migrated[0].mediaIds)
        assertEquals(ContentModule.ROMANTIC_MOMENT, migrated[1].module)
        assertEquals("recent-1", migrated[1].id)
        assertEquals(ContentModule.PHOTO, migrated[2].module)
        assertEquals("photo-1", migrated[2].id)
    }

    @Test
    fun `已知类型迁移幂等`() {
        val records = listOf(quickRecord(type = "做饭"), quickRecord(type = "探店"))
        val once = QuickRecordRouter.migrate(records)
        assertEquals(once, QuickRecordRouter.migrate(once))
    }

    private fun quickRecord(
        type: String,
        visibility: Visibility = Visibility.COUPLE,
        mediaIds: List<String> = emptyList(),
        createdAt: Long = 0L,
        version: Int = 1,
        extra: Map<String, String> = mapOf("type" to type)
    ): ContentRecord = ContentRecord(
        id = "rec-$type",
        module = ContentModule.QUICK_RECORD,
        spaceId = "space-local",
        ownerId = "local-user",
        ownerName = "小明",
        visibility = visibility,
        title = type,
        content = "",
        eventDate = null,
        mediaIds = mediaIds,
        extra = extra,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = version
    )
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --tests "com.s520j.app.domain.heart.QuickRecordRouterTest" --rerun-tasks
```

预期：编译失败（`QuickRecordRouter` 未定义）。

- [ ] **Step 3: 实现 QuickRecordRouter**

```kotlin
package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord

/** 采集类型 → 唯一归属模块；同时承担旧数据的一次性迁移。 */
object QuickRecordRouter {

    private const val LEGACY_TEXT_TYPE = "文字"
    private const val LEGACY_ROMANTIC_TYPE = "浪漫"

    /** 将一条快速记录转换为其目标模块；非快速记录返回 null。 */
    fun route(record: ContentRecord): ContentRecord? {
        if (record.module != ContentModule.QUICK_RECORD) return null
        return record.copy(
            module = targetForLegacyType(record.extra["type"]),
            extra = record.extra.filterKeys { it != "type" && it != "category" }
        )
    }

    /**
     * 一次性迁移：先把既有 RECENT_MOMENT 整体搬入 ROMANTIC_MOMENT，
     * 再把 QUICK_RECORD 按类型路由。保留 id / 时间 / 图片 / 可见性。
     */
    fun migrate(records: List<ContentRecord>): List<ContentRecord> = records.map { record ->
        when (record.module) {
            ContentModule.RECENT_MOMENT -> record.copy(module = ContentModule.ROMANTIC_MOMENT)
            ContentModule.QUICK_RECORD -> route(record) ?: record
            else -> record
        }
    }

    private fun targetForLegacyType(rawType: String?): ContentModule = when (rawType?.trim()) {
        QuickRecordTypes.PHOTO -> ContentModule.PHOTO
        QuickRecordTypes.COOKING -> ContentModule.DISH
        QuickRecordTypes.SHOP -> ContentModule.SHOP
        QuickRecordTypes.TRAVEL -> ContentModule.TRAVEL
        QuickRecordTypes.ROMANTIC, LEGACY_ROMANTIC_TYPE -> ContentModule.ROMANTIC_MOMENT
        LEGACY_TEXT_TYPE -> ContentModule.NOTE
        else -> ContentModule.RECENT_MOMENT
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --tests "com.s520j.app.domain.heart.QuickRecordRouterTest" --rerun-tasks
```

预期：6 个用例全绿。

---

### Task 3: PhotoFeed 改为模块派生

**Files:**
- Modify: `app/src/main/java/com/s520j/app/domain/heart/PhotoFeed.kt`
- Test: `app/src/test/java/com/s520j/app/domain/heart/PhotoFeedTest.kt`
- Modify: `app/src/test/java/com/s520j/app/domain/heart/CrossPageSyncTest.kt`

- [ ] **Step 1: 写失败测试**

新建 `PhotoFeedTest.kt`：

```kotlin
package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFeedTest {

    @Test
    fun `分类顺序为全部做饭探店旅行日常浪漫时刻`() {
        assertEquals(listOf("全部", "做饭", "探店", "旅行", "日常", "浪漫时刻"), PhotoFeed.categories)
    }

    @Test
    fun `分类完全由模块派生`() {
        assertEquals("日常", PhotoFeed.categoryOf(record(ContentModule.PHOTO)))
        assertEquals("做饭", PhotoFeed.categoryOf(record(ContentModule.DISH)))
        assertEquals("探店", PhotoFeed.categoryOf(record(ContentModule.SHOP)))
        assertEquals("旅行", PhotoFeed.categoryOf(record(ContentModule.TRAVEL)))
        assertEquals("浪漫时刻", PhotoFeed.categoryOf(record(ContentModule.ROMANTIC_MOMENT)))
        assertNull(PhotoFeed.categoryOf(record(ContentModule.NOTE)))
        assertNull(PhotoFeed.categoryOf(record(ContentModule.QUICK_RECORD)))
    }

    @Test
    fun `分类可反查目标模块`() {
        assertEquals(ContentModule.DISH, PhotoFeed.moduleOf("做饭"))
        assertEquals(ContentModule.SHOP, PhotoFeed.moduleOf("探店"))
        assertEquals(ContentModule.TRAVEL, PhotoFeed.moduleOf("旅行"))
        assertEquals(ContentModule.PHOTO, PhotoFeed.moduleOf("日常"))
        assertEquals(ContentModule.ROMANTIC_MOMENT, PhotoFeed.moduleOf("浪漫时刻"))
        assertNull(PhotoFeed.moduleOf("全部"))
    }

    @Test
    fun `照片墙只聚合五个来源且必须有图`() {
        val records = listOf(
            record(ContentModule.PHOTO, "照片", 10L, listOf("u1")),
            record(ContentModule.DISH, "做饭", 20L, listOf("u2")),
            record(ContentModule.SHOP, "探店", 30L, listOf("u3")),
            record(ContentModule.TRAVEL, "旅行", 40L, listOf("u4")),
            record(ContentModule.ROMANTIC_MOMENT, "浪漫", 50L, listOf("u5")),
            record(ContentModule.COLLECTED_MOMENT, "收藏", 60L, listOf("u6")),
            record(ContentModule.NOTE, "笔记", 70L, listOf("u7")),
            record(ContentModule.PHOTO, "无图", 80L, emptyList())
        )
        assertEquals(
            listOf("浪漫", "旅行", "探店", "做饭", "照片"),
            PhotoFeed.feed(records).map { it.title }
        )
    }

    @Test
    fun `分类筛选按模块生效`() {
        val feed = PhotoFeed.feed(
            listOf(
                record(ContentModule.TRAVEL, "旅行", 1L, listOf("u")),
                record(ContentModule.DISH, "做饭", 2L, listOf("u"))
            )
        )
        assertEquals(listOf("做饭", "旅行"), PhotoFeed.filter(feed, "全部").map { it.title })
        assertEquals(listOf("旅行"), PhotoFeed.filter(feed, "旅行").map { it.title })
        assertTrue(PhotoFeed.filter(feed, "探店").isEmpty())
    }

    private fun record(
        module: ContentModule,
        title: String = module.name,
        createdAt: Long = 0L,
        mediaIds: List<String> = emptyList()
    ): ContentRecord = ContentRecord(
        id = "test-$title",
        module = module,
        spaceId = "space-local",
        ownerId = "local-user",
        ownerName = "小明",
        visibility = Visibility.COUPLE,
        title = title,
        content = "",
        eventDate = null,
        mediaIds = mediaIds,
        extra = emptyMap(),
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = 1
    )
}
```

同时删除 `CrossPageSyncTest.kt` 中「照片墙聚合」整段旧测试（`// ---------- 照片墙聚合 ----------` 到 `照片墙分类筛选...` 方法结束，原 37–68 行）。保留时段问候与心愿状态测试。

- [ ] **Step 2: 运行测试确认失败**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --tests "com.s520j.app.domain.heart.PhotoFeedTest" --rerun-tasks
```

预期：编译失败（`PhotoFeed.feed` 仍接收两个参数、`moduleOf` 不存在）。

- [ ] **Step 3: 重写 PhotoFeed**

```kotlin
package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord

/** 拾光照片墙：分类完全由模块派生，聚合五个带图来源。 */
object PhotoFeed {
    const val ALL = "全部"
    const val COOKING = "做饭"
    const val SHOP = "探店"
    const val TRAVEL = "旅行"
    const val DAILY = "日常"
    const val ROMANTIC = "浪漫时刻"

    val categories: List<String> = listOf(ALL, COOKING, SHOP, TRAVEL, DAILY, ROMANTIC)

    val sources: List<ContentModule> = listOf(
        ContentModule.PHOTO,
        ContentModule.DISH,
        ContentModule.SHOP,
        ContentModule.TRAVEL,
        ContentModule.ROMANTIC_MOMENT
    )

    fun categoryOf(record: ContentRecord): String? = when (record.module) {
        ContentModule.PHOTO -> DAILY
        ContentModule.DISH -> COOKING
        ContentModule.SHOP -> SHOP
        ContentModule.TRAVEL -> TRAVEL
        ContentModule.ROMANTIC_MOMENT -> ROMANTIC
        else -> null
    }

    /** 分类 → 对应模块，供 tab 直达与空状态跳转；「全部」不指向单一模块。 */
    fun moduleOf(category: String): ContentModule? = when (category) {
        COOKING -> ContentModule.DISH
        SHOP -> ContentModule.SHOP
        TRAVEL -> ContentModule.TRAVEL
        DAILY -> ContentModule.PHOTO
        ROMANTIC -> ContentModule.ROMANTIC_MOMENT
        else -> null
    }

    fun feed(records: List<ContentRecord>): List<ContentRecord> =
        records.filter { it.module in sources && it.mediaIds.isNotEmpty() }
            .sortedByDescending { it.createdAt }

    fun filter(feed: List<ContentRecord>, category: String): List<ContentRecord> =
        if (category == ALL) feed else feed.filter { categoryOf(it) == category }
}
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --tests "com.s520j.app.domain.heart.PhotoFeedTest" --tests "com.s520j.app.domain.heart.CrossPageSyncTest" --rerun-tasks
```

预期：全绿。`GalleryScreen.kt` 此时仍用旧 `feed(photos, quickRecords)`，会在 Task 8 修复。

---

### Task 4: 数据层迁移入口

**Files:**
- Modify: `app/src/main/java/com/s520j/app/data/local/S520JDao.kt`
- Modify: `app/src/main/java/com/s520j/app/data/repository/ContentRepository.kt`
- Modify: `app/src/main/java/com/s520j/app/data/prefs/UserPreferences.kt`
- Modify: `app/src/main/java/com/s520j/app/ui/AppViewModel.kt`

- [ ] **Step 1: DAO 增加全量读取**

在 `ContentDao` 的 `findById` 之前加入：

```kotlin
    @Query("SELECT * FROM contents")
    suspend fun listAll(): List<ContentEntity>
```

- [ ] **Step 2: Repository 增加迁移方法**

在 `ContentRepository` 的 `count()` 之后、`seedIfEmpty` 之前加入（并在文件头补 import `com.s520j.app.domain.heart.QuickRecordRouter`）：

```kotlin
    /** 一次性迁移：旧快速记录按类型路由，RECENT_MOMENT 整体搬入浪漫时刻。 */
    suspend fun migrateTaxonomyV2() {
        val originals = contentDao.listAll().map { it.toRecord() }
        val migrated = QuickRecordRouter.migrate(originals)
        val changed = migrated.filterIndexed { index, record ->
            record.module != originals[index].module || record.extra != originals[index].extra
        }
        if (changed.isNotEmpty()) {
            contentDao.upsertAll(changed.map { it.toEntity() })
        }
    }
```

- [ ] **Step 3: DataStore 增加迁移标记**

在 `UserPreferences.Keys` 中追加：

```kotlin
        val TAXONOMY_V2_MIGRATED = booleanPreferencesKey("taxonomy_v2_migrated")
```

在 `sessionSnapshot()` 之前追加：

```kotlin
    /** 记录类分类体系 v2 迁移是否已执行（幂等守卫）。 */
    suspend fun taxonomyMigratedSnapshot(): Boolean =
        context.dataStore.data.map { it[Keys.TAXONOMY_V2_MIGRATED] ?: false }.first()

    suspend fun markTaxonomyMigrated() {
        context.dataStore.edit { it[Keys.TAXONOMY_V2_MIGRATED] = true }
    }
```

- [ ] **Step 4: AppViewModel 启动时执行迁移**

`seedIfNeeded()` 改为：

```kotlin
    fun seedIfNeeded() = viewModelScope.launch {
        val snapshot = prefs.sessionSnapshot()
        if (snapshot.loggedIn) {
            seedContent()
            if (!prefs.taxonomyMigratedSnapshot()) {
                contentRepository.migrateTaxonomyV2()
                prefs.markTaxonomyMigrated()
            }
        }
    }
```

- [ ] **Step 5: 编译验证**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:compileDebugKotlin --rerun-tasks
```

预期：BUILD SUCCESSFUL（此时 UI 与 saveRecord 仍有旧引用，若失败先完成 Task 5–Task 10 后回读）。

---

### Task 5: 心动页五入口直达目标模块

**Files:**
- Modify: `app/src/main/java/com/s520j/app/ui/screens/heart/HeartScreen.kt`
- Modify: `app/src/main/java/com/s520j/app/ui/navigation/S520JNavHost.kt`

- [ ] **Step 1: HeartScreen 增加编辑器直达回调**

函数签名改为：

```kotlin
@Composable
fun HeartScreen(
    vm: AppViewModel,
    onOpenModule: (ContentModule, String?) -> Unit,
    onOpenEditor: (ContentModule) -> Unit,
    onOpenRecord: (ContentModule, String) -> Unit
)
```

- [ ] **Step 2: 「最近的美好」改读 ROMANTIC_MOMENT**

把：

```kotlin
    val momentsFlow = remember { vm.contents(ContentModule.RECENT_MOMENT) }
```

改为：

```kotlin
    val momentsFlow = remember { vm.contents(ContentModule.ROMANTIC_MOMENT) }
```

并把该区块内三处 `ContentModule.RECENT_MOMENT` 改为 `ContentModule.ROMANTIC_MOMENT`（`onAction`、空状态 `onOpenModule`、`onOpenRecord`）。

- [ ] **Step 3: 快速记录入口传入编辑器回调**

调用处改为：

```kotlin
        QuickRecordSection(onOpenEditor)
```

`QuickRecordSection` 定义改为：

```kotlin
@Composable
private fun QuickRecordSection(onOpenEditor: (ContentModule) -> Unit) {
    HeartSectionHeader(title = "快速记录")
    Spacer(Modifier.height(14.dp))
    BoxWithConstraints {
        val gap = 8.dp
        val itemWidth = (maxWidth - gap * 4f) / 5f
        val circle = minOf(54.dp, itemWidth)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            QuickRecordTypes.all.forEach { type ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(circle)
                            .clip(CircleShape)
                            .background(S520JColors.PrimarySoft)
                            .clickable {
                                QuickRecordTypes.targetModuleOf(type)?.let(onOpenEditor)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = quickIcon(type),
                            contentDescription = type,
                            tint = S520JColors.DeepGreen,
                            modifier = Modifier.size(circle * 0.44f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = type,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = S520JColors.TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: 图标映射去掉文字、补旅行**

```kotlin
private fun quickIcon(type: String): ImageVector = when (type) {
    QuickRecordTypes.PHOTO -> Icons.Outlined.PhotoCamera
    QuickRecordTypes.COOKING -> Icons.Outlined.Restaurant
    QuickRecordTypes.SHOP -> Icons.Outlined.Storefront
    QuickRecordTypes.TRAVEL -> Icons.Outlined.Luggage
    else -> Icons.Outlined.AutoAwesome
}
```

并删除 `quickLabel` 函数；文件头 import 增加 `androidx.compose.material.icons.outlined.Luggage`，删除不再使用的 `androidx.compose.material.icons.outlined.Edit`。

- [ ] **Step 5: 导航接线**

`S520JNavHost.kt` 的 `HeartScreen` 调用增加：

```kotlin
                        onOpenEditor = { module ->
                            navController.navigate("module/${module.name}/edit")
                        },
```

- [ ] **Step 6: 编译验证**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:compileDebugKotlin --rerun-tasks
```

---

### Task 6: 编辑页删除照片分类与旧字段写入

**Files:**
- Modify: `app/src/main/java/com/s520j/app/ui/screens/module/ModuleEditScreen.kt`

- [ ] **Step 1: 删除分类常量与状态**

删除：

```kotlin
private val photoCategories = listOf("日常生活", "做饭", "探店", "旅行", "纪念日")
```

删除 `var category by remember { mutableStateOf("日常生活") }` 与 `LaunchedEffect` 中的 `category = existing.extra["category"] ?: "日常生活"`。

- [ ] **Step 2: 简化照片门禁**

把：

```kotlin
    val showPhoto = when {
        !module.needsPhoto -> false
        module == ContentModule.QUICK_RECORD -> quickType == null || QuickRecordTypes.needsPhoto(quickType)
        else -> true
    }
```

改为：

```kotlin
    val showPhoto = module.needsPhoto
```

并删除 `quickType` 变量、`记录类型` 卡片（`if (quickType != null) { ... }` 整段）与 `ModuleEditScreen.kt` 中 `QuickRecordTypes` 的 import。

- [ ] **Step 3: 删除 PHOTO 分类卡片**

删除：

```kotlin
        if (module == ContentModule.PHOTO) {
            Spacer(Modifier.height(Dimens.CardGap))
            SoftCard {
                Text("分类", ...)
                ...
            }
        }
```

- [ ] **Step 4: 保存不再写 category / type**

保存调用中的：

```kotlin
                    photoType = if (module == ContentModule.PHOTO) category else "",
```

删除；并把 `extra` 构建块中的：

```kotlin
                        if (quickType != null) {
                            put("type", quickType)
                        }
```

删除。

- [ ] **Step 5: 编译验证**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:compileDebugKotlin --rerun-tasks
```

预期：因 `saveRecord` 仍有 `photoType` 参数而通过；Task 10 再移除该参数。

---

### Task 7: 默契页删除快速记录区块、浪漫时刻置末

**Files:**
- Modify: `app/src/main/java/com/s520j/app/ui/screens/tacit/TacitScreen.kt`

- [ ] **Step 1: 调整八入口顺序**

`tacitEntries` 改为（浪漫时刻移到末位）：

```kotlin
private val tacitEntries = listOf(
    TacitEntry(ContentModule.IMPORTANT_DAY, Icons.Filled.Cake, "重要日子"),
    TacitEntry(ContentModule.PROMISE, Icons.Filled.Handshake, "彼此的承诺"),
    TacitEntry(ContentModule.DEED, Icons.Filled.CardGiftcard, "为彼此做过"),
    TacitEntry(ContentModule.RELATIONSHIP_RULE, Icons.Filled.Balance, "相处之道"),
    TacitEntry(ContentModule.LIKE, Icons.Filled.ThumbUp, "TA 的喜欢"),
    TacitEntry(ContentModule.DISLIKE, Icons.Filled.ThumbDown, "TA 不喜欢"),
    TacitEntry(ContentModule.NOTE, Icons.Filled.Edit, "我们的笔记"),
    TacitEntry(ContentModule.ROMANTIC_MOMENT, Icons.Filled.Favorite, "浪漫时刻")
)
```

- [ ] **Step 2: 删除「来自快速记录」区块**

删除 `quickFlow` / `quickRecords` 两行，以及 `SectionHeader(title = "来自快速记录", ...)` 到该区块 `if/else` 结束的整段。同时删除 `QuickPreviewCard` 函数与不再使用的 `TagPill` import（`TacitPreviewCard` 仍用 `TagPill`，若仍使用则保留）。

- [ ] **Step 3: 编译验证**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:compileDebugKotlin --rerun-tasks
```

---

### Task 8: 拾光页对接新 PhotoFeed 与统计口径

**Files:**
- Modify: `app/src/main/java/com/s520j/app/ui/screens/gallery/GalleryScreen.kt`

- [ ] **Step 1: 数据源改为全量记录**

删除 `photosFlow` / `quickFlow` / `dishesFlow` / `shopsFlow` / `momentsFlow`，改为：

```kotlin
    val allFlow = remember { vm.allContents() }
    val records by allFlow.collectAsState(initial = emptyList())
    val isReadOnly by vm.isReadOnly.collectAsState()

    var category by remember { mutableStateOf(PhotoFeed.ALL) }

    // 照片墙 = 五个带图来源，分类完全由 module 派生。
    val feed = remember(records) { PhotoFeed.feed(records) }
    val filtered = remember(feed, category) { PhotoFeed.filter(feed, category) }
    val dishCount = remember(records) { records.count { it.module == ContentModule.DISH } }
    val shopCount = remember(records) { records.count { it.module == ContentModule.SHOP } }
    val collectedCount = remember(records) { records.count { it.module == ContentModule.COLLECTED_MOMENT } }
```

- [ ] **Step 2: 统计卡口径与点击行为**

```kotlin
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                StatCard("共同照片", feed.size, Modifier.weight(1f)) {
                    category = PhotoFeed.ALL
                }
                StatCard("一起做过的菜", dishCount, Modifier.weight(1f)) {
                    onOpenModule(ContentModule.DISH)
                }
            }
            Spacer(Modifier.height(Dimens.CardGap))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                StatCard("一起去过的店", shopCount, Modifier.weight(1f)) {
                    onOpenModule(ContentModule.SHOP)
                }
                StatCard("收藏的瞬间", collectedCount, Modifier.weight(1f)) {
                    onOpenModule(ContentModule.COLLECTED_MOMENT)
                }
            }
```

- [ ] **Step 3: 照片墙 header 与 tab 直达**

`SectionHeader` 改为：

```kotlin
            SectionHeader(
                title = "照片墙",
                subtitle = "两张一组的日常",
                actionLabel = "查看全部",
                onAction = {
                    onOpenModule(PhotoFeed.moduleOf(category) ?: ContentModule.PHOTO)
                }
            )
```

分类 tab 循环保持使用 `PhotoFeed.categories`，顺序即新顺序。

- [ ] **Step 4: 空状态按当前分类跳转**

```kotlin
            if (filtered.isEmpty()) {
                SoftCard {
                    EmptyState("这个分类还没有照片", actionLabel = "去添加") {
                        onOpenModule(PhotoFeed.moduleOf(category) ?: ContentModule.PHOTO)
                    }
                }
            } else {
```

- [ ] **Step 5: 瓦片点击使用记录自身模块；标签为空兜底**

```kotlin
                        pair.forEach { photo ->
                            PhotoTile(photo, Modifier.weight(1f)) {
                                onOpenRecord(photo.module, photo.id)
                            }
                        }
```

`PhotoTile` 内标签改为：

```kotlin
                    text = PhotoFeed.categoryOf(photo).orEmpty(),
```

- [ ] **Step 6: 五星榜文案统一**

```kotlin
                Text(
                    text = "只记录菜品与餐厅的体验，不评价人和关系。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
```

- [ ] **Step 7: 编译验证**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:compileDebugKotlin --rerun-tasks
```

预期：BUILD SUCCESSFUL。若提示 `Visibility` / `toDayTitle` 等 import 未使用，一并清理。

---

### Task 9: SeedData 调整

**Files:**
- Modify: `app/src/main/java/com/s520j/app/data/repository/ContentRepository.kt`

- [ ] **Step 1: RECENT_MOMENT 预设改为 ROMANTIC_MOMENT**

```kotlin
        rec(ContentModule.ROMANTIC_MOMENT, "一起做晚饭", "番茄牛腩，厨房很暖。", now - day, ownerId, ownerName,
            eventDate = now - day),
        rec(ContentModule.ROMANTIC_MOMENT, "夜里的散步", "绕小区走了三圈。", now - day * 3, ownerId, ownerName,
            eventDate = now - day * 3),
```

- [ ] **Step 2: PHOTO 预设去掉 category，旅行改为 TRAVEL**

```kotlin
        rec(ContentModule.TRAVEL, "海边的风", "第一次一起看海。", now - day * 5, ownerId, ownerName,
            eventDate = now - day * 5),
        rec(ContentModule.PHOTO, "窗台的绿植", "新买的小盆栽。", now - day * 8, ownerId, ownerName,
            eventDate = now - day * 8),
        rec(ContentModule.PHOTO, "生日蛋糕", "今年一起过的第一个生日。", now - day * 12, ownerId, ownerName,
            eventDate = now - day * 12),
        rec(ContentModule.PHOTO, "深夜的汤面", "楼下小店的宵夜。", now - day * 15, ownerId, ownerName,
            eventDate = now - day * 15),
```

- [ ] **Step 3: 编译验证**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:compileDebugKotlin --rerun-tasks
```

---

### Task 10: 保存回执与「去看看」直达

**Files:**
- Modify: `app/src/main/java/com/s520j/app/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/s520j/app/ui/navigation/S520JNavHost.kt`
- Modify: `app/src/main/java/com/s520j/app/ui/screens/module/ModuleEditScreen.kt`

- [ ] **Step 1: 结构化消息类型**

在 `AppViewModel.kt` 文件顶部（`@HiltViewModel` 之前）加入：

```kotlin
/** 顶栏提示：文本 + 可选动作（用于保存后「去看看」直达）。 */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val targetModule: ContentModule? = null
)
```

- [ ] **Step 2: 消息通道改为 UiMessage**

把：

```kotlin
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
```

改为：

```kotlin
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private suspend fun send(text: String) {
        _messages.send(UiMessage(text))
    }
```

将文件中所有 `_messages.send(` 替换为 `send(`（`send` 方法自身内部与保存回执处除外）。

- [ ] **Step 3: 移除 photoType 参数与 category 写入**

`saveRecord` 签名删除 `photoType: String,`，删除：

```kotlin
            if (photoType.isNotBlank()) put("category", photoType)
```

- [ ] **Step 4: 保存回执**

把：

```kotlin
        contentRepository.save(record)
        _messages.send(if (existing == null) "已保存" else "已更新")
```

改为：

```kotlin
        contentRepository.save(record)
        if (existing == null) {
            _messages.send(
                UiMessage(
                    text = receiptOf(record),
                    actionLabel = "去看看",
                    targetModule = record.module
                )
            )
        } else {
            send("已更新")
        }
```

并在类内加入：

```kotlin
    /** 保存回执：与分类常量同源，明确落到哪个页面。 */
    private fun receiptOf(record: ContentRecord): String =
        PhotoFeed.categoryOf(record)?.let { "已记入 拾光 · $it" }
            ?: "已记入 ${record.module.group.label} · ${record.module.label}"
```

文件头补 import `com.s520j.app.domain.heart.PhotoFeed`。

- [ ] **Step 5: 编辑页调用去掉 photoType**

删除 `ModuleEditScreen.kt` 保存调用中的 `photoType = ...,` 一行，并删除 `photoCategories` 相关残留（Task 6 已删）。

- [ ] **Step 6: 导航消费动作**

`S520JNavHost.kt` 的 `LaunchedEffect` 改为：

```kotlin
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                message.targetModule?.let { module ->
                    navController.navigate("module/${module.name}")
                }
            }
        }
    }
```

文件头补 import `androidx.compose.material3.SnackbarResult`。

- [ ] **Step 7: 编译验证**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:compileDebugKotlin --rerun-tasks
```

预期：BUILD SUCCESSFUL。

---

### Task 11: 全量回归与构建

**Files:** 无代码修改，仅验证。

- [ ] **Step 1: 全量单测**

```powershell
# cwd = C:\s520j-build
.\gradlew :app:testDebugUnitTest --rerun-tasks
```

预期：既有 81 条（减去已删除的旧照片墙/快速记录用例、加上新增路由与聚合用例）全部通过，0 失败。

- [ ] **Step 2: 命名残留扫描**

```powershell
# cwd = C:\s520j-build
rg -n "日常生活|\"浪漫\"|纪念日" app/src/main/java
```

预期：`日常生活` 0 命中；`"浪漫"` 仅允许出现在 `QuickRecordRouter` 的旧数据兼容分支；`纪念日` 仅允许出现在 `SeedData` 日历事项文案与 `IMPORTANT_DAY` 语境。

- [ ] **Step 3: 构建 APK**

```powershell
# cwd = C:\s520j-build
powershell -ExecutionPolicy Bypass -File scripts/build_apk.ps1 -Tag "v0.5.0-stage1"
```

预期：产出 `dist/S520J-v0.5.0-stage1-debug.apk`。

- [ ] **Step 4: 实机验证清单（交给用户）**

1. 心动页五入口：照片、做饭、探店、旅行、浪漫时刻（无文字）。
2. 五个入口各记一条（含 1 张照片），回执文案分别为「已记入 拾光 · 日常 / 做饭 / 探店 / 旅行」与「已记入 默契 · 浪漫时刻」。
3. 点回执「去看看」直达对应模块列表，新记录在首位。
4. 拾光照片墙 tab 顺序：全部 / 做饭 / 探店 / 旅行 / 日常 / 浪漫时刻。
5. 统计卡数字与对应 tab 瓦片数一致（共同照片 = 全部）。
6. 点「共同照片」卡回到「全部」tab，不跳页。
7. 照片编辑器无「分类」卡片，保存后落「日常」tab。
8. 默契页无「来自快速记录」区块，浪漫时刻为最后一个入口。
9. 心动「最近的美好」显示刚记的浪漫时刻；「查看全部」进入浪漫时刻列表。
10. 覆盖安装（不卸载）后，旧快速记录按类型出现在对应模块，图片仍可显示。

- [ ] **Step 5: 验证通过后统一提交**

等待用户确认第 4 步全部通过后，再执行：

```powershell
git status
git diff --stat
```

由用户确认提交范围与标签。禁止在验证前 commit / tag。
