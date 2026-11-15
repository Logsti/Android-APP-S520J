package com.s520j.app.data.sync

/** 回收站保留策略：删除满 30 天后彻底清理。 */
object RecyclePolicy {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    const val RETENTION_DAYS = 30

    fun shouldPurge(deletedAt: Long?, now: Long): Boolean =
        deletedAt != null && now - deletedAt >= RETENTION_DAYS * DAY_MS
}
