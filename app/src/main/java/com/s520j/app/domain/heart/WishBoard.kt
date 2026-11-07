package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentRecord

/** 心愿三态看板：待回应 / 已答应 / 已实现。 */
object WishBoard {
    const val PENDING = "待回应"
    const val ACCEPTED = "已答应"
    const val REALIZED = "已实现"

    val statuses: List<String> = listOf(PENDING, ACCEPTED, REALIZED)

    fun statusOf(record: ContentRecord): String =
        record.extra["状态"]?.takeIf { it in statuses } ?: PENDING

    fun group(records: List<ContentRecord>): Map<String, List<ContentRecord>> =
        statuses.associateWith { status -> records.filter { statusOf(it) == status } }

    /** 更新心愿状态（非法状态回退为待回应）。 */
    fun withStatus(record: ContentRecord, status: String): ContentRecord {
        val normalized = status.takeIf { it in statuses } ?: PENDING
        return record.copy(
            extra = record.extra + ("状态" to normalized),
            updatedAt = System.currentTimeMillis(),
            version = record.version + 1
        )
    }
}
