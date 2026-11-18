package com.s520j.app.data.cloud

import com.s520j.app.domain.food.DishCandidate
import com.s520j.app.domain.food.DishGroup
import com.s520j.app.domain.food.DishPickRequest
import com.s520j.app.domain.food.DishPickResult
import com.s520j.app.domain.food.DishSource
import com.s520j.app.domain.food.DishTaxonomy
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** `dish.list` 一页快照：首屏还会带四组二级分类与自建库。 */
data class DishSnapshotPage(
    val items: List<DishCandidate>,
    val nextCursor: String?,
    val total: Int,
    val categories: Map<String, List<String>>?,
    val builtin: List<DishCandidate>?
)

/**
 * 点菜云端通道：`dish.pick`（实时抽页 + 降级）与 `dish.list`（池子快照）。
 *
 * 按现有手写 JsonElement 解析，不反射序列化整个对象；
 * 网络异常抛 IOException，业务/鉴权错误抛 [CloudRequestException]（含 401），
 * 401 时用 refresh token 刷新后重试一次。
 */
@Singleton
class DishRandomRepository @Inject constructor(
    private val api: CloudApi,
    private val cloudAuth: CloudAuthRepository
) {
    private val path = "api/content"

    suspend fun pick(request: DishPickRequest): DishPickResult {
        val data = call("dish.pick", buildJsonObject {
            put("groups", buildJsonArray {
                request.groups.forEach { selection ->
                    add(buildJsonObject {
                        put("group", JsonPrimitive(selection.group.key))
                        put("count", JsonPrimitive(selection.count))
                        put("subCategory", JsonPrimitive(selection.subCategory))
                    })
                }
            })
            put("exclude", names(request.exclude))
            put("recent", names(request.recent))
        })
        return DishPickResult(
            items = (data["items"] as? JsonArray).orEmpty().mapNotNull { candidateOf(it) },
            quotaRemaining = data["quotaRemaining"]?.jsonPrimitive?.intOrNull ?: 0,
            degraded = data["degraded"]?.jsonPrimitive?.booleanOrNull == true,
            note = data.str("note")?.takeIf { it.isNotBlank() }
        )
    }

    /** 分页拉取池子快照；cursor 为 null 表示第一页（会额外返回分类与自建库）。 */
    suspend fun listPage(cursor: String?, limit: Int = PAGE_LIMIT): DishSnapshotPage {
        val data = call("dish.list", buildJsonObject {
            put("cursor", JsonPrimitive(cursor ?: "0"))
            put("limit", JsonPrimitive(limit.coerceIn(1, PAGE_LIMIT)))
        })
        val categories = (data["categories"] as? JsonObject)?.let { obj ->
            obj.mapNotNull { (key, value) ->
                val list = (value as? JsonArray).orEmpty()
                    .mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                    .filter { it.isNotEmpty() }
                if (list.isEmpty()) null else key to list
            }.toMap()
        }
        return DishSnapshotPage(
            items = (data["items"] as? JsonArray).orEmpty().mapNotNull { candidateOf(it) },
            nextCursor = data["nextCursor"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            total = data["total"]?.jsonPrimitive?.intOrNull ?: 0,
            categories = categories?.takeIf { it.isNotEmpty() },
            builtin = (data["builtin"] as? JsonArray)?.mapNotNull { candidateOf(it) }
        )
    }

    private suspend fun call(action: String, payload: JsonObject): JsonObject {
        val token = cloudAuth.ensureAccessToken()
            ?: throw CloudRequestException(401, "未登录云端")
        val resp = try {
            api.call(path, "Bearer $token", CloudRequest(action, payload))
        } catch (e: IOException) {
            throw e
        }
        if (resp.code == 401) {
            // token 过期：强制刷新后重试一次，仍失败则把 401 抛给界面提示重新登录。
            val refreshed = cloudAuth.ensureAccessToken(force = true)
            if (refreshed != null) {
                val retry = api.call(path, "Bearer $refreshed", CloudRequest(action, payload))
                if (retry.code != 0) throw CloudRequestException(retry.code, retry.message)
                return retry.data as? JsonObject ?: JsonObject(emptyMap())
            }
        }
        if (resp.code != 0) throw CloudRequestException(resp.code, resp.message)
        return resp.data as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun candidateOf(element: JsonElement): DishCandidate? {
        val obj = element as? JsonObject ?: return null
        val name = obj.str("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        val level2 = obj.str("level2").orEmpty()
        val group = DishGroup.fromKey(obj.str("group")) ?: DishTaxonomy.level2ToGroup(level2) ?: return null
        return DishCandidate(
            name = name,
            level1 = obj.str("level1").orEmpty(),
            level2 = level2,
            group = group,
            // 自建库对象没有 source 字段，默认按内置标注，避免伪装成实时。
            source = DishSource.fromKey(obj.str("source"))
        )
    }

    private fun names(values: List<String>): JsonArray = buildJsonArray {
        values.forEach { value -> add(JsonPrimitive(value)) }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        /** 单页最多 1000 道，服务端按此上限分页。 */
        const val PAGE_LIMIT = 1000
    }
}
