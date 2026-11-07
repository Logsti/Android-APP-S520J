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

    @Test
    fun `空关键词返回原列表`() {
        val feed = listOf(record(ContentModule.PHOTO, "窗台"))
        assertEquals(feed, PhotoFeed.search(feed, ""))
        assertEquals(feed, PhotoFeed.search(feed, "   "))
    }

    @Test
    fun `按标题搜索且忽略大小写`() {
        val feed = listOf(
            record(ContentModule.PHOTO, "Sunset"),
            record(ContentModule.PHOTO, "窗台")
        )
        assertEquals(listOf("Sunset"), PhotoFeed.search(feed, "sun").map { it.title })
        assertEquals(listOf("窗台"), PhotoFeed.search(feed, "窗台").map { it.title })
    }

    @Test
    fun `按内容搜索`() {
        val feed = listOf(
            record(ContentModule.PHOTO, "窗台", content = "新买的小盆栽"),
            record(ContentModule.PHOTO, "蛋糕", content = "生日那天")
        )
        assertEquals(listOf("窗台"), PhotoFeed.search(feed, "盆栽").map { it.title })
    }

    @Test
    fun `搜索与分类叠加`() {
        val feed = PhotoFeed.feed(
            listOf(
                record(ContentModule.DISH, "番茄牛腩", 2L, listOf("u"), "炖了很久"),
                record(ContentModule.TRAVEL, "番茄味的旅行", 1L, listOf("u"))
            )
        )
        val cooking = PhotoFeed.filter(feed, "做饭")
        assertEquals(listOf("番茄牛腩"), PhotoFeed.search(cooking, "番茄").map { it.title })
    }

    private fun record(
        module: ContentModule,
        title: String = module.name,
        createdAt: Long = 0L,
        mediaIds: List<String> = emptyList(),
        content: String = ""
    ): ContentRecord = ContentRecord(
        id = "test-$title",
        module = module,
        spaceId = "space-local",
        ownerId = "local-user",
        ownerName = "小明",
        visibility = Visibility.COUPLE,
        title = title,
        content = content,
        eventDate = null,
        mediaIds = mediaIds,
        extra = emptyMap(),
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = 1
    )
}
