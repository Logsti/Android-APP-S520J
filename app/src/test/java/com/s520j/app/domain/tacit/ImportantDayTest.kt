package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import com.s520j.app.ui.util.epochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportantDayTest {

    private val origin = LocalDate.of(2024, 2, 29).epochMillis() // 闰年生日
    private val now = LocalDate.of(2026, 11, 7).epochMillis()

    @Test
    fun `不重复时直接返回原日期`() {
        assertEquals(origin, ImportantDay.nextOccurrence(day(repeat = false), now))
        assertFalse(ImportantDay.isRepeat(day(repeat = false)))
    }

    @Test
    fun `年度重复取今年或明年的同月日`() {
        // 2024-02-29 重复，2026 年不是闰年，下次为 2028-02-29
        val next = ImportantDay.nextOccurrence(day(repeat = true), now)
        assertEquals(LocalDate.of(2028, 2, 29), java.time.Instant.ofEpochMilli(next)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }

    @Test
    fun `年度重复且今年日期未过时取今年`() {
        val future = LocalDate.of(2026, 12, 25).epochMillis()
        val next = ImportantDay.nextOccurrence(day(repeat = true, eventDate = future), now)
        assertEquals(LocalDate.of(2026, 12, 25), java.time.Instant.ofEpochMilli(next)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }

    @Test
    fun `年度重复且今年日期已过时取明年`() {
        val past = LocalDate.of(2026, 1, 1).epochMillis()
        val next = ImportantDay.nextOccurrence(day(repeat = true, eventDate = past), now)
        assertEquals(LocalDate.of(2027, 1, 1), java.time.Instant.ofEpochMilli(next)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }

    private fun day(repeat: Boolean, eventDate: Long = origin): ContentRecord = ContentRecord(
        id = "d-$repeat",
        module = ContentModule.IMPORTANT_DAY,
        spaceId = "space-local",
        ownerId = "u",
        ownerName = "我",
        visibility = Visibility.COUPLE,
        title = "生日",
        content = "",
        eventDate = eventDate,
        mediaIds = emptyList(),
        extra = if (repeat) mapOf("每年重复" to "是") else emptyMap(),
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        version = 1
    )
}
