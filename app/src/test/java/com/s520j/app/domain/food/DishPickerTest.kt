package com.s520j.app.domain.food

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DishPickerTest {

    @Test
    fun `份数限制在0到4`() {
        var state = DishPicker.initialState()
        state = DishPicker.updateCount(state, DishGroup.MEAT, 9)
        assertEquals(4, state.countOf(DishGroup.MEAT))
        state = DishPicker.updateCount(state, DishGroup.MEAT, -3)
        assertEquals(0, state.countOf(DishGroup.MEAT))
    }

    @Test
    fun `四组全0时禁用随机`() {
        var state = DishPicker.initialState()
        DishGroup.order.forEach { state = DishPicker.updateCount(state, it, 0) }
        assertEquals(0, state.totalCount)
        assertNotNull(DishPicker.validation(state))
        assertFalse(DishPicker.canPick(state))
    }

    @Test
    fun `默认状态可点菜且四组各一份`() {
        val state = DishPicker.initialState()
        assertEquals(4, state.totalCount)
        assertTrue(DishPicker.canPick(state))
        assertTrue(state.preferNew)
        assertEquals(DishTaxonomy.ALL, state.subOf(DishGroup.SOUP))
    }

    @Test
    fun `调整份数或切换二级分类清空旧结果`() {
        val picked = stateWithTable()
        assertTrue(picked.hasResult)
        assertNull(DishPicker.updateCount(picked, DishGroup.MEAT, 2).table.firstOrNull())
        assertNull(DishPicker.updateSub(picked, DishGroup.VEG, "菌菇类").table.firstOrNull())
        assertEquals("菌菇类", DishPicker.updateSub(picked, DishGroup.VEG, "菌菇类").subOf(DishGroup.VEG))
        // 切换筛选不会改动份数。
        assertEquals(picked.countOf(DishGroup.MEAT), DishPicker.updateSub(picked, DishGroup.VEG, "菌菇类").countOf(DishGroup.MEAT))
    }

    @Test
    fun `请求只带非零分组与组内筛选`() {
        var state = DishPicker.initialState()
        state = DishPicker.updateCount(state, DishGroup.SOUP, 0)
        state = DishPicker.updateSub(state, DishGroup.MEAT, "牛")
        val request = DishPicker.request(state)
        assertEquals(listOf(DishGroup.MEAT, DishGroup.VEG, DishGroup.STAPLE), request.groups.map { it.group })
        assertEquals(listOf(1, 1, 1), request.groups.map { it.count })
        assertEquals("牛", request.groups.first().subCategory)
        assertEquals(DishTaxonomy.ALL, request.groups.last().subCategory)
    }

    @Test
    fun `关闭优先出新菜时不携带最近点过`() {
        val state = DishPicker.initialState().copy(preferNew = false)
        val request = DishPicker.request(state, recent = listOf("红烧肉", "番茄蛋汤"))
        assertTrue(request.recent.isEmpty())
        val enabled = DishPicker.request(state.copy(preferNew = true), recent = listOf("红烧肉"))
        assertEquals(listOf("红烧肉"), enabled.recent)
    }

    @Test
    fun `云端结果按荤素汤主食顺序落桌`() {
        val state = DishPicker.initialState()
        val result = DishPickResult(
            items = listOf(
                dish("蛋炒饭", DishGroup.STAPLE),
                dish("番茄蛋汤", DishGroup.SOUP, level2 = "汤"),
                dish("清炒油麦菜", DishGroup.VEG, level2 = "叶类蔬菜"),
                dish("红烧肉", DishGroup.MEAT, level2 = "猪")
            ),
            quotaRemaining = 42,
            degraded = true,
            note = "部分菜品来自缓存或内置"
        )
        val applied = DishPicker.applyRemote(state, result)
        assertEquals(
            listOf(DishGroup.MEAT, DishGroup.VEG, DishGroup.SOUP, DishGroup.STAPLE),
            applied.table.map { it.group }
        )
        assertEquals(42, applied.quotaRemaining)
        assertTrue(applied.degraded)
        assertEquals("部分菜品来自缓存或内置", applied.note)
        assertEquals(applied.table.size, applied.haystack.size)
    }

    @Test
    fun `候选池换菜不与桌上重复`() {
        val state = stateWithTable().copy(
            haystack = listOf(
                dish("红烧肉", DishGroup.MEAT, "猪"),
                dish("黑椒牛柳", DishGroup.MEAT, "牛"),
                dish("清炒油麦菜", DishGroup.VEG, "叶类蔬菜")
            )
        )
        val target = state.table.first { it.group == DishGroup.MEAT }
        val swapped = DishPicker.swapFromPool(state, target, Random(1))
        assertNotNull(swapped)
        val next = requireNotNull(swapped)
        assertEquals(listOf("黑椒牛柳"), next.table.filter { it.group == DishGroup.MEAT }.map { it.name })
        assertEquals(next.table.size, next.table.map { it.name }.distinct().size)
        // 候选池只有这两道荤菜，换过一次后没有更多候选。
        assertTrue(DishPicker.swapCandidates(next, next.table.first { it.group == DishGroup.MEAT }).isEmpty())
    }

    @Test
    fun `候选池换菜遵守组内二级分类`() {
        val state = stateWithTable().copy(
            subs = DishPicker.initialState().subs + (DishGroup.MEAT to "牛"),
            haystack = listOf(
                dish("红烧肉", DishGroup.MEAT, "猪"),
                dish("黑椒牛柳", DishGroup.MEAT, "牛")
            )
        )
        val target = state.table.first { it.group == DishGroup.MEAT }
        val candidates = DishPicker.swapCandidates(state, target)
        assertEquals(listOf("黑椒牛柳"), candidates.map { it.name })
    }

    @Test
    fun `补抽结果替换指定一格`() {
        val state = stateWithTable()
        val target = state.table.first()
        val swapped = DishPicker.applySwapResult(state, target, listOf(dish("新菜", target.group, target.level2)))
        assertNotNull(swapped)
        assertTrue(requireNotNull(swapped).table.any { it.name == "新菜" })
        assertFalse(requireNotNull(swapped).table.any { it.name == target.name })
        // 桌上已存在的菜不会用来替换。
        assertNull(DishPicker.applySwapResult(state, target, listOf(state.table[1])))
    }

    @Test
    fun `换一桌清空结果并避开当前菜`() {
        val state = stateWithTable()
        val next = DishPicker.newTable(state)
        assertTrue(next.table.isEmpty())
        assertTrue(next.haystack.isEmpty())
        assertTrue(next.avoid.containsAll(state.table.map { it.name }))
        val request = DishPicker.request(next)
        assertTrue(request.exclude.containsAll(state.table.map { it.name }))
    }

    @Test
    fun `离线兜底能凑一桌`() {
        var state = DishPicker.initialState()
        state = DishPicker.updateCount(state, DishGroup.MEAT, 2)
        state = DishPicker.updateCount(state, DishGroup.VEG, 2)
        state = DishPicker.updateCount(state, DishGroup.SOUP, 1)
        state = DishPicker.updateCount(state, DishGroup.STAPLE, 1)
        val table = DishPicker.fillLocal(state, BuiltinDishes.all, Random(7), offline = true)
        assertEquals(6, table.table.size)
        assertEquals(
            listOf(DishGroup.MEAT, DishGroup.MEAT, DishGroup.VEG, DishGroup.VEG, DishGroup.SOUP, DishGroup.STAPLE),
            table.table.map { it.group }
        )
        assertEquals(6, table.table.map { it.name }.distinct().size)
        assertTrue(table.table.all { it.source == DishSource.BUILTIN })
        assertTrue(table.offline)
        assertNull(table.note)
    }

    @Test
    fun `离线兜底凑不齐时不改动份数并给出说明`() {
        var state = DishPicker.initialState()
        state = DishPicker.updateCount(state, DishGroup.MEAT, 4)
        val partial = DishPicker.fillLocal(state, listOf(dish("红烧肉", DishGroup.MEAT, "猪")), Random(1))
        assertEquals(1, partial.table.size)
        assertEquals(4, partial.countOf(DishGroup.MEAT))
        assertNotNull(partial.note)

        val empty = DishPicker.fillLocal(state, emptyList(), Random(1))
        assertTrue(empty.table.isEmpty())
        assertNotNull(empty.note)
    }

    @Test
    fun `来源标记与结果标签`() {
        val live = dish("红烧肉", DishGroup.MEAT, "猪", DishSource.LIVE)
        val pool = dish("清蒸鲈鱼", DishGroup.MEAT, "鱼", DishSource.POOL)
        assertEquals("实时", live.source.label)
        assertEquals("云端池", pool.source.label)
        assertEquals("内置", DishSource.fromKey("builtin").label)
        assertEquals(DishSource.BUILTIN, DishSource.fromKey("unknown"))
        assertEquals("猪 · 荤菜 · 实时", live.tag)
    }

    private fun stateWithTable(): DishPickerState {
        var state = DishPicker.initialState()
        state = DishPicker.updateCount(state, DishGroup.MEAT, 1)
        state = DishPicker.updateCount(state, DishGroup.VEG, 1)
        val result = DishPickResult(
            items = listOf(
                dish("红烧肉", DishGroup.MEAT, "猪"),
                dish("清炒油麦菜", DishGroup.VEG, "叶类蔬菜")
            )
        )
        return DishPicker.applyRemote(state, result)
    }

    private fun dish(
        name: String,
        group: DishGroup,
        level2: String = "",
        source: DishSource = DishSource.LIVE
    ): DishCandidate = DishCandidate(
        name = name,
        level1 = "",
        level2 = level2,
        group = group,
        source = source
    )
}
