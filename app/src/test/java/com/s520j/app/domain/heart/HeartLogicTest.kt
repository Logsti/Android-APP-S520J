package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段 4 心动页纯逻辑：今日挑战、心愿状态、日历网格、碰碰你 DIY、快速记录照片规则。 */
class HeartLogicTest {

    // ---------- 今日心动挑战 ----------

    @Test
    fun `新的一天挑战从未完成状态开始`() {
        val state = ChallengeState.forDay(null, 20261107L)
        assertEquals(20261107L, state.epochDay)
        assertEquals(0, state.offset)
        assertFalse(state.selfDone)
        assertFalse(state.partnerDone)
    }

    @Test
    fun `跨天时重置偏移与双方确认状态`() {
        val old = ChallengeState(20261106L, offset = 2, selfDone = true, partnerDone = true)
        val state = ChallengeState.forDay(old, 20261107L)
        assertEquals(20261107L, state.epochDay)
        assertEquals(0, state.offset)
        assertFalse(state.selfDone)
        assertFalse(state.partnerDone)
    }

    @Test
    fun `同一天保留偏移与确认状态`() {
        val old = ChallengeState(20261107L, offset = 3, selfDone = true, partnerDone = false)
        val state = ChallengeState.forDay(old, 20261107L)
        assertEquals(old, state)
    }

    @Test
    fun `挑战文案按日期稳定轮转且不会越界`() {
        assertEquals(2, ChallengeState(20261107L, 0, false, false).promptIndex(5))
        assertEquals(3, ChallengeState(20261107L, 1, false, false).promptIndex(5))
        assertEquals(1, ChallengeState(20261107L, 4, false, false).promptIndex(5))
        assertEquals(2, ChallengeState(20261107L, 5, false, false).promptIndex(5))
        assertEquals(4, ChallengeState(-1L, 0, false, false).promptIndex(5))
    }

    @Test
    fun `换一换只推进偏移不影响确认状态`() {
        val state = ChallengeState(20261107L, 0, selfDone = true, partnerDone = false)
        assertEquals(ChallengeState(20261107L, 1, true, false), state.rotate())
    }

    @Test
    fun `双方确认相互独立且都确认后才算完成`() {
        val base = ChallengeState(20261107L, 0, false, false)
        assertFalse(base.toggleSelf().bothDone)
        val selfOnly = base.toggleSelf()
        assertTrue(selfOnly.selfDone)
        assertFalse(selfOnly.bothDone)
        val both = selfOnly.togglePartner()
        assertTrue(both.bothDone)
        assertFalse(both.toggleSelf().bothDone)
    }

    @Test
    fun `挑战状态可编码解码且非法内容返回空`() {
        val state = ChallengeState(20261107L, 2, true, false)
        assertEquals(state, ChallengeState.decode(state.encode()))
        assertNull(ChallengeState.decode(null))
        assertNull(ChallengeState.decode(""))
        assertNull(ChallengeState.decode("20261107|2|true"))
        assertNull(ChallengeState.decode("abc|2|true|false"))
        assertEquals(listOf("20261107", "2", "true", "false"), state.encode().split("|"))
    }

    // ---------- 心愿三态 ----------

    @Test
    fun `心愿默认待回应且未知状态归入待回应`() {
        assertEquals("待回应", WishBoard.statusOf(wish("无状态")))
        assertEquals("待回应", WishBoard.statusOf(wish("随便写")))
        assertEquals("已答应", WishBoard.statusOf(wish("已答应")))
        assertEquals("已实现", WishBoard.statusOf(wish("已实现")))
        assertEquals("被拒绝", WishBoard.statusOf(wish("被拒绝")))
        assertEquals(listOf("待回应", "已答应", "已实现", "被拒绝"), WishBoard.statuses)
    }

    @Test
    fun `心愿按状态分组且始终返回四个分组`() {
        val records = listOf(wish("待回应"), wish("已实现"), wish("已答应"), wish("被拒绝"), wish("已实现"))
        val grouped = WishBoard.group(records)
        assertEquals(setOf("待回应", "已答应", "已实现", "被拒绝"), grouped.keys)
        assertEquals(1, grouped["待回应"]?.size)
        assertEquals(1, grouped["已答应"]?.size)
        assertEquals(1, grouped["被拒绝"]?.size)
        assertEquals(listOf("已实现", "已实现"), grouped["已实现"]?.map { it.title })
        assertTrue(WishBoard.group(emptyList()).values.all { it.isEmpty() })
    }

    // ---------- 共同日历 ----------

    @Test
    fun `月份网格按星期补齐且包含整月日期`() {
        val november = CalendarSupport.cells(YearMonth.of(2026, 11))
        assertEquals(0, november.size % 7)
        assertEquals(LocalDate.of(2026, 11, 1), november.first())
        assertEquals(30, november.mapNotNull { it }.size)
        assertEquals(LocalDate.of(2026, 11, 30), november.mapNotNull { it }.last())

        val february = CalendarSupport.cells(YearMonth.of(2024, 2))
        assertEquals(0, february.size % 7)
        assertEquals(4, february.takeWhile { it == null }.size)
        assertEquals(29, february.mapNotNull { it }.size)
        assertEquals(LocalDate.of(2024, 2, 29), february.mapNotNull { it }.last())
    }

    @Test
    fun `同一天多条事项计数并忽略无日期记录`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val day = LocalDate.of(2026, 11, 7)
        val records = listOf(
            event("事项 A", day, zone),
            event("事项 B", day, zone),
            event("其他日", LocalDate.of(2026, 11, 9), zone),
            noDate("无日期")
        )
        val counts = CalendarSupport.eventCounts(records, zone)
        assertEquals(2, counts[day])
        assertEquals(1, counts[LocalDate.of(2026, 11, 9)])
        assertEquals(2, CalendarSupport.eventsOn(records, day, zone).size)
        assertEquals(listOf("事项 A", "事项 B"), CalendarSupport.eventsOn(records, day, zone).map { it.title })
        assertTrue(CalendarSupport.eventsOn(records, LocalDate.of(2026, 11, 8), zone).isEmpty())
    }

    // ---------- 碰碰你 DIY ----------

    @Test
    fun `默认碰碰你动作齐全`() {
        assertEquals(listOf("拍一拍", "抱抱", "亲亲", "想你"), PokeCatalog.defaults)
        assertEquals(PokeCatalog.defaults, PokeCatalog.all(emptyList()))
    }

    @Test
    fun `自定义动作名称校验`() {
        assertEquals(PokeAddResult.EMPTY, PokeCatalog.validate(emptyList(), ""))
        assertEquals(PokeAddResult.EMPTY, PokeCatalog.validate(emptyList(), "   "))
        assertEquals(PokeAddResult.TOO_LONG, PokeCatalog.validate(emptyList(), "一二三四五六七八九"))
        assertEquals(PokeAddResult.OK, PokeCatalog.validate(emptyList(), "一二三四五六七八"))
        assertEquals(PokeAddResult.DUPLICATE, PokeCatalog.validate(emptyList(), "抱抱"))
        assertEquals(PokeAddResult.DUPLICATE, PokeCatalog.validate(emptyList(), " 抱抱 "))
        assertEquals(PokeAddResult.OK, PokeCatalog.validate(listOf("转圈圈"), "摸摸头"))
    }

    @Test
    fun `新增与单个删除自定义动作`() {
        assertEquals(listOf("转圈圈"), PokeCatalog.add(emptyList(), " 转圈圈 "))
        assertEquals(listOf("转圈圈"), PokeCatalog.add(listOf("转圈圈"), "转圈圈"))
        assertEquals(listOf("转圈圈"), PokeCatalog.add(listOf("转圈圈"), "  "))
        assertEquals(emptyList<String>(), PokeCatalog.remove(listOf("转圈圈"), "转圈圈"))
        assertEquals(listOf("摸摸头"), PokeCatalog.remove(listOf("转圈圈", "摸摸头"), "转圈圈"))
        assertEquals(PokeCatalog.defaults + listOf("转圈圈", "摸摸头"), PokeCatalog.all(listOf("抱抱", "转圈圈", "摸摸头")))
    }

    @Test
    fun `自定义动作可编码持久化`() {
        val list = listOf("转圈圈", "摸摸头")
        assertEquals(list, PokeCatalog.decode(PokeCatalog.encode(list)))
        assertTrue(PokeCatalog.decode(null).isEmpty())
        assertTrue(PokeCatalog.decode("  ").isEmpty())
        assertEquals(listOf("转圈圈", "摸摸头"), PokeCatalog.decode(" 转圈圈 \n\n 摸摸头 "))
    }

    // ---------- 测试数据 ----------

    private fun wish(status: String): ContentRecord = record(
        module = ContentModule.WISH,
        title = status,
        extra = mapOf("状态" to status)
    )

    private fun event(title: String, date: LocalDate, zone: ZoneId): ContentRecord = record(
        module = ContentModule.CALENDAR_EVENT,
        title = title,
        eventDate = date.atStartOfDay(zone).toInstant().toEpochMilli()
    )

    private fun noDate(title: String): ContentRecord = record(
        module = ContentModule.CALENDAR_EVENT,
        title = title,
        eventDate = null
    )

    private fun record(
        module: ContentModule,
        title: String,
        eventDate: Long? = null,
        extra: Map<String, String> = emptyMap()
    ): ContentRecord = ContentRecord(
        id = "test-$title",
        module = module,
        spaceId = "space-local",
        ownerId = "local-user",
        ownerName = "小明",
        visibility = Visibility.COUPLE,
        title = title,
        content = "",
        eventDate = eventDate,
        mediaIds = emptyList(),
        extra = extra,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        version = 1
    )
}
