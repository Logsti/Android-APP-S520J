package com.s520j.app.domain.heart

import com.s520j.app.data.model.ContentRecord

/**
 * 心愿状态机：待回应 / 已答应 / 已实现 / 被拒绝（失效）。
 *
 * 所有状态迁移集中在这里校验，非法迁移返回原记录；
 * 「待我回应 / 待对方回应 / 失效」只是展示视角，不落库。
 */
object WishBoard {
    const val PENDING = "待回应"
    const val ACCEPTED = "已答应"
    const val REALIZED = "已实现"
    const val REJECTED = "被拒绝"

    /** 视角：待回应按提出人身份拆成两个视角，其余视角与状态同名。 */
    const val WAITING_ME = "待我回应"
    const val WAITING_PARTNER = "待对方回应"
    const val INVALID = "失效"

    const val KEY_STATUS = "状态"
    const val KEY_PROPOSER = "提出人"
    const val KEY_ACCEPTER = "答应人"

    private const val PROPOSER_ME = "我"
    private const val PROPOSER_PARTNER = "TA"

    /** 旧数据「TA」没有 userId，用占位值保证与任何真实 userId 都不相等。 */
    private const val PARTNER_PLACEHOLDER = "\u0000partner"

    /** 四个业务状态，顺序即胶囊排列顺序。 */
    val statuses: List<String> = listOf(PENDING, ACCEPTED, REALIZED, REJECTED)

    /** 心愿页五个视角。 */
    val views: List<String> = listOf(WAITING_ME, WAITING_PARTNER, ACCEPTED, REALIZED, INVALID)

    fun statusOf(record: ContentRecord): String =
        record.extra[KEY_STATUS]?.takeIf { it in statuses } ?: PENDING

    /** 按状态分组（保留给旧调用点，始终返回四个状态）。 */
    fun group(records: List<ContentRecord>): Map<String, List<ContentRecord>> =
        statuses.associateWith { status -> records.filter { statusOf(it) == status } }

    /**
     * 提出人归一化：新数据存 userId；旧数据「我」/空回退 ownerId，「TA」视为对方。
     * 返回归一化后的标识，可直接与当前 userId 比较。
     */
    fun proposerIdOf(record: ContentRecord, selfId: String): String =
        when (val raw = record.extra[KEY_PROPOSER]?.trim().orEmpty()) {
            "", PROPOSER_ME -> record.ownerId.ifBlank { selfId }
            PROPOSER_PARTNER -> PARTNER_PLACEHOLDER
            else -> raw
        }

    fun isProposedBySelf(record: ContentRecord, selfId: String): Boolean =
        selfId.isNotBlank() && proposerIdOf(record, selfId) == selfId

    /** 展示用提出人称呼：我 / 对方昵称。 */
    fun proposerLabel(record: ContentRecord, selfId: String, partnerName: String): String =
        if (isProposedBySelf(record, selfId)) "我" else partnerName.ifBlank { PROPOSER_PARTNER }

    /** 展示用答应人称呼：答应人 userId 是自己则「我」，其余一律视为对方。 */
    fun accepterLabel(record: ContentRecord, selfId: String, partnerName: String): String {
        val raw = record.extra[KEY_ACCEPTER]?.trim().orEmpty()
        return if (raw.isNotBlank() && raw == selfId) "我" else partnerName.ifBlank { PROPOSER_PARTNER }
    }

    /** 视角判定：待回应按提出人身份拆分。 */
    fun viewOf(record: ContentRecord, selfId: String): String = when (statusOf(record)) {
        PENDING -> if (isProposedBySelf(record, selfId)) WAITING_PARTNER else WAITING_ME
        ACCEPTED -> ACCEPTED
        REALIZED -> REALIZED
        else -> INVALID
    }

    /** 按视角分组，始终返回五个视角，便于 UI 直接取数量。 */
    fun groupByView(records: List<ContentRecord>, selfId: String): Map<String, List<ContentRecord>> =
        views.associateWith { view -> records.filter { viewOf(it, selfId) == view } }

    /** 默认视角：有待我回应选它，否则待对方回应，再否则已答应。 */
    fun defaultView(groups: Map<String, List<ContentRecord>>): String = when {
        groups[WAITING_ME].orEmpty().isNotEmpty() -> WAITING_ME
        groups[WAITING_PARTNER].orEmpty().isNotEmpty() -> WAITING_PARTNER
        else -> ACCEPTED
    }

    /** 只有「非提出人」才能处理「待回应」的心愿。 */
    fun canAccept(record: ContentRecord, selfId: String): Boolean =
        statusOf(record) == PENDING && !isProposedBySelf(record, selfId)

    /** 只有「已答应」才能标记实现，任意一方都可以。 */
    fun canRealize(record: ContentRecord): Boolean = statusOf(record) == ACCEPTED

    /** 答应：仅待回应且操作者不是提出人，写入答应人。 */
    fun accept(
        record: ContentRecord,
        actorId: String,
        now: Long = System.currentTimeMillis()
    ): ContentRecord {
        if (!canAccept(record, actorId)) return record
        return moved(record, ACCEPTED, now, mapOf(KEY_ACCEPTER to actorId))
    }

    /** 拒绝：仅待回应且操作者不是提出人，归入失效心愿。 */
    fun reject(
        record: ContentRecord,
        actorId: String,
        now: Long = System.currentTimeMillis()
    ): ContentRecord {
        if (!canAccept(record, actorId)) return record
        return moved(record, REJECTED, now)
    }

    /** 实现：仅已答应。 */
    fun realize(record: ContentRecord, now: Long = System.currentTimeMillis()): ContentRecord {
        if (!canRealize(record)) return record
        return moved(record, REALIZED, now)
    }

    /**
     * 状态迁移只写 extra 与 updatedAt，不改 version：
     * version 是服务端下发的乐观锁基线（baseVersion），本地自增会让基线比云端大 1，
     * 推送被判冲突后云端旧数据覆盖本地，导致刷新后状态回退。
     */
    private fun moved(
        record: ContentRecord,
        to: String,
        now: Long,
        extra: Map<String, String> = emptyMap()
    ): ContentRecord = record.copy(
        extra = record.extra + extra + (KEY_STATUS to to),
        updatedAt = now
    )

    /**
     * 历史 API：只做状态写入，不校验迁移。
     * 业务流转请改用 [accept] / [reject] / [realize]。
     */
    fun withStatus(record: ContentRecord, status: String): ContentRecord {
        val normalized = status.takeIf { it in statuses } ?: PENDING
        return record.copy(
            extra = record.extra + (KEY_STATUS to normalized),
            updatedAt = System.currentTimeMillis()
        )
    }
}
