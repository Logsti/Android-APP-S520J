package com.s520j.app.data.repository

import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.local.SpaceMemberEntity
import com.s520j.app.data.prefs.SessionProfile
import com.s520j.app.data.prefs.UserPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SpaceRepository @Inject constructor(
    private val db: S520JDatabase,
    private val prefs: UserPreferences
) {
    private val spaceDao get() = db.spaceDao()

    fun observeSpace(): Flow<SpaceEntity?> = spaceDao.observeSpace()

    suspend fun currentSpace(): SpaceEntity? = spaceDao.currentSpace()

    /** 创建共同空间：创建后处于「已生成邀请」，并生成 24 小时有效的绑定码。 */
    suspend fun createSpace(name: String, intro: String, owner: SessionProfile): SpaceEntity {
        val now = System.currentTimeMillis()
        val space = SpaceEntity(
            id = "space-${UUID.randomUUID()}",
            name = name.ifBlank { "我们的空间" },
            intro = intro,
            status = "ACTIVE",
            bindState = BindState.INVITE_GENERATED.name,
            bindCode = newBindCode(),
            bindCodeExpiresAt = now + 24 * 3600_000L,
            createdBy = owner.userId,
            partnerUserId = null,
            createdAt = now,
            updatedAt = now
        )
        spaceDao.upsert(space)
        spaceDao.addMember(SpaceMemberEntity(space.id, owner.userId, "OWNER", now))
        return space
    }

    /** 重新生成绑定码：状态回到「等待对方加入」。 */
    suspend fun refreshBindCode(space: SpaceEntity): SpaceEntity {
        val now = System.currentTimeMillis()
        val updated = space.copy(
            bindCode = newBindCode(),
            bindCodeExpiresAt = now + 24 * 3600_000L,
            bindState = BindState.WAITING_PARTNER.name,
            updatedAt = now
        )
        spaceDao.upsert(updated)
        return updated
    }

    /** 输入绑定码：命中且未过期则进入「绑定申请中」。 */
    suspend fun submitBindCode(code: String, userId: String): Pair<SpaceEntity?, String?> {
        val target = spaceDao.findByBindCode(code.trim().uppercase())
            ?: return null to "绑定码不存在，请核对后重试"
        val expiresAt = target.bindCodeExpiresAt ?: 0L
        if (System.currentTimeMillis() > expiresAt) {
            val failed = target.copy(bindState = BindState.FAILED.name, updatedAt = System.currentTimeMillis())
            spaceDao.upsert(failed)
            return failed to "绑定码已过期，请让对方重新生成"
        }
        if (target.partnerUserId != null && target.partnerUserId != userId) {
            return target to "该空间已满，一个空间最多两人"
        }
        val pending = target.copy(
            bindState = BindState.REQUEST_PENDING.name,
            partnerUserId = userId,
            updatedAt = System.currentTimeMillis()
        )
        spaceDao.upsert(pending)
        spaceDao.addMember(SpaceMemberEntity(pending.id, userId, "MEMBER", System.currentTimeMillis()))
        return pending to null
    }

    /** 双方各确认一次后进入「已绑定」。 */
    suspend fun confirmBind(space: SpaceEntity, partnerNickname: String): SpaceEntity {
        val updated = space.copy(
            bindState = BindState.BOUND.name,
            bindCode = null,
            bindCodeExpiresAt = null,
            status = "ACTIVE",
            updatedAt = System.currentTimeMillis()
        )
        spaceDao.upsert(updated)
        val session = prefs.sessionSnapshot()
        prefs.saveSession(session.copy(partnerNickname = partnerNickname))
        return updated
    }

    /** 任一方发起解绑，另一方确认后进入「冻结归档」。 */
    suspend fun requestUnbind(space: SpaceEntity): SpaceEntity {
        val updated = space.copy(bindState = BindState.REQUEST_PENDING.name, updatedAt = System.currentTimeMillis())
        spaceDao.upsert(updated)
        return updated
    }

    suspend fun confirmUnbind(space: SpaceEntity): SpaceEntity {
        val updated = space.copy(
            bindState = BindState.FROZEN.name,
            status = "FROZEN",
            updatedAt = System.currentTimeMillis()
        )
        spaceDao.upsert(updated)
        return updated
    }

    /** 重新绑定解冻，历史共同内容保留。 */
    suspend fun rebind(space: SpaceEntity): SpaceEntity {
        val updated = space.copy(
            bindState = BindState.INVITE_GENERATED.name,
            status = "ACTIVE",
            bindCode = newBindCode(),
            bindCodeExpiresAt = System.currentTimeMillis() + 24 * 3600_000L,
            updatedAt = System.currentTimeMillis()
        )
        spaceDao.upsert(updated)
        return updated
    }

    fun isReadOnly(state: BindState?): Boolean = state == BindState.FROZEN

    private fun newBindCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return CharArray(6) { alphabet.random() }.concatToString()
    }
}
