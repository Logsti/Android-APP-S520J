package com.s520j.app.data.space

import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.SpaceEntity

/**
 * 把云端空间状态映射为本地镜像（纯函数）。
 * A 端 = 创建者（云端 confirmed_by_a），B 端 = 加入者（confirmed_by_b）。
 */
object SpaceMirror {

    data class Member(val userId: String, val nickname: String, val role: String, val avatar: String? = null)

    data class BindRequest(
        val id: String,
        val fromUser: String,
        val toUser: String,
        val status: String,
        val confirmedByA: String? = null,
        val confirmedByB: String? = null
    )

    data class Remote(
        val id: String,
        val name: String,
        val intro: String,
        val status: String,
        val createdBy: String,
        val members: List<Member>,
        val codeExpiresAt: Long? = null,
        val pending: BindRequest? = null
    )

    fun merge(remote: Remote, existing: SpaceEntity?, selfId: String, now: Long): SpaceEntity {
        val partner = remote.members.firstOrNull { it.userId != selfId }?.userId
        val base = SpaceEntity(
            id = remote.id,
            name = remote.name,
            intro = remote.intro,
            status = if (remote.status == "active") "ACTIVE" else "FROZEN",
            bindState = BindState.UNBOUND.name,
            bindCode = null,
            bindCodeExpiresAt = null,
            createdBy = remote.createdBy,
            partnerUserId = partner,
            createdAt = existing?.takeIf { it.id == remote.id }?.createdAt ?: now,
            updatedAt = now
        )

        if (remote.status != "active") {
            return base.copy(bindState = BindState.FROZEN.name, status = "FROZEN")
        }

        val pending = remote.pending
        if (pending != null && pending.status == "pending") {
            return base.copy(
                bindState = BindState.REQUEST_PENDING.name,
                pendingAction = PendingAction.BIND.name,
                bindRequestedBy = pending.fromUser,
                bindRequestId = pending.id,
                partnerUserId = if (pending.fromUser == selfId) pending.toUser else pending.fromUser,
                ownerConfirmedAt = if (pending.confirmedByA != null) 1L else null,
                partnerConfirmedAt = if (pending.confirmedByB != null) 1L else null
            )
        }

        // 解绑申请：服务端用 unbind_pending 表示，本地同样进入申请中状态，但动作是解绑。
        if (pending != null && pending.status == "unbind_pending") {
            return base.copy(
                bindState = BindState.REQUEST_PENDING.name,
                pendingAction = PendingAction.UNBIND.name,
                unbindRequestedBy = pending.fromUser,
                bindRequestId = pending.id,
                partnerUserId = if (pending.fromUser == selfId) pending.toUser else pending.fromUser,
                ownerConfirmedAt = null,
                partnerConfirmedAt = null
            )
        }

        if (remote.members.size >= 2) {
            return base.copy(bindState = BindState.BOUND.name, status = "ACTIVE")
        }

        // 单人空间：保留本地已生成的绑定码，避免刷新后看不到码。
        val keepCode = existing?.takeIf { it.id == remote.id }
        val keepState = keepCode?.let { SpaceStateMachine.stateOf(it.bindState) }
        val codeExpiresAt = remote.codeExpiresAt
        val hasActiveCode = codeExpiresAt != null && codeExpiresAt > now
        val state = when {
            hasActiveCode -> BindState.INVITE_GENERATED
            keepState != null && keepState in SOLO_STATES -> keepState
            else -> BindState.UNBOUND
        }
        return base.copy(
            bindState = state.name,
            bindCode = if (hasActiveCode || state != BindState.UNBOUND) keepCode?.bindCode else null,
            bindCodeExpiresAt = if (hasActiveCode) codeExpiresAt else keepCode?.bindCodeExpiresAt,
            pendingAction = null,
            bindRequestId = null
        )
    }

    private val SOLO_STATES = setOf(
        BindState.UNBOUND,
        BindState.INVITE_GENERATED,
        BindState.WAITING_PARTNER,
        BindState.FAILED
    )
}
