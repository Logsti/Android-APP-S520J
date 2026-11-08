package com.s520j.app.domain.us

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import com.s520j.app.domain.tacit.Promise
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderDigestTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 8)
    private val todayEpochDay = today.toEpochDay()
    private val todayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `汇总当天重要日子日历事项与待兑现承诺`() {
        val counts = ReminderDigest.counts(
            records = listOf(
                record(ContentModule.IMPORTANT_DAY, todayMillis),
                record(ContentModule.CALENDAR_EVENT, todayMillis),
                record(ContentModule.PROMISE, todayMillis)
            ),
            setting = ReminderSetting(),
            todayEpochDay = todayEpochDay
        )
        assertEquals(1, counts.importantDay)
        assertEquals(1, counts.calendar)
        assertEquals(1, counts.task)
        assertEquals(3, counts.total)
    }

    @Test
    fun `年度重复的重要日子在当天也计入`() {
        val origin = LocalDate.of(2024, 11, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        val counts = ReminderDigest.counts(
            records = listOf(record(ContentModule.IMPORTANT_DAY, origin, mapOf("每年重复" to "是"))),
            setting = ReminderSetting(),
            todayEpochDay = todayEpochDay
        )
        assertEquals(1, counts.importantDay)
    }

    @Test
    fun `已兑现承诺不计入任务提醒`() {
        val counts = ReminderDigest.counts(
            records = listOf(
                record(
                    ContentModule.PROMISE,
                    todayMillis,
                    mapOf(Promise.STATUS_KEY to Promise.FULFILLED)
                )
            ),
            setting = ReminderSetting(),
            todayEpochDay = todayEpochDay
        )
        assertEquals(0, counts.task)
    }

    @Test
    fun `非今日事项不计入`() {
        val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val counts = ReminderDigest.counts(
            records = listOf(
                record(ContentModule.IMPORTANT_DAY, tomorrow),
                record(ContentModule.CALENDAR_EVENT, tomorrow),
                record(ContentModule.PROMISE, tomorrow)
            ),
            setting = ReminderSetting(),
            todayEpochDay = todayEpochDay
        )
        assertEquals(0, counts.total)
    }

    @Test
    fun `关闭某一类开关后该类不再计入`() {
        val counts = ReminderDigest.counts(
            records = listOf(
                record(ContentModule.IMPORTANT_DAY, todayMillis),
                record(ContentModule.CALENDAR_EVENT, todayMillis),
                record(ContentModule.PROMISE, todayMillis)
            ),
            setting = ReminderSetting(importantDay = false, calendar = false, task = false),
            todayEpochDay = todayEpochDay
        )
        assertEquals(0, counts.total)
    }

    private fun record(
        module: ContentModule,
        eventDate: Long?,
        extra: Map<String, String> = emptyMap()
    ): ContentRecord = ContentRecord(
        id = "r-${module.name}-$eventDate",
        module = module,
        spaceId = "space-local",
        ownerId = "u",
        ownerName = "我",
        visibility = Visibility.COUPLE,
        title = module.label,
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
