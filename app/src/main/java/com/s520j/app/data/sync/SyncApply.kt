package com.s520j.app.data.sync

/** 同步合并规则（纯函数，便于单测）。 */
object SyncApply {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 本地存在待推送操作时，远端版本不得覆盖本机，避免用户刚写的内容被回滚。
     * 待推送操作成功后才允许拉取覆盖。
     */
    fun shouldApplyRemote(hasPending: Boolean): Boolean = !hasPending

    /** 冲突快照保留 30 天，与回收站一致。 */
    fun conflictExpireAt(now: Long): Long = now + 30 * DAY_MS
}
