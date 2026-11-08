package com.s520j.app.domain.us

/** 本机的提醒偏好：时间 + 三类提醒开关（V1 仅本机一份，不区分双方）。 */
data class ReminderSetting(
    val time: String = DEFAULT_TIME,
    val importantDay: Boolean = true,
    val calendar: Boolean = true,
    val task: Boolean = true
) {
    /** 已开启的提醒类别数量。 */
    val enabledCount: Int
        get() = listOf(importantDay, calendar, task).count { it }

    companion object {
        const val DEFAULT_TIME = "20:00"
    }
}

/** 本机提醒偏好的持久化编解码（DataStore 中存单行字符串）。 */
object ReminderSettingCodec {
    val TIME_OPTIONS: List<String> = listOf("08:00", "12:00", "20:00")

    fun encode(setting: ReminderSetting): String = listOf(
        setting.time,
        if (setting.importantDay) "1" else "0",
        if (setting.calendar) "1" else "0",
        if (setting.task) "1" else "0"
    ).joinToString("|")

    fun decode(raw: String?): ReminderSetting {
        if (raw.isNullOrBlank()) return ReminderSetting()
        val fields = raw.split("|")
        val time = fields.getOrNull(0)?.trim()?.takeIf { it in TIME_OPTIONS } ?: ReminderSetting.DEFAULT_TIME
        return ReminderSetting(
            time = time,
            importantDay = flag(fields.getOrNull(1)),
            calendar = flag(fields.getOrNull(2)),
            task = flag(fields.getOrNull(3))
        )
    }

    /** 缺失或非法开关一律按默认开启处理。 */
    private fun flag(raw: String?): Boolean = when (raw?.trim()) {
        "0" -> false
        "1" -> true
        else -> true
    }
}
