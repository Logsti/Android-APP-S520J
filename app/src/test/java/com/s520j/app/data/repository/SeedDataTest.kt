package com.s520j.app.data.repository

import com.s520j.app.data.model.ContentModule
import com.s520j.app.domain.heart.WishBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    private val now = 1_760_000_000_000L
    private val day = 86_400_000L
    private val selfId = "user-1"
    private val nickname = "小满"
    private val seeds = SeedData.records(now, day, selfId, nickname)

    @Test
    fun `新账号种子不含本人提出且待回应的心愿`() {
        val unanswerable = seeds.filter { record ->
            record.module == ContentModule.WISH &&
                WishBoard.statusOf(record) == WishBoard.PENDING &&
                WishBoard.isProposedBySelf(record, selfId)
        }

        assertTrue("不应再生成我方提出、等待对方回应的演示心愿", unanswerable.isEmpty())
    }

    @Test
    fun `对方提出的演示心愿保留且可操作`() {
        val wishes = seeds.filter { it.module == ContentModule.WISH }

        assertEquals(1, wishes.size)
        val wish = wishes.single()
        assertFalse(WishBoard.isProposedBySelf(wish, selfId))
        assertEquals("学会做提拉米苏", wish.title)
        assertEquals(WishBoard.ACCEPTED, WishBoard.statusOf(wish))
    }

    @Test
    fun `其余演示数据仍然保留`() {
        val countByModule = seeds.groupingBy { it.module }.eachCount()

        assertEquals(2, countByModule[ContentModule.CALENDAR_EVENT] ?: 0)
        assertEquals(3, countByModule[ContentModule.PHOTO] ?: 0)
        assertEquals(3, countByModule[ContentModule.DISH] ?: 0)
        assertEquals(3, countByModule[ContentModule.ROMANTIC_MOMENT] ?: 0)
        assertEquals(2, countByModule[ContentModule.COLLECTED_MOMENT] ?: 0)
        assertEquals(23, seeds.size)
    }
}
