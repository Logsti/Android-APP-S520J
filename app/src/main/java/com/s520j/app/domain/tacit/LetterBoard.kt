package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord

/** 情书方向始终以创建者视角存储，展示时再转换成当前查看者视角。 */
object LetterBoard {
    const val KEY_DIRECTION = "letterDirection"
    const val ALL = "ALL"
    const val TO = "TO"
    const val FROM = "FROM"

    val filters = listOf(ALL, TO, FROM)

    fun directionOf(record: ContentRecord): String = when (record.extra[KEY_DIRECTION]) {
        FROM -> FROM
        else -> TO
    }

    /** 新增采用用户选择；编辑始终保留创建时方向，避免对方编辑后翻转。 */
    fun directionForSave(existing: ContentRecord?, selected: String): String =
        existing?.let(::directionOf) ?: if (selected == FROM) FROM else TO

    fun viewerDirection(record: ContentRecord, viewerId: String): String {
        val stored = directionOf(record)
        if (record.ownerId == viewerId) return stored
        return if (stored == TO) FROM else TO
    }

    fun directionLabel(record: ContentRecord, viewerId: String, partnerNickname: String): String {
        val direction = viewerDirection(record, viewerId)
        val otherName = if (record.ownerId == viewerId) {
            partnerNickname.ifBlank { "TA" }
        } else {
            record.ownerName.ifBlank { "TA" }
        }
        return "${if (direction == TO) "写给" else "来自"} $otherName"
    }

    fun filterLabel(filter: String, partnerNickname: String): String = when (filter) {
        TO -> "写给 ${partnerNickname.ifBlank { "TA" }}"
        FROM -> "来自 ${partnerNickname.ifBlank { "TA" }}"
        else -> "全部"
    }

    fun letters(records: List<ContentRecord>, viewerId: String, filter: String): List<ContentRecord> =
        records.asSequence()
            .filter { it.module == ContentModule.LETTER }
            .filter { filter == ALL || viewerDirection(it, viewerId) == filter }
            .sortedWith(
                compareByDescending<ContentRecord> { it.eventDate ?: it.createdAt }
                    .thenByDescending { it.createdAt }
            )
            .toList()
}
