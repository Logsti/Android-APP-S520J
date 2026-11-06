package com.s520j.app.data.repository

import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.local.SpaceMemberEntity
import com.s520j.app.data.prefs.SessionProfile
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.data.space.PendingAction
import com.s520j.app.data.space.SpaceStateMachine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** 提交绑定码的结果：目标空间可能不存在（此时由 UI 展示失败提示）。 */
data class BindSubmitResult(
    val space: SpaceEntity?,
    val error: String?
)

@Singleton
class SpaceRepository @Inject constructor(
    private val db: S520JDatabase,
    private val prefs: UserPreferences
) {
    private val spaceDao get() = db.spaceDao()

    fun observeSpace(): Flow<SpaceEntity?> = spaceDao.observeSpace()

    suspend fun currentSpace(): SpaceEntity? = spaceDao.currentSpace()

    /** 创建共同空间：进入「已生成邀请」，并生成 24 小时有效的绑定码。 */
    suspend fun createSpace(name: String, intro: String, owner: SessionProfile): SpaceEntity {
        val now = System.currentTimeMillis()
        val space = SpaceEntity(
            id = "space-${UUID.randomUUID()}",
            name = name.ifBlank { "我们的空间" },
            intro = intro,
            status = "ACTIVE",
            bindState = SpaceStateMachine.afterCreate().name,
            bindCode = newBindCode(),
            bindCodeExpiresAt = now + SpaceStateMachine.BIND_CODE_TTL_MS,
            createdBy = owner.userId,
            partnerUserId = null,
            createdAt = now,
            updatedAt = now
        )
        spaceDao.upsert(space)
        spaceDao.addMember(SpaceMemberEntity(space.id, owner.userId, "OWNER", now))
        return space
    }

    /** 分享绑定码：进入「等待对方加入」（仅允许从已生成邀请 / 等待对方加入调用）。 */
    suspend fun shareInvite(space: SpaceEntity, now: Long): SpaceEntity {
        val state = SpaceStateMachine.stateOf(space.bindState)
        if (state != BindState.INVITE_GENERATED && state != BindState.WAITING_PARTNER) return space
        val updated = space.copy(
            bindState = SpaceStateMachine.afterShare().name,
            updatedAt = now
        )
        spaceDao.upsert(updated)
        return updated
    }

    /** 重新生成绑定码：刷新有效期并清空待确认动作。 */
    suspend fun refreshBindCode(space: SpaceEntity, now: Long): SpaceEntity {
        val updated = space.copy(
            bindCode = newBindCode(),
            bindCodeExpiresAt = now + SpaceStateMachine.BIND_CODE_TTL_MS,
            bindState = SpaceStateMachine.afterShare().name,
            pendingAction = null,
            bindRequestedBy = null,
            unbindRequestedBy = null,
            ownerConfirmedAt = null,
            partnerConfirmedAt = null,
            updatedAt = now
        )
        spaceDao.upsert(updated)
        return updated
    }

    /** 输入对方的绑定码：命中且有效则进入「绑定申请中」，失败原因写回状态机文案。 */
    suspend fun submitBindCode(
        code: String,
        applicantId: String,
        applicantName: String,
        now: Long
    ): BindSubmitResult {
        val target = spaceDao.findByBindCode(code.trim().uppercase())
            ?: return BindSubmitResult(null, SpaceStateMachine.ERROR_CODE_NOT_FOUND)
        val expired = SpaceStateMachine.isBindCodeExpired(target.bindCodeExpiresAt, now)
        val occupied = target.partnerUserId != null && target.partnerUserId != applicantId
        if (expired || occupied) {
            val (state, error) = SpaceStateMachine.afterSubmit(expired, occupied)
            // 失败态落库，但绝不覆盖已有的 partnerUserId。
            val failed = target.copy(bindState = state.name, updatedAt = now)
            spaceDao.upsert(failed)
            return BindSubmitResult(failed, error)
        }
        val pending = target.copy(
            bindState = SpaceStateMachine.afterSubmit(expired = false, occupied = false).first.name,
            pendingAction = PendingAction.BIND.name,
            partnerUserId = applicantId,
            bindRequestedBy = applicantId,
            unbindRequestedBy = null,
            bindCode = null,
            bindCodeExpiresAt = null,
            ownerConfirmedAt = null,
            partnerConfirmedAt = null,
            updatedAt = now
        )
        spaceDao.upsert(pending)
        spaceDao.addMember(SpaceMemberEntity(pending.id, applicantId, "MEMBER", now))
        return BindSubmitResult(pending, null)
    }

    /** 绑定确认：双方各确认一次后进入「已绑定」，并记录对方昵称。 */
    suspend fun confirmBind(
        space: SpaceEntity,
        actorId: String,
        partnerNickname: String,
        now: Long
    ): SpaceEntity {
        if (PendingAction.from(space.pendingAction) != PendingAction.BIND) return space
        val isOwner = actorId == space.createdBy
        val ownerConfirmedAt = if (isOwner) now else space.ownerConfirmedAt
        val partnerConfirmedAt = if (isOwner) space.partnerConfirmedAt else now
        val state = SpaceStateMachine.afterConfirm(
            PendingAction.BIND,
            ownerConfirmedAt != null,
            partnerConfirmedAt != null
        )
        val updated = if (state == BindState.BOUND) {
            space.copy(
                bindState = state.name,
                status = "ACTIVE",
                bindCode = null,
                bindCodeExpiresAt = null,
                pendingAction = null,
                bindRequestedBy = null,
                unbindRequestedBy = null,
                ownerConfirmedAt = null,
                partnerConfirmedAt = null,
                updatedAt = now
            )
        } else {
            space.copy(
                bindState = state.name,
                ownerConfirmedAt = ownerConfirmedAt,
                partnerConfirmedAt = partnerConfirmedAt,
                updatedAt = now
            )
        }
        spaceDao.upsert(updated)
        if (state == BindState.BOUND) {
            prefs.saveSession(prefs.sessionSnapshot().copy(partnerNickname = partnerNickname))
        }
        return updated
    }

    /** 发起解绑：仅已绑定状态可发起，进入解绑申请中。 */
    suspend fun requestUnbind(space: SpaceEntity, requesterId: String, now: Long): SpaceEntity {
        if (SpaceStateMachine.stateOf(space.bindState) != BindState.BOUND) return space
        val updated = space.copy(
            bindState = BindState.REQUEST_PENDING.name,
            pendingAction = PendingAction.UNBIND.name,
            unbindRequestedBy = requesterId,
            bindRequestedBy = null,
            ownerConfirmedAt = null,
            partnerConfirmedAt = null,
            updatedAt = now
        )
        spaceDao.upsert(updated)
        return updated
    }

    /** 解绑确认：双方各确认一次后进入「冻结归档」。 */
    suspend fun confirmUnbind(space: SpaceEntity, actorId: String, now: Long): SpaceEntity {
        if (PendingAction.from(space.pendingAction) != PendingAction.UNBIND) return space
        val isOwner = actorId == space.createdBy
        val ownerConfirmedAt = if (isOwner) now else space.ownerConfirmedAt
        val partnerConfirmedAt = if (isOwner) space.partnerConfirmedAt else now
        val state = SpaceStateMachine.afterConfirm(
            PendingAction.UNBIND,
            ownerConfirmedAt != null,
            partnerConfirmedAt != null
        )
        val frozen = state == BindState.FROZEN
        val updated = space.copy(
            bindState = state.name,
            status = if (frozen) "FROZEN" else space.status,
            pendingAction = if (frozen) null else space.pendingAction,
            unbindRequestedBy = if (frozen) null else space.unbindRequestedBy,
            ownerConfirmedAt = if (frozen) null else ownerConfirmedAt,
            partnerConfirmedAt = if (frozen) null else partnerConfirmedAt,
            updatedAt = now
        )
        spaceDao.upsert(updated)
        return updated
    }

    /** 重新绑定：解冻并恢复读写，历史内容保留。 */
    suspend fun rebind(space: SpaceEntity, now: Long): SpaceEntity {
        val updated = space.copy(
            bindState = SpaceStateMachine.afterRebind().name,
            status = "ACTIVE",
            bindCode = null,
            bindCodeExpiresAt = null,
            pendingAction = null,
            bindRequestedBy = null,
            unbindRequestedBy = null,
            ownerConfirmedAt = null,
            partnerConfirmedAt = null,
            updatedAt = now
        )
        spaceDao.upsert(updated)
        return updated
    }

    /** 退出登录直接冻结（沿用原 logout 语义）。 */
    suspend fun freeze(space: SpaceEntity, now: Long): SpaceEntity {
        val updated = space.copy(
            bindState = BindState.FROZEN.name,
            status = "FROZEN",
            pendingAction = null,
            bindRequestedBy = null,
            unbindRequestedBy = null,
            ownerConfirmedAt = null,
            partnerConfirmedAt = null,
            updatedAt = now
        )
        spaceDao.upsert(updated)
        return updated
    }

    fun isReadOnly(space: SpaceEntity?): Boolean =
        !SpaceStateMachine.canEdit(space?.bindState?.let(SpaceStateMachine::stateOf))

    private fun newBindCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return CharArray(6) { alphabet.random() }.concatToString()
    }
}
