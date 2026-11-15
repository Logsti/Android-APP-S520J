package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段 4 反馈修复的纯逻辑：时段问候、照片墙聚合、心愿状态更新。 */
class CrossPageSyncTest {

    // ---------- 时段问候 ----------

    @Test
    fun `问候前缀按时段正确切换`() {
        assertEquals("早上好", TimeGreeting.prefix(5))
        assertEquals("早上好", TimeGreeting.prefix(8))
        assertEquals("上午好", TimeGreeting.prefix(9))
        assertEquals("上午好", TimeGreeting.prefix(11))
        assertEquals("中午好", TimeGreeting.prefix(12))
        assertEquals("中午好", TimeGreeting.prefix(13))
        assertEquals("下午好", TimeGreeting.prefix(14))
        assertEquals("下午好", TimeGreeting.prefix(17))
        assertEquals("晚上好", TimeGreeting.prefix(18))
        assertEquals("晚上好", TimeGreeting.prefix(22))
        assertEquals("夜深了", TimeGreeting.prefix(23))
        assertEquals("夜深了", TimeGreeting.prefix(0))
        assertEquals("夜深了", TimeGreeting.prefix(4))
    }

    @Test
    fun `问候整行包含双方昵称`() {
        assertEquals("晚上好，许愿树 & 小锦鲤", TimeGreeting.line(20, "许愿树", "小锦鲤"))
    }

    // ---------- 心愿状态更新 ----------

    @Test
    fun `标记实现会更新状态且不改版本`() {
        val wish = record(ContentModule.WISH, "去看海", extra = mapOf("状态" to "已答应", "提出人" to "小明"), version = 2)
        val updated = WishBoard.withStatus(wish, "已实现")
        assertEquals("已实现", updated.extra["状态"])
        assertEquals("小明", updated.extra["提出人"])
        // 本地不改 version：保持服务端下发的乐观锁基线，否则推送会因基线不符被回滚。
        assertEquals(2, updated.version)
    }

    @Test
    fun `未知状态回退为待回应`() {
        val wish = record(ContentModule.WISH, "去看海", extra = mapOf("状态" to "已答应"))
        assertEquals("待回应", WishBoard.withStatus(wish, "随便写").extra["状态"])
    }

    private fun record(
        module: ContentModule,
        title: String,
        createdAt: Long = 0L,
        mediaIds: List<String> = emptyList(),
        extra: Map<String, String> = emptyMap(),
        version: Int = 1
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
        extra = extra,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        version = version
    )
}
