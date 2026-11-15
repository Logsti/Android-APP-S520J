package com.s520j.app.data.couple

import com.s520j.app.data.cloud.CloudApi
import com.s520j.app.data.cloud.CloudAuthRepository
import com.s520j.app.data.cloud.CloudRequest
import com.s520j.app.data.cloud.CloudResponse
import com.s520j.app.data.prefs.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 云端每日挑战状态：selfDone / partnerDone 由服务端按成员身份判定。 */
data class CloudChallenge(
    val day: Long,
    val selfDone: Boolean,
    val partnerDone: Boolean,
    val doneAt: Long?
)

/** 双人事件（碰一碰 / 挑战达成）。 */
data class CoupleEvent(
    val id: String,
    val type: String,
    val body: String,
    val fromName: String,
    val createdAt: Long
) {
    companion object {
        const val TYPE_POKE = "poke"
        const val TYPE_CHALLENGE_DONE = "challenge_done"
    }
}

/**
 * 共同生活相关的云端能力：每日挑战与双人事件。
 * 未绑定 / 未登录 / 离线时一律返回 null 或空列表，调用方降级为本地能力。
 */
@Singleton
class CoupleLiveRepository @Inject constructor(
    private val api: CloudApi,
    private val cloudAuth: CloudAuthRepository,
    private val prefs: UserPreferences
) {
    private val path = "api/space"

    suspend fun getChallenge(day: Long): CloudChallenge? = call("challenge.get", dayParams(day))
        ?.let { parseChallenge(it) }

    suspend fun markChallenge(day: Long): CloudChallenge? = call("challenge.mark", dayParams(day))
        ?.let { parseChallenge(it) }

    suspend fun sendPoke(text: String): Boolean {
        val data = call("event.send", buildJsonObject {
            put("type", JsonPrimitive(CoupleEvent.TYPE_POKE))
            put("body", JsonPrimitive(text))
        }) ?: return false
        return data["id"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
    }

    /** 拉取对方发来的新事件（排除自己），并推进本机事件游标。 */
    suspend fun pullEvents(): List<CoupleEvent> {
        val cursor = prefs.eventCursorSnapshot()
        val data = call("event.pull", buildJsonObject { put("cursor", JsonPrimitive(cursor)) })
            ?: return emptyList()
        val events = (data["events"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { parseEvent(it) }
        data["cursor"]?.jsonPrimitive?.longOrNull?.let { prefs.setEventCursor(it) }
        return events
    }

    private fun dayParams(day: Long) = buildJsonObject { put("day", JsonPrimitive(day)) }

    private fun parseChallenge(data: JsonObject): CloudChallenge? {
        val obj = data["challenge"] as? JsonObject ?: return null
        val day = obj["day"]?.jsonPrimitive?.longOrNull ?: return null
        return CloudChallenge(
            day = day,
            selfDone = obj["selfDone"]?.jsonPrimitive?.booleanOrNull == true,
            partnerDone = obj["partnerDone"]?.jsonPrimitive?.booleanOrNull == true,
            doneAt = obj["doneAt"]?.jsonPrimitive?.longOrNull
        )
    }

    private fun parseEvent(obj: JsonObject): CoupleEvent? {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        return CoupleEvent(
            id = id,
            type = type,
            body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            fromName = obj["fromName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L
        )
    }

    /** 统一调用：任何异常都吞掉，返回 null 让上层降级。 */
    private suspend fun call(action: String, payload: JsonObject): JsonObject? {
        val token = cloudAuth.ensureAccessToken() ?: return null
        val resp: CloudResponse = try {
            api.call(path, "Bearer $token", CloudRequest(action, payload))
        } catch (e: Exception) {
            return null
        }
        if (resp.code != 0) return null
        return resp.data as? JsonObject
    }
}
