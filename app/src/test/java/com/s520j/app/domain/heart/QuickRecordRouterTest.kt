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
            "浪漫时刻" to ContentModule.ROMANTIC_MOMENT,
            "随手记" to ContentModule.JOT
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
    fun `随手记迁移到默契页模块`() {
        val migrated = QuickRecordRouter.migrate(listOf(quickRecord(type = "随手记")))
        assertEquals(ContentModule.JOT, migrated.single().module)
        assertEquals("rec-随手记", migrated.single().id)
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
