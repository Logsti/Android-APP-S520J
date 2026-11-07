package com.s520j.app.domain.heart

/** 按当前时段生成问候前缀。 */
object TimeGreeting {
    fun prefix(hour: Int): String {
        require(hour in 0..23) { "小时必须在 0..23 之间" }
        return when (hour) {
            in 5..8 -> "早上好"
            in 9..11 -> "上午好"
            in 12..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..22 -> "晚上好"
            else -> "夜深了"
        }
    }

    fun line(hour: Int, nickname: String, partner: String): String =
        "${prefix(hour)}，$nickname & $partner"
}
