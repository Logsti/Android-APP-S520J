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

    @Test
    fun `共同照片展平保留单条记录的全部图片`() {
        val images = (1..6).map { "u$it" }
        val photos = PhotoFeed.sharedPhotos(
            listOf(record(ContentModule.PHOTO, "六张照片", mediaIds = images))
        )

        assertEquals(images, photos.map { it.image })
    }

    @Test
    fun `共同照片按记录时间倒序排列`() {
        val photos = PhotoFeed.sharedPhotos(
            listOf(
                record(ContentModule.PHOTO, "较早", createdAt = 10L, mediaIds = listOf("old")),
                record(ContentModule.TRAVEL, "较新", createdAt = 20L, mediaIds = listOf("new"))
            )
        )

        assertEquals(listOf("new", "old"), photos.map { it.image })
    }

    @Test
    fun `共同照片保持记录内图片顺序`() {
        val photos = PhotoFeed.sharedPhotos(
            listOf(
                record(
                    ContentModule.DISH,
                    "晚餐",
                    mediaIds = listOf("first", "second", "third", "fourth", "fifth")
                )
            )
        )

        assertEquals(listOf("first", "second", "third", "fourth", "fifth"), photos.map { it.image })
    }

    @Test
    fun `共同照片使用记录ID和图片序号作为稳定key`() {
        val photos = PhotoFeed.sharedPhotos(
            listOf(record(ContentModule.PHOTO, "稳定key", mediaIds = listOf("same", "same")))
        )

        assertEquals(listOf("test-稳定key#0", "test-稳定key#1"), photos.map { it.key })
    }

    @Test
    fun `共同照片空列表返回空列表`() {
        assertTrue(PhotoFeed.sharedPhotos(emptyList()).isEmpty())
    }

    @Test
    fun `按记录时间正序排列`() {
        val records = listOf(
            record(ContentModule.PHOTO, "十月", createdAt = 30L, mediaIds = listOf("u")),
            record(ContentModule.PHOTO, "八月", createdAt = 10L, mediaIds = listOf("u")),
            record(ContentModule.PHOTO, "九月", createdAt = 20L, mediaIds = listOf("u"))
        )

        assertEquals(
            listOf("八月", "九月", "十月"),
            PhotoFeed.feed(records, byEventDate = false, ascending = true).map { it.title }
        )
    }

    @Test
    fun `按填写日期倒序排列`() {
        val records = listOf(
            record(ContentModule.PHOTO, "八月", createdAt = 10L, mediaIds = listOf("u"), eventDate = 10L),
            record(ContentModule.PHOTO, "十月", createdAt = 20L, mediaIds = listOf("u"), eventDate = 30L),
            record(ContentModule.PHOTO, "九月", createdAt = 30L, mediaIds = listOf("u"), eventDate = 20L)
        )

        assertEquals(
            listOf("十月", "九月", "八月"),
            PhotoFeed.feed(records, byEventDate = true).map { it.title }
        )
    }

    @Test
    fun `按填写日期正序排列`() {
        val records = listOf(
            record(ContentModule.PHOTO, "十月", createdAt = 20L, mediaIds = listOf("u"), eventDate = 30L),
            record(ContentModule.PHOTO, "八月", createdAt = 30L, mediaIds = listOf("u"), eventDate = 10L)
        )

        assertEquals(
            listOf("八月", "十月"),
            PhotoFeed.feed(records, byEventDate = true, ascending = true).map { it.title }
        )
    }

    @Test
    fun `未填写日期时按记录时间参与排序`() {
        val records = listOf(
            record(ContentModule.PHOTO, "无日期", createdAt = 25L, mediaIds = listOf("u")),
            record(ContentModule.PHOTO, "有日期", createdAt = 99L, mediaIds = listOf("u"), eventDate = 20L)
        )

        assertEquals(
            listOf("无日期", "有日期"),
            PhotoFeed.feed(records, byEventDate = true).map { it.title }
        )
    }

    private fun record(
        module: ContentModule,
        title: String = module.name,
        createdAt: Long = 0L,
        mediaIds: List<String> = emptyList(),
        content: String = "",
        eventDate: Long? = null
    ): ContentRecord = ContentRecord(
        id = "test-$title",
        module = module,
        spaceId = "space-local",
        ownerId = "local-user",
        ownerName = "小明",
        visibility = Visibility.COUPLE,
        title = title,
        content = content,
        eventDate = eventDate,
        mediaIds = mediaIds,
        extra = emptyMap(),
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = 1
    )
}
