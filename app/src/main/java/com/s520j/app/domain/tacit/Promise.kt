package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentRecord

/** 彼此的承诺：进行中 / 已兑现，兑现时记录兑现时间。 */
object Promise {
    const val STATUS_KEY = "状态"
    const val FULFILLED_AT_KEY = "兑现时间"
    const val IN_PROGRESS = "进行中"
    const val FULFILLED = "已兑现"

    val statuses: List<String> = listOf(IN_PROGRESS, FULFILLED)

    fun status(record: ContentRecord): String = record.extra[STATUS_KEY] ?: IN_PROGRESS

    fun isFulfilled(record: ContentRecord): Boolean = status(record) == FULFILLED

    fun fulfilledAt(record: ContentRecord): Long? =
        record.extra[FULFILLED_AT_KEY]?.toLongOrNull()?.takeIf { isFulfilled(record) }
}
