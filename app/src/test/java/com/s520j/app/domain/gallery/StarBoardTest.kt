package com.s520j.app.domain.gallery

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarBoardTest {

    @Test
    fun `五星自做饭菜进榜`() {
        val records = listOf(dish("番茄牛腩", 5, StarBoard.SONG))
        assertEquals(listOf("番茄牛腩"), StarBoard.list(records, StarBoard.SONG).map { it.title })
    }

    @Test
    fun `四星与无星不进榜`() {
        val records = listOf(dish("提拉米苏", 4, StarBoard.SONG), dish("炒饭", 0, StarBoard.SONG))
        assertTrue(StarBoard.list(records, StarBoard.SONG).isEmpty())
    }

    @Test
    fun `探店五星不进榜`() {
        val records = listOf(
            record(ContentModule.SHOP, "转角咖啡", 5, StarBoard.SONG)
        )
        assertFalse(StarBoard.isFiveStarDish(records.first()))
        assertTrue(StarBoard.list(records, StarBoard.SONG).isEmpty())
    }

    @Test
    fun `未填榜单默认归入松记`() {
        val records = listOf(dish("番茄牛腩", 5, null))
        assertEquals(StarBoard.SONG, StarBoard.boardOf(records.first()))
        assertEquals(1, StarBoard.count(records, StarBoard.SONG))
        assertEquals(0, StarBoard.count(records, StarBoard.JIN))
    }

    @Test
    fun `两个榜单互不串数据`() {
        val records = listOf(
            dish("松记菜", 5, StarBoard.SONG),
            dish("锦记菜", 5, StarBoard.JIN)
        )
        assertEquals(listOf("松记菜"), StarBoard.list(records, StarBoard.SONG).map { it.title })
        assertEquals(listOf("锦记菜"), StarBoard.list(records, StarBoard.JIN).map { it.title })
    }

    @Test
    fun `榜单按日期倒序`() {
        val records = listOf(
            dish("旧菜", 5, StarBoard.SONG, day = 10L),
            dish("新菜", 5, StarBoard.SONG, day = 30L)
        )
        assertEquals(listOf("新菜", "旧菜"), StarBoard.list(records, StarBoard.SONG).map { it.title })
    }

    private fun dish(title: String, stars: Int, board: String?, day: Long = 0L): ContentRecord =
        record(ContentModule.DISH, title, stars, board, day)

    private fun record(
        module: ContentModule,
        title: String,
        stars: Int,
        board: String?,
        day: Long = 0L
    ): ContentRecord = ContentRecord(
        id = "test-$title",
        module = module,
        spaceId = "space-local",
        ownerId = "local-user",
        ownerName = "小明",
        visibility = Visibility.COUPLE,
        title = title,
        content = "",
        eventDate = day,
        mediaIds = emptyList(),
        extra = buildMap {
            if (stars > 0) put(StarBoard.RATING_KEY, stars.toString())
            if (board != null) put(StarBoard.KEY, board)
        },
        createdAt = day,
        updatedAt = day,
        deletedAt = null,
        version = 1
    )
}
