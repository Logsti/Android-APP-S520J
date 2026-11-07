package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentRecord
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 共同日历的纯计算：月份网格、事项标记与按日筛选。 */
object CalendarSupport {
    /** 完整月份网格（周日开头），前后用 null 补足整周。 */
    fun cells(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val leadingBlanks = first.dayOfWeek.value % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
        return (0 until totalCells).map { index ->
            val dayNumber = index - leadingBlanks + 1
            if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
        }
    }

    fun eventCounts(
        records: List<ContentRecord>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, Int> =
        records.mapNotNull { dateOf(it, zone) }.groupingBy { it }.eachCount()

    fun eventsOn(
        records: List<ContentRecord>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<ContentRecord> = records.filter { dateOf(it, zone) == date }

    private fun dateOf(record: ContentRecord, zone: ZoneId): LocalDate? =
        record.eventDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
}
