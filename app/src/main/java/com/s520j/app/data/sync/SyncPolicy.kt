package com.s520j.app.data.sync

/** 自动同步失败的持久化进度；generation 在恢复后递增，使新的失败周期可再次提醒。 */
data class SyncFailureTrack(
    val accountId: String = "",
    val consecutive: Int = 0,
    val signature: String = "",
    val alerted: Boolean = false,
    val generation: Long = 0L
)

data class SyncFailureUpdate(
    val track: SyncFailureTrack,
    val shouldAlert: Boolean
)

data class AccountAlert(
    val kind: String,
    val title: String,
    val body: String
)

/**
 * 9.3 同步可见化：自动同步节流、状态条文案、未绑定时的共同可见内容延后推送。
 * 纯函数，便于单测；不持有任何 Android 依赖。
 */
object SyncPolicy {

    /** 前台自动同步的最小间隔：20 秒。 */
    const val AUTO_SYNC_INTERVAL_MS = 20_000L

    /**
     * 是否应该发起一次同步：强制、从未同步过、或距上次尝试已满间隔。
     * 时钟回拨（now 早于上次尝试）时返回 false，避免反复触发。
     */
    fun shouldAutoSync(lastAttemptAt: Long, now: Long, force: Boolean): Boolean {
        if (force) return true
        if (lastAttemptAt <= 0L) return true
        val elapsed = now - lastAttemptAt
        if (elapsed < 0L) return false
        return elapsed >= AUTO_SYNC_INTERVAL_MS
    }

    /** 顶栏同步状态条文案；IDLE / SYNCING 不打扰用户，返回 null。 */
    fun statusBarText(phase: SyncPhase): String? = when (phase) {
        SyncPhase.OFFLINE -> "当前离线，云端同步暂停 · 点击重试"
        SyncPhase.FAILED -> "同步失败 · 点击重试"
        else -> null
    }

    /**
     * 共同可见内容在未绑定共同空间时没有 spaceId，云端会以 400 拒绝；
     * 这类 outbox 项必须延后推送（保留队列），等绑定后再上传，否则内容永久丢失。
     */
    fun shouldDeferPush(visibility: String?, spaceId: String?): Boolean =
        visibility.equals("couple", ignoreCase = true) && spaceId.isNullOrBlank()

    fun autoSyncFailed(
        current: SyncFailureTrack,
        accountId: String,
        signature: String,
        pendingUpload: Boolean
    ): SyncFailureUpdate {
        val base = if (current.accountId == accountId) current
        else SyncFailureTrack(accountId = accountId, generation = current.generation)
        val consecutive = base.consecutive + 1
        val shouldAlert = consecutive >= 3 && pendingUpload && !base.alerted
        return SyncFailureUpdate(
            track = base.copy(
                consecutive = consecutive.coerceAtMost(3),
                signature = signature,
                alerted = base.alerted || shouldAlert
            ),
            shouldAlert = shouldAlert
        )
    }

    fun syncRecovered(current: SyncFailureTrack, accountId: String): SyncFailureTrack =
        SyncFailureTrack(
            accountId = accountId,
            generation = if (current.accountId == accountId) current.generation + 1 else current.generation
        )

    fun isSpaceFrozenFailure(code: Int, message: String): Boolean =
        code == 403 && message.contains("冻结")

    fun requiredAccountAlert(code: Int, message: String): AccountAlert? {
        if (isSpaceFrozenFailure(code, message)) return null
        if (code == 401) return AccountAlert(
            kind = "auth",
            title = "登录已失效",
            body = "请重新登录账号，恢复云端同步。"
        )
        if (code == 403) return AccountAlert(
            kind = "permission",
            title = "同步权限需要检查",
            body = "当前账号无权继续同步，请检查账号与共同空间权限后重试。"
        )
        val unavailable = message.contains("账号") &&
            listOf("不存在", "不可用", "已删除", "已禁用").any(message::contains)
        if (unavailable) return AccountAlert(
            kind = "account",
            title = "账号不可用",
            body = "当前云端账号不可用，请检查账号状态或重新登录。"
        )
        return null
    }
}
