package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentRecord
import com.s520j.app.ui.util.epochMillis
import com.s520j.app.ui.util.toLocalDate
import java.time.LocalDate

/** 重要日子：年度重复时计算下一次到来的日期。 */
object ImportantDay {
    const val REPEAT_KEY = "每年重复"
    const val REPEAT_YES = "是"

    fun isRepeat(record: ContentRecord): Boolean = record.extra[REPEAT_KEY] == REPEAT_YES

    /** 下一次到来的时间戳；不重复返回原 eventDate。 */
    fun nextOccurrence(record: ContentRecord, nowMillis: Long = System.currentTimeMillis()): Long {
        val eventDate = record.eventDate ?: return 0L
        if (!isRepeat(record)) return eventDate
        return nextAnniversary(eventDate, nowMillis)
    }

    private fun nextAnniversary(eventDate: Long, nowMillis: Long): Long {
        val origin = eventDate.toLocalDate()
        val today = nowMillis.toLocalDate()
        var year = today.year
        var candidate = validAnniversary(origin, year)
        if (candidate.isBefore(today)) {
            year += 1
            candidate = validAnniversary(origin, year)
        }
        return candidate.epochMillis()
    }

    /** 取 origin 的同月日在指定年份的合法日期；闰年 2/29 在非闰年落到 2/28，并确保最终落在一个有效闰日。 */
    private fun validAnniversary(origin: LocalDate, year: Int): LocalDate {
        runCatching { LocalDate.of(year, origin.month, origin.dayOfMonth) }.getOrNull()?.let { return it }
        // 2/29 落到非闰年：找之后最近的闰年闰日。
        for (y in year..year + 8) {
            val leap = runCatching { LocalDate.of(y, origin.month, origin.dayOfMonth) }.getOrNull()
            if (leap != null) return leap
        }
        return LocalDate.of(year, 2, 28)
    }
}
