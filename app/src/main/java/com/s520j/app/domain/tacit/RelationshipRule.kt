package com.s520j.app.domain.tacit

import com.s520j.app.data.model.ContentRecord

/** 相处之道：需双方确认后才标记「共同认可」。 */
object RelationshipRule {
    const val KEY = "确认"
    const val ACKNOWLEDGED = "双方已确认"
    const val PENDING = "等待对方确认"

    const val STATUS_ACK = "共同认可"
    const val STATUS_PENDING = "待对方确认"

    fun isAcknowledged(record: ContentRecord): Boolean = record.extra[KEY] == ACKNOWLEDGED

    fun status(record: ContentRecord): String = if (isAcknowledged(record)) STATUS_ACK else STATUS_PENDING
}
