package com.s520j.app.data.cloud

import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.data.repository.SpaceRepository
import com.s520j.app.data.space.SpaceMirror
import com.s520j.app.data.space.SpaceStateMachine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface CloudSpaceResult<out T> {
    data class Success<T>(val value: T) : CloudSpaceResult<T>
    data class Failure(val message: String, val offline: Boolean = false) : CloudSpaceResult<Nothing>
}

/** 跨设备的共同空间绑定：生成绑定码 / 加入 / 确认，全部以云端为准并镜像到本地。 */
@Singleton
class CloudSpaceRepository @Inject constructor(
    private val api: CloudApi,
    private val prefs: UserPreferences,
    private val cloudAuth: CloudAuthRepository,
    private val db: S520JDatabase,
    private val spaceRepository: SpaceRepository
) {
    private val path = "api/space"

    /** 打开「共同空间」页时拉取云端状态，处理对方确认、绑定成功、冻结等变化。 */
    suspend fun refreshLocal(): Boolean {
        return try {
            val token = cloudAuth.ensureAccessToken() ?: return false
            val resp = api.call(path, bearer(token), CloudRequest("space.get"))
            if (resp.code != 0) return false
            val data = resp.data.asObject() ?: return false
            val spaceObj = data["space"] as? JsonObject
            if (spaceObj == null) {
                // 云端没有空间：通常说明账号还没创建空间，清掉本地镜像回到「未绑定」态。
                // 例外是「我刚输入对方绑定码」：加入时旧空间已被云端归档，在双方确认前
                // 云端查不到空间，此时必须保留本地镜像，否则会丢掉待确认状态。
                val awaitingPartner = spaceRepository.currentSpace()
                    ?.let { SpaceStateMachine.stateOf(it.bindState) } == BindState.REQUEST_PENDING
                if (!awaitingPartner) {
                    db.spaceDao().clear()
                    prefs.setCloudSpaceId(null)
                }
                return true
            }
            val remote = parseRemote(spaceObj, data)
            val now = System.currentTimeMillis()
            var merged = SpaceMirror.merge(remote, spaceRepository.currentSpace(), selfId(), now)
            // 旧版本地生成的绑定码在云端并不存在：进入页面时自动换成真实云端码，避免对方输入必失败。
            if (isSolo(remote) && showsInviteCode(merged) && remote.codeExpiresAt?.let { it > now } != true) {
                when (val code = generateCode(token)) {
                    is CloudSpaceResult.Success -> merged = merged.copy(
                        bindCode = code.value.first,
                        bindCodeExpiresAt = code.value.second,
                        bindState = BindState.INVITE_GENERATED.name,
                        updatedAt = now
                    )
                    is CloudSpaceResult.Failure -> Unit
                }
            }
            saveMirror(merged)
            prefs.setCloudSpaceId(merged.id)
            // 绑定后把对方昵称与头像同步到 prefs，供「我们」页资料卡显示。
            remote.members.firstOrNull { it.userId != selfId() }?.let { partner ->
                prefs.setPartnerNickname(partner.nickname)
                prefs.setPartnerAvatar(partner.avatar)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isSolo(remote: SpaceMirror.Remote): Boolean =
        remote.status == "active" && remote.members.size < 2 && remote.pending == null

    private fun showsInviteCode(entity: SpaceEntity): Boolean =
        entity.bindState == BindState.INVITE_GENERATED.name ||
            entity.bindState == BindState.WAITING_PARTNER.name

    /** 创建共同空间：云端没有空间就新建，已有单人空间就改名，随后生成绑定码。 */
    suspend fun create(name: String, intro: String): CloudSpaceResult<SpaceEntity> =
        guarded { doCreate(name, intro) }

    private suspend fun doCreate(name: String, intro: String): CloudSpaceResult<SpaceEntity> {
        val token = cloudAuth.ensureAccessToken()
            ?: return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        val spaceName = name.ifBlank { "我们的空间" }
        val existingId = cloudAuth.resolveSpaceId()
        val spaceId = when (val written = if (existingId == null) {
            writeSpace(token, "space.create", spaceName, intro)
        } else {
            writeSpace(token, "space.update", spaceName, intro)
        }) {
            is CloudSpaceResult.Failure -> return written
            is CloudSpaceResult.Success -> written.value
        }

        val code = when (val result = generateCode(token)) {
            is CloudSpaceResult.Failure -> return result
            is CloudSpaceResult.Success -> result.value
        }
        val remote = fetchRemote(token) ?: return CloudSpaceResult.Failure("空间状态获取失败，请稍后重试")
        val now = System.currentTimeMillis()
        val merged = SpaceMirror.merge(remote, null, selfId(), now).copy(
            id = spaceId,
            bindCode = code.first,
            bindCodeExpiresAt = code.second,
            bindState = BindState.INVITE_GENERATED.name,
            updatedAt = now
        )
        saveMirror(merged)
        prefs.setCloudSpaceId(spaceId)
        return CloudSpaceResult.Success(merged)
    }

    /** `space.create` 与 `space.update` 共用：写入名称与简介，返回空间 id。 */
    private suspend fun writeSpace(
        token: String,
        action: String,
        name: String,
        intro: String
    ): CloudSpaceResult<String> {
        val resp = try {
            api.call(path, bearer(token), CloudRequest(action, buildJsonObject {
                put("name", JsonPrimitive(name))
                put("intro", JsonPrimitive(intro))
            }))
        } catch (e: Exception) {
            return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        if (resp.code != 0) return CloudSpaceResult.Failure(resp.message.ifBlank { "空间创建失败" })
        val id = resp.data.asObject()?.get("space").asObject()
            ?.get("id")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return CloudSpaceResult.Failure("空间创建失败")
        return CloudSpaceResult.Success(id)
    }

    /** 修改共同空间名称 / 简介：云端成功后才更新本地镜像，离线保存不落本地。 */
    suspend fun updateInfo(name: String, intro: String): CloudSpaceResult<SpaceEntity> = guarded {
        doUpdateInfo(name, intro)
    }

    private suspend fun doUpdateInfo(name: String, intro: String): CloudSpaceResult<SpaceEntity> {
        val token = cloudAuth.ensureAccessToken()
            ?: return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        val spaceName = name.trim()
        val spaceIntro = intro.trim()
        val resp = try {
            api.call(path, bearer(token), CloudRequest("space.update", buildJsonObject {
                put("name", JsonPrimitive(spaceName))
                put("intro", JsonPrimitive(spaceIntro))
            }))
        } catch (e: Exception) {
            return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        if (resp.code != 0) return CloudSpaceResult.Failure(resp.message.ifBlank { "保存失败，请重试" })
        val current = spaceRepository.currentSpace()
            ?: return CloudSpaceResult.Failure("请先创建共同空间")
        val now = System.currentTimeMillis()
        val updated = current.copy(name = spaceName, intro = spaceIntro, updatedAt = now)
        saveMirror(updated)
        return CloudSpaceResult.Success(updated)
    }

    /** 刷新绑定码：旧码由云端作废，只展示新码。 */
    suspend fun refreshCode(): CloudSpaceResult<SpaceEntity> = guarded { doRefreshCode() }

    private suspend fun doRefreshCode(): CloudSpaceResult<SpaceEntity> {
        val current = spaceRepository.currentSpace()
            ?: return CloudSpaceResult.Failure("请先创建共同空间")
        val token = cloudAuth.ensureAccessToken()
            ?: return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        return when (val code = generateCode(token)) {
            is CloudSpaceResult.Failure -> code
            is CloudSpaceResult.Success -> {
                val now = System.currentTimeMillis()
                val updated = current.copy(
                    bindCode = code.value.first,
                    bindCodeExpiresAt = code.value.second,
                    bindState = BindState.INVITE_GENERATED.name,
                    pendingAction = null,
                    bindRequestedBy = null,
                    bindRequestId = null,
                    ownerConfirmedAt = null,
                    partnerConfirmedAt = null,
                    updatedAt = now
                )
                saveMirror(updated)
                CloudSpaceResult.Success(updated)
            }
        }
    }

    /** 输入对方绑定码：云端创建绑定申请，本地进入「绑定申请中」。 */
    suspend fun join(code: String): CloudSpaceResult<SpaceEntity> = guarded { doJoin(code) }

    private suspend fun doJoin(code: String): CloudSpaceResult<SpaceEntity> {
        val token = cloudAuth.ensureAccessToken()
            ?: return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        val resp = try {
            api.call(path, bearer(token), CloudRequest("space.joinByCode", buildJsonObject {
                put("code", JsonPrimitive(code.trim().uppercase()))
            }))
        } catch (e: Exception) {
            return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        if (resp.code != 0) return CloudSpaceResult.Failure(resp.message.ifBlank { "绑定失败，请重试" })
        val data = resp.data.asObject() ?: return CloudSpaceResult.Failure("绑定返回数据异常")
        val requestObj = data["request"] as? JsonObject
            ?: return CloudSpaceResult.Failure("绑定返回数据异常")
        val spaceObj = data["space"] as? JsonObject
        val self = selfId()
        val request = SpaceMirror.BindRequest(
            id = requestObj.str("id").orEmpty(),
            fromUser = requestObj.str("fromUser").orEmpty(),
            toUser = requestObj.str("toUser").orEmpty(),
            status = requestObj.str("status") ?: "pending"
        )
        if (request.id.isBlank()) return CloudSpaceResult.Failure("绑定返回数据异常")
        val partner = if (request.fromUser == self) request.toUser else request.fromUser

        val now = System.currentTimeMillis()
        val remote = SpaceMirror.Remote(
            id = spaceObj?.str("id") ?: request.id,
            name = spaceObj?.str("name") ?: "共同空间",
            intro = spaceObj?.str("intro").orEmpty(),
            status = spaceObj?.str("status") ?: "active",
            createdBy = spaceObj?.str("createdBy") ?: partner,
            members = emptyList(),
            pending = request
        )
        val merged = SpaceMirror.merge(remote, spaceRepository.currentSpace(), self, now)
            .copy(partnerUserId = partner, bindRequestId = request.id)
        saveMirror(merged)
        prefs.setCloudSpaceId(merged.id)
        return CloudSpaceResult.Success(merged)
    }

    /** 我方确认绑定；返回是否双方都已确认。 */
    suspend fun confirmBind(requestId: String): CloudSpaceResult<Boolean> = guarded { doConfirmBind(requestId) }

    private suspend fun doConfirmBind(requestId: String): CloudSpaceResult<Boolean> {
        val token = cloudAuth.ensureAccessToken()
            ?: return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        val resp = try {
            api.call(path, bearer(token), CloudRequest("space.confirmBind", buildJsonObject {
                put("requestId", JsonPrimitive(requestId))
            }))
        } catch (e: Exception) {
            return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        if (resp.code != 0) return CloudSpaceResult.Failure(resp.message.ifBlank { "确认失败，请重试" })
        val bound = resp.data.asObject()?.get("bound")?.jsonPrimitive?.contentOrNull == "true"
        refreshLocal()
        if (!bound) markSelfConfirmed()
        return CloudSpaceResult.Success(bound)
    }

    /**
     * 我方已确认、对方还没确认时，云端仍然查不到请求（加入方此时暂无空间），
     * 先把本机标记为「我方已确认」，否则按钮看起来像没生效。
     */
    private suspend fun markSelfConfirmed() {
        val current = spaceRepository.currentSpace() ?: return
        if (SpaceStateMachine.stateOf(current.bindState) != BindState.REQUEST_PENDING) return
        val updated = if (current.createdBy == selfId()) {
            if (current.ownerConfirmedAt != null) return
            current.copy(ownerConfirmedAt = 1L)
        } else {
            if (current.partnerConfirmedAt != null) return
            current.copy(partnerConfirmedAt = 1L)
        }
        db.spaceDao().upsert(updated)
    }

    /** 发起解绑申请：云端记录一条 unbind_pending，随后刷新本地状态。 */
    suspend fun requestUnbind(): CloudSpaceResult<Unit> = guarded {
        unbindAction("space.unbindRequest", null, "解绑申请发送失败，请重试")
    }

    /** 同意解绑：空间随即在云端冻结。 */
    suspend fun confirmUnbind(requestId: String): CloudSpaceResult<Unit> = guarded {
        unbindAction("space.confirmUnbind", requestId, "确认解绑失败，请重试")
    }

    /** 拒绝解绑：申请作废，空间保持已绑定。 */
    suspend fun rejectUnbind(requestId: String): CloudSpaceResult<Unit> = guarded {
        unbindAction("space.rejectUnbind", requestId, "拒绝解绑失败，请重试")
    }

    /** 撤回自己发起的解绑申请。 */
    suspend fun cancelUnbind(requestId: String): CloudSpaceResult<Unit> = guarded {
        unbindAction("space.cancelUnbind", requestId, "撤回解绑失败，请重试")
    }

    private suspend fun unbindAction(
        action: String,
        requestId: String?,
        failureMessage: String
    ): CloudSpaceResult<Unit> {
        val token = cloudAuth.ensureAccessToken()
            ?: return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        val resp = try {
            api.call(path, bearer(token), CloudRequest(action, buildJsonObject {
                if (requestId != null) put("requestId", JsonPrimitive(requestId))
            }))
        } catch (e: Exception) {
            return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        if (resp.code != 0) return CloudSpaceResult.Failure(resp.message.ifBlank { failureMessage })
        refreshLocal()
        return CloudSpaceResult.Success(Unit)
    }

    // ------------------------------------------------------------ helpers

    private suspend fun generateCode(token: String): CloudSpaceResult<Pair<String, Long>> {
        val resp = try {
            api.call(path, bearer(token), CloudRequest("space.generateBindCode"))
        } catch (e: Exception) {
            return CloudSpaceResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        if (resp.code != 0) return CloudSpaceResult.Failure(resp.message.ifBlank { "绑定码生成失败" })
        val data = resp.data.asObject() ?: return CloudSpaceResult.Failure("绑定码生成失败")
        val code = data.str("code") ?: return CloudSpaceResult.Failure("绑定码生成失败")
        val expires = data["expiresAt"]?.jsonPrimitive?.longOrNull ?: 0L
        return CloudSpaceResult.Success(code to expires)
    }

    private suspend fun fetchRemote(token: String): SpaceMirror.Remote? {
        val resp = try {
            api.call(path, bearer(token), CloudRequest("space.get"))
        } catch (e: Exception) {
            return null
        }
        if (resp.code != 0) return null
        val data = resp.data.asObject() ?: return null
        val spaceObj = data["space"] as? JsonObject ?: return null
        return parseRemote(spaceObj, data)
    }

    private fun parseRemote(spaceObj: JsonObject, data: JsonObject): SpaceMirror.Remote {
        val members = (data["members"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .map {
                SpaceMirror.Member(
                    userId = it.str("userId").orEmpty(),
                    nickname = it.str("nickname").orEmpty(),
                    role = it.str("role") ?: "member",
                    avatar = it.str("avatar")
                )
            }
            .filter { it.userId.isNotBlank() }
        val pending = (data["pendingRequest"] as? JsonObject)?.let {
            SpaceMirror.BindRequest(
                id = it.str("id").orEmpty(),
                fromUser = it.str("fromUser").orEmpty(),
                toUser = it.str("toUser").orEmpty(),
                status = it.str("status") ?: "pending",
                confirmedByA = it.str("confirmedByA"),
                confirmedByB = it.str("confirmedByB")
            )
        }?.takeIf { it.id.isNotBlank() }
        return SpaceMirror.Remote(
            id = spaceObj.str("id").orEmpty(),
            name = spaceObj.str("name") ?: "共同空间",
            intro = spaceObj.str("intro").orEmpty(),
            status = spaceObj.str("status") ?: "active",
            createdBy = spaceObj.str("createdBy").orEmpty(),
            members = members,
            codeExpiresAt = data["bindCodeExpiresAt"]?.jsonPrimitive?.longOrNull,
            pending = pending
        )
    }

    private suspend fun selfId(): String =
        prefs.cloudSessionSnapshot()?.userId ?: prefs.sessionSnapshot().userId

    /** 本地只保留一份空间镜像：云端 id 变化（例如加入对方空间）时清掉旧记录。 */
    private suspend fun saveMirror(entity: SpaceEntity) {
        val existing = spaceRepository.currentSpace()
        if (existing != null && existing.id != entity.id) db.spaceDao().clear()
        db.spaceDao().upsert(entity)
    }

    private fun bearer(token: String) = "Bearer $token"

    /**
     * 统一异常守卫：网络、数据库、解析任何一环抛出异常都转为可见失败。
     * 这些方法在 viewModelScope 中调用，异常一旦逃逸会直接导致 APP 闪退（实机已复现）。
     */
    private suspend fun <T> guarded(block: suspend () -> CloudSpaceResult<T>): CloudSpaceResult<T> =
        try {
            block()
        } catch (e: Exception) {
            CloudSpaceResult.Failure(
                e.message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试",
                offline = e is IOException
            )
        }

    /** 只有 JSON 对象才可取字段，避免非法负载抛 IllegalArgumentException。 */
    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
