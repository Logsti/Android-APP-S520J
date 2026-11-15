package com.s520j.app.data.cloud

import com.s520j.app.data.prefs.UserPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface CloudAuthResult {
    data class Success(
        val userId: String,
        val nickname: String,
        val accountId: String,
        val avatar: String? = null
    ) : CloudAuthResult
    data class Failure(val message: String, val offline: Boolean = false) : CloudAuthResult
}

/** 云端账号与会话：注册、登录、刷新 token、查询共同空间。 */
@Singleton
class CloudAuthRepository @Inject constructor(
    private val api: CloudApi,
    private val prefs: UserPreferences
) {
    private val mutex = Mutex()

    private val authPath = "api/auth"
    private val spacePath = "api/space"

    suspend fun register(accountId: String, nickname: String, password: String): CloudAuthResult =
        authenticate("auth.register", buildJsonObject {
            put("accountId", JsonPrimitive(accountId))
            put("nickname", JsonPrimitive(nickname))
            put("password", JsonPrimitive(password))
        }, accountId, nickname)

    suspend fun login(accountId: String, password: String): CloudAuthResult =
        authenticate("auth.login", buildJsonObject {
            put("accountId", JsonPrimitive(accountId))
            put("password", JsonPrimitive(password))
        }, accountId, nickname = "")

    private suspend fun authenticate(
        action: String,
        payload: JsonObject,
        accountId: String,
        nickname: String
    ): CloudAuthResult {
        val resp = try {
            api.call(authPath, null, CloudRequest(action, payload))
        } catch (e: IOException) {
            return CloudAuthResult.Failure("网络不可用，请检查后重试", offline = true)
        } catch (e: Exception) {
            return CloudAuthResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        if (resp.code != 0) return CloudAuthResult.Failure(resp.message.ifBlank { "服务暂时不可用" })

        val data = resp.data.asObject()
            ?: return CloudAuthResult.Failure("登录返回数据异常")
        val user = data["user"].asObject()
            ?: return CloudAuthResult.Failure("登录返回数据异常")
        val userId = user["id"]?.jsonPrimitive?.contentOrNull
            ?: return CloudAuthResult.Failure("登录返回数据异常")
        val access = data["accessToken"]?.jsonPrimitive?.contentOrNull
            ?: return CloudAuthResult.Failure("登录返回数据异常")
        val refresh = data["refreshToken"]?.jsonPrimitive?.contentOrNull
            ?: return CloudAuthResult.Failure("登录返回数据异常")
        val name = user["nickname"]?.jsonPrimitive?.contentOrNull ?: nickname
        val avatar = user["avatar"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        prefs.saveCloudSession(userId, access, refresh, System.currentTimeMillis() + TOKEN_TTL_MS)
        // 登录/注册响应已带自头像，直接回填到本机 prefs，保证卸载重装后头像能恢复。
        prefs.setMyAvatar(avatar)
        return CloudAuthResult.Success(userId, name, accountId, avatar)
    }

    /** 取可用 access token：过期或强制刷新时用 refresh token 换新。 */
    suspend fun ensureAccessToken(force: Boolean = false): String? = mutex.withLock {
        val session = prefs.cloudSessionSnapshot() ?: return null
        if (!force && session.expiresAt > System.currentTimeMillis() + REFRESH_AHEAD_MS) {
            return session.accessToken
        }
        val resp = try {
            api.call(authPath, null, CloudRequest("auth.refresh", buildJsonObject {
                put("refreshToken", JsonPrimitive(session.refreshToken))
            }))
        } catch (e: Exception) {
            return null
        }
        if (resp.code != 0) {
            if (resp.code == 401) prefs.clearCloudSession()
            return null
        }
        val data = resp.data.asObject() ?: return null
        val access = data["accessToken"]?.jsonPrimitive?.contentOrNull ?: return null
        val refresh = data["refreshToken"]?.jsonPrimitive?.contentOrNull ?: session.refreshToken
        prefs.saveCloudSession(session.userId, access, refresh, System.currentTimeMillis() + TOKEN_TTL_MS)
        access
    }

    /**
     * 查询当前账号在云端的共同空间 id。
     * 没有空间时返回 null，由用户在「共同空间」页主动创建；这里绝不自动创建，
     * 否则登录后就会出现一个「昵称+的小屋」，用户无法自行命名并获得绑定码。
     */
    suspend fun resolveSpaceId(): String? {
        prefs.cloudSessionSnapshot()?.spaceId?.takeIf { it.isNotBlank() }?.let { return it }
        val token = ensureAccessToken() ?: return null
        val space = fetchSpace(token) ?: return null
        prefs.setCloudSpaceId(space)
        return space
    }

    /** 同步路径必须看到 401/403，不能像界面刷新那样静默降级。 */
    suspend fun resolveSpaceIdForSync(token: String): String? {
        prefs.cloudSessionSnapshot()?.spaceId?.takeIf { it.isNotBlank() }?.let { return it }
        val resp = api.call(spacePath, bearer(token), CloudRequest("space.get"))
        if (resp.code != 0) throw CloudRequestException(resp.code, resp.message)
        val space = resp.data.asObject()?.get("space").asObject()
            ?.get("id")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        prefs.setCloudSpaceId(space)
        return space
    }

    private suspend fun fetchSpace(token: String): String? {
        val resp = try {
            api.call(spacePath, bearer(token), CloudRequest("space.get"))
        } catch (e: Exception) {
            return null
        }
        if (resp.code != 0) return null
        return resp.data.asObject()?.get("space").asObject()
            ?.get("id")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }

    /** 云端在无空间时会返回 `{"space": null}`，必须安全转换而不是直接取 jsonObject。 */
    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    suspend fun changePassword(oldPassword: String, newPassword: String): CloudAuthResult {
        val token = ensureAccessToken() ?: return CloudAuthResult.Failure("未登录云端")
        val resp = try {
            api.call(authPath, bearer(token), CloudRequest("auth.changePassword", buildJsonObject {
                put("oldPassword", JsonPrimitive(oldPassword))
                put("newPassword", JsonPrimitive(newPassword))
            }))
        } catch (e: Exception) {
            return CloudAuthResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        return if (resp.code == 0) {
            CloudAuthResult.Success(prefs.sessionSnapshot().userId, "", "")
        } else {
            CloudAuthResult.Failure(resp.message.ifBlank { "密码更新失败" })
        }
    }

    /** 上传自头像：base64 数据 URL，服务端写 users.avatar，对方在 space.get 时拉到。 */
    suspend fun updateAvatar(dataUrl: String): CloudAuthResult {
        val token = ensureAccessToken() ?: return CloudAuthResult.Failure("未登录云端")
        val resp = try {
            api.call(authPath, bearer(token), CloudRequest("users.updateAvatar", buildJsonObject {
                put("avatar", JsonPrimitive(dataUrl))
            }))
        } catch (e: Exception) {
            return CloudAuthResult.Failure("网络不可用，请检查后重试", offline = true)
        }
        return if (resp.code == 0) {
            CloudAuthResult.Success(prefs.sessionSnapshot().userId, "", "")
        } else {
            CloudAuthResult.Failure(resp.message.ifBlank { "头像上传失败" })
        }
    }

    suspend fun hasSession(): Boolean = prefs.cloudSessionSnapshot() != null

    /**
     * 拉取当前用户资料（昵称 + 头像），同步到本机 prefs。
     * 用于启动时刷新：跨设备改了昵称/头像后，本机能自动同步。
     * 失败静默返回，不影响登录态。
     */
    suspend fun fetchMe() {
        val token = ensureAccessToken() ?: return
        val resp = runCatching {
            api.call(authPath, bearer(token), CloudRequest("auth.me"))
        }.getOrNull() ?: return
        if (resp.code != 0) return
        val user = resp.data.asObject()?.get("user").asObject() ?: return
        user["nickname"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { name ->
            // 昵称同步到 session，保证各页面显示一致。
            val snap = prefs.sessionSnapshot()
            prefs.saveSession(snap.copy(nickname = name))
        }
        val avatar = user["avatar"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        prefs.setMyAvatar(avatar)
    }

    suspend fun logout() {
        val token = ensureAccessToken() ?: run {
            prefs.clearCloudSession()
            return
        }
        try {
            api.call(authPath, bearer(token), CloudRequest("auth.logout"))
        } catch (_: Exception) {
            // 离线退出也要清理本机凭证。
        }
        prefs.clearCloudSession()
    }

    private fun bearer(token: String) = "Bearer $token"

    companion object {
        /** 服务端 access token 有效期 2 小时，这里提前刷新，避免同步中途失效。 */
        private const val TOKEN_TTL_MS = 100L * 60 * 1000
        private const val REFRESH_AHEAD_MS = 5L * 60 * 1000
    }
}
