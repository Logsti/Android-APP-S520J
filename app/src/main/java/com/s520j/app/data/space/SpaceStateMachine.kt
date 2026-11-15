package com.s520j.app.data.space

import com.s520j.app.data.local.BindState

/** 待确认动作：绑定申请与解绑申请必须区分，避免语义混用。 */
enum class PendingAction {
    BIND,
    UNBIND;

    companion object {
        fun from(raw: String?): PendingAction? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * 七态绑定状态机的纯 Kotlin 规则层：不依赖 Room / Android，时间全部由参数注入以便测试。
 */
object SpaceStateMachine {

    /** 绑定码有效期：24 小时。 */
    const val BIND_CODE_TTL_MS = 24 * 3600_000L

    const val ERROR_CODE_NOT_FOUND = "绑定码不存在，请核对后重试"
    const val ERROR_CODE_EXPIRED = "绑定码已过期，请让对方重新生成"
    const val ERROR_SPACE_FULL = "该空间已满，一个空间最多两人"

    /** 有效期截止时刻即视为过期。 */
    fun isBindCodeExpired(expiresAt: Long?, now: Long): Boolean =
        expiresAt != null && now >= expiresAt

    fun afterCreate(): BindState = BindState.INVITE_GENERATED

    fun afterShare(): BindState = BindState.WAITING_PARTNER

    /** 提交绑定码：过期或空间已满进入失败态并返回原因，正常进入绑定申请中。 */
    fun afterSubmit(expired: Boolean, occupied: Boolean): Pair<BindState, String?> = when {
        expired -> BindState.FAILED to ERROR_CODE_EXPIRED
        occupied -> BindState.FAILED to ERROR_SPACE_FULL
        else -> BindState.REQUEST_PENDING to null
    }

    /** 双方各确认一次：绑定 → 已绑定，解绑 → 冻结归档。 */
    fun afterConfirm(
        action: PendingAction,
        ownerConfirmed: Boolean,
        partnerConfirmed: Boolean
    ): BindState {
        val bothConfirmed = ownerConfirmed && partnerConfirmed
        return when (action) {
            PendingAction.BIND -> if (bothConfirmed) BindState.BOUND else BindState.REQUEST_PENDING
            PendingAction.UNBIND -> if (bothConfirmed) BindState.FROZEN else BindState.REQUEST_PENDING
        }
    }

    fun afterRebind(): BindState = BindState.BOUND

    /** 冻结归档后共同内容只读；无空间（null）视为可编辑。 */
    fun canEdit(state: BindState?): Boolean = state != BindState.FROZEN

    /** 导出在任意状态都允许，冻结后仍可导出归档内容。 */
    fun canExport(state: BindState?): Boolean = true

    /** 未绑定、无空间或绑定失败时允许创建共同空间；已生成绑定码及之后的状态不允许。 */
    fun canCreate(state: BindState?): Boolean =
        state == null || state == BindState.UNBOUND || state == BindState.FAILED

    fun stateOf(raw: String?): BindState? = BindState.entries.firstOrNull { it.name == raw }
}
