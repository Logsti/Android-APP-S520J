package com.s520j.app.data.cloud

import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.domain.food.DishCandidate
import com.s520j.app.domain.food.DishGroup
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class DishHistoryEntry(
    val name: String,
    val group: DishGroup?,
    val level2: String,
    val at: Long
)

data class DishHistory(
    val picked: List<DishHistoryEntry> = emptyList(),
    /** 仅本机 UI 偏好，随记录一起在内存里传递，不写入云端记录。 */
    val preferNew: Boolean = true
) {
    val names: List<String> get() = picked.map { it.name }

    /** 最近点过的菜名，按时间倒序，作为 `dish.pick` 的 recent。 */
    fun recentNames(limit: Int = RECENT_LIMIT): List<String> =
        picked.sortedByDescending { it.at }.take(limit).map { it.name }

    /** 把一桌菜并入记录：同名只更新时间，按时间倒序截断到上限。 */
    fun withRecords(items: List<DishCandidate>, now: Long): DishHistory {
        val merged = picked.toMutableList()
        items.filter { it.name.isNotBlank() }.distinctBy { it.name.trim() }.forEach { dish ->
            val entry = DishHistoryEntry(dish.name.trim(), dish.group, dish.level2, now)
            val index = merged.indexOfFirst { it.name == entry.name }
            if (index >= 0) merged[index] = entry else merged.add(entry)
        }
        return copy(picked = merged.sortedByDescending { it.at }.take(LIMIT))
    }

    companion object {
        const val LIMIT = 1000
        const val RECENT_LIMIT = 100
    }
}

/**
 * 点过的菜：只存云端的 `api/settings`（[DISH_HISTORY_KEY]）。
 *
 * 本机不缓存记录、不维护待补推队列：读取失败保留进程内上次值，写入失败由界面提示重试。
 * 「新菜优先」是 UI 偏好，只留在本机 DataStore，不与记录混存。
 */
@Singleton
class DishHistoryRepository @Inject constructor(
    private val api: CloudApi,
    private val cloudAuth: CloudAuthRepository,
    private val prefs: UserPreferences
) {
    private val path = "api/settings"

    /** 进程内最近一次读到的记录，仅作写前合并基线，不做任何本机持久化。 */
    private var cached: DishHistory? = null

    /** 只读云端：成功返回最新记录，失败返回 null（调用方保留上次内存值）。 */
    suspend fun refresh(): DishHistory? {
        val token = accessToken() ?: return null
        val values = readRemote(token) ?: return null
        val history = decode(values[DISH_HISTORY_KEY]?.toString())
            .copy(preferNew = prefs.dishPreferNewSnapshot())
        cached = history
        return history
    }

    /**
     * 确认这一桌后写入云端：先取云端基线合并，再整份写回。
     * 失败返回 null，不落本机，由界面提示重试。
     */
    suspend fun record(
        items: List<DishCandidate>,
        now: Long = System.currentTimeMillis()
    ): DishHistory? {
        if (items.isEmpty()) return cached
        val base = cached ?: refresh() ?: return null
        val next = base.withRecords(items, now)
        if (!push(next)) return null
        cached = next
        return next
    }

    /** 「新菜优先」只改本机偏好，不写云端记录。 */
    suspend fun setPreferNew(value: Boolean) {
        prefs.setDishPreferNew(value)
        cached = (cached ?: DishHistory()).copy(preferNew = value)
    }

    /** 清空云端记录（共同空间与个人都清）；失败返回 false，本机不留缓存。 */
    suspend fun clear(): Boolean {
        val token = accessToken() ?: return false
        val cleared = DishHistory(preferNew = prefs.dishPreferNewSnapshot())
        val ok = scopes().map { scope -> push(token, scope, cleared) }.all { it }
        if (ok) cached = cleared
        return ok
    }

    private suspend fun accessToken(): String? = try {
        cloudAuth.ensureAccessToken()
    } catch (e: Exception) {
        null
    }

    private suspend fun push(history: DishHistory): Boolean {
        val token = accessToken() ?: return false
        return scopes().any { scope -> push(token, scope, history) }
    }

    private suspend fun push(token: String, scope: String, history: DishHistory): Boolean {
        val payload = buildJsonObject {
            put("scope", JsonPrimitive(scope))
            put("key", JsonPrimitive(DISH_HISTORY_KEY))
            put("value", encode(history))
        }
        val resp = try {
            api.call(path, "Bearer $token", CloudRequest("settings.put", payload))
        } catch (e: IOException) {
            return false
        } catch (e: Exception) {
            return false
        }
        return resp.code == 0
    }

    /** 已绑定空间优先写 space；失败或未绑定时回退 user。 */
    private suspend fun scopes(): List<String> {
        val spaceId = prefs.cloudSessionSnapshot()?.spaceId?.takeIf { it.isNotBlank() }
            ?: try {
                cloudAuth.resolveSpaceId()
            } catch (e: Exception) {
                null
            }
        return if (spaceId.isNullOrBlank()) listOf(SCOPE_USER) else listOf(SCOPE_SPACE, SCOPE_USER)
    }

    private suspend fun readRemote(token: String): JsonObject? {
        for (scope in scopes()) {
            val resp = try {
                api.call(path, "Bearer $token", CloudRequest("settings.get", buildJsonObject {
                    put("scope", JsonPrimitive(scope))
                }))
            } catch (e: Exception) {
                null
            } ?: continue
            if (resp.code != 0) continue
            val values = (resp.data as? JsonObject)?.get("values") as? JsonObject ?: continue
            if (values.isEmpty()) continue
            return values
        }
        return null
    }

    // ------------------------------------------------------------ codec

    private fun encode(history: DishHistory): JsonObject = buildJsonObject {
        put("picked", buildJsonArray {
            history.picked.forEach { entry ->
                add(buildJsonObject {
                    put("name", JsonPrimitive(entry.name))
                    entry.group?.let { put("group", JsonPrimitive(it.key)) }
                    put("level2", JsonPrimitive(entry.level2))
                    put("at", JsonPrimitive(entry.at))
                })
            }
        })
    }

    private fun decode(raw: String?): DishHistory {
        val obj = raw?.let {
            runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
        } ?: return DishHistory()
        val picked = (obj["picked"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            DishHistoryEntry(
                name = name,
                group = DishGroup.fromKey(item["group"]?.jsonPrimitive?.contentOrNull),
                level2 = item["level2"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                at = item["at"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        }.distinctBy { it.name }
        return DishHistory(picked = picked.sortedByDescending { it.at }.take(DishHistory.LIMIT))
    }

    companion object {
        const val DISH_HISTORY_KEY = "dish_history"
        const val SCOPE_SPACE = "space"
        const val SCOPE_USER = "user"
    }
}
