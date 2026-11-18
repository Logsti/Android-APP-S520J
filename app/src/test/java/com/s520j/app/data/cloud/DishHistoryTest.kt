package com.s520j.app.data.cloud

import com.s520j.app.domain.food.DishCandidate
import com.s520j.app.domain.food.DishGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DishHistoryTest {

    @Test
    fun `记录上限为1000`() {
        assertEquals(1000, DishHistory.LIMIT)
        assertEquals(100, DishHistory.RECENT_LIMIT)
    }

    @Test
    fun `recentNames按时间倒序取前100`() {
        val picked = (1..120).map { index ->
            DishHistoryEntry("菜$index", DishGroup.MEAT, "家常", index.toLong())
        }
        val history = DishHistory(picked = picked)

        val recent = history.recentNames()

        assertEquals(100, recent.size)
        assertEquals("菜120", recent.first())
        assertEquals("菜21", recent.last())
    }

    @Test
    fun `同名菜只更新时间不新增条目`() {
        val history = DishHistory(
            picked = listOf(DishHistoryEntry("番茄炒蛋", DishGroup.MEAT, "家常", at = 100L))
        )

        val once = history.withRecords(listOf(dish("番茄炒蛋", "家常")), now = 200L)
        val twice = once.withRecords(listOf(dish("番茄炒蛋", "热菜")), now = 300L)

        assertEquals(1, twice.picked.size)
        assertEquals(300L, twice.picked.first().at)
        assertEquals("热菜", twice.picked.first().level2)
        assertEquals(listOf("番茄炒蛋"), twice.recentNames())
    }

    @Test
    fun `一桌菜去重后写入且不超过上限`() {
        val full = DishHistory(
            picked = (1..DishHistory.LIMIT).map { index ->
                DishHistoryEntry("旧菜$index", DishGroup.VEG, "时蔬", index.toLong())
            }
        )

        val next = full.withRecords(
            listOf(dish("新菜", "时蔬"), dish("新菜", "时蔬")),
            now = (DishHistory.LIMIT + 1).toLong()
        )

        assertEquals(DishHistory.LIMIT, next.picked.size)
        assertEquals("新菜", next.picked.first().name)
        assertEquals("旧菜2", next.picked.last().name)
        assertTrue(next.picked.none { it.name == "旧菜1" })
    }

    private fun dish(name: String, level2: String) =
        DishCandidate(name = name, level2 = level2, group = DishGroup.MEAT)
}
