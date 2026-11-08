package com.s520j.app.domain.us

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.domain.tacit.ImportantDay
import com.s520j.app.domain.tacit.Promise
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 当天待提醒项数量，按提醒设置的三类分别统计。 */
data class ReminderCounts(
    val importantDay: Int = 0,
    val calendar: Int = 0,
    val task: Int = 0
) {
    val total: Int get() = importantDay + calendar + task
}

/** 进入 APP 时按当天日期与提醒设置汇总待提醒项。 */
object ReminderDigest {
    fun counts(
        records: List<ContentRecord>,
        setting: ReminderSetting,
        todayEpochDay: Long
    ): ReminderCounts {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofEpochDay(todayEpochDay)
        val nowMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

        fun onToday(record: ContentRecord): Boolean {
            val millis = record.eventDate ?: return false
            return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == today
        }

        val importantDay = if (!setting.importantDay) 0 else records.count { record ->
            record.module == ContentModule.IMPORTANT_DAY && (
                if (ImportantDay.isRepeat(record)) {
                    val next = ImportantDay.nextOccurrence(record, nowMillis)
                    Instant.ofEpochMilli(next).atZone(zone).toLocalDate() == today
                } else {
                    onToday(record)
                }
                )
        }

        val calendar = if (!setting.calendar) 0 else records.count { record ->
            record.module == ContentModule.CALENDAR_EVENT && onToday(record)
        }

        val task = if (!setting.task) 0 else records.count { record ->
            record.module == ContentModule.PROMISE && !Promise.isFulfilled(record) && onToday(record)
        }

        return ReminderCounts(importantDay = importantDay, calendar = calendar, task = task)
    }
}
