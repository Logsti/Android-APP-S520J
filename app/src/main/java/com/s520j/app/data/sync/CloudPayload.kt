package com.s520j.app.data.sync

import com.s520j.app.data.local.ContentEntity
import com.s520j.app.data.local.FieldCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 本地实体与云端 sync.pull / sync.push 之间的 JSON 映射。 */
object CloudPayload {

    /** 内置演示数据只存在本机，不参与云端同步。 */
    fun isSeed(id: String): Boolean = id.startsWith("seed-")

    fun encodeRecord(entity: ContentEntity, cloudSpaceId: String?): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(entity.id))
        put("module", JsonPrimitive(entity.module))
        // 仅共同可见内容绑定空间，私有内容不向云端暴露空间归属。
        if (entity.visibility.equals("couple", ignoreCase = true) && cloudSpaceId != null) put("spaceId", JsonPrimitive(cloudSpaceId))
        put("visibility", JsonPrimitive(entity.visibility.lowercase()))
        put("title", JsonPrimitive(entity.title))
        put("content", JsonPrimitive(entity.content))
        put("eventDate", entity.eventDate?.let { JsonPrimitive(it) } ?: JsonNull)
        // mediaIds 是本机可显示路径（content:// 或缓存文件），不外传；跨设备用 mediaRefs。
        put("mediaIds", buildJsonArray {
            FieldCodec.decodeList(entity.mediaIds).forEach { add(JsonPrimitive(it)) }
        })
        put("mediaRefs", buildJsonArray {
            FieldCodec.decodeList(entity.mediaRefs).forEach { add(JsonPrimitive(it)) }
        })
        put("extra", buildJsonObject {
            FieldCodec.decodeMap(entity.extraJson).forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        })
        put("createdAt", JsonPrimitive(entity.createdAt))
        put("updatedAt", JsonPrimitive(entity.updatedAt))
        put("deletedAt", entity.deletedAt?.let { JsonPrimitive(it) } ?: JsonNull)
        put("version", JsonPrimitive(entity.version))
    }

    fun pushOp(entity: ContentEntity, seq: Long, cloudSpaceId: String?): JsonObject = buildJsonObject {
        val rec = encodeRecord(entity, cloudSpaceId)
        put("op", JsonPrimitive("UPSERT"))
        put("seq", JsonPrimitive(seq))
        put("entityId", JsonPrimitive(entity.id))
        put("baseVersion", JsonPrimitive(entity.version))
        // 新建分支读取 record 字段；更新分支读取 record.patch，完整字段可直接作为补丁。
        put("record", JsonObject(rec + mapOf("patch" to rec)))
    }

    fun deleteOp(entityId: String, seq: Long, op: String = "SOFT_DELETE"): JsonObject = buildJsonObject {
        put("op", JsonPrimitive(op))
        put("seq", JsonPrimitive(seq))
        put("entityId", JsonPrimitive(entityId))
    }

    /**
     * 解析 pull 返回的「撤销可见」id 列表：对方把记录从共同可见改回仅自己后，
     * 云端只回 id，客户端据此删除本地旧副本。
     */
    fun decodeRevokedIds(data: JsonObject): List<String> =
        (data["revokedIds"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun decodeRecord(obj: JsonObject, fallbackSpaceId: String?): ContentEntity? {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        return ContentEntity(
            id = id,
            module = obj["module"]?.jsonPrimitive?.contentOrNull ?: "NOTE",
            spaceId = obj["spaceId"]?.jsonPrimitive?.contentOrNull ?: fallbackSpaceId,
            ownerId = obj["ownerId"]?.jsonPrimitive?.contentOrNull ?: "",
            ownerName = obj["ownerName"]?.jsonPrimitive?.contentOrNull ?: "",
            visibility = obj["visibility"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "private",
            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
            content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
            eventDate = obj["eventDate"]?.jsonPrimitive?.longOrNull,
            mediaIds = (obj["mediaIds"] as? JsonArray)?.joinToString("|") {
                it.jsonPrimitive.contentOrNull.orEmpty()
            }.orEmpty(),
            mediaRefs = (obj["mediaRefs"] as? JsonArray)?.joinToString("|") {
                it.jsonPrimitive.contentOrNull.orEmpty()
            }.orEmpty(),
            extraJson = (obj["extra"] as? JsonObject)?.entries?.joinToString(";") { (k, v) ->
                "$k=${v.jsonPrimitive.contentOrNull.orEmpty()}"
            }.orEmpty(),
            createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            deletedAt = obj["deletedAt"]?.jsonPrimitive?.longOrNull,
            version = obj["version"]?.jsonPrimitive?.intOrNull ?: 1
        )
    }

    /** 服务端 record 对象（content.* 返回值）。 */
    fun decodeServerRecord(data: JsonObject, fallbackSpaceId: String?): ContentEntity? =
        (data["record"] as? JsonObject)?.let { decodeRecord(it, fallbackSpaceId) }
            ?: (data["server"] as? JsonObject)?.let { decodeRecord(it, fallbackSpaceId) }

    @Suppress("unused")
    private fun JsonObject.raw(key: String): JsonArray? = this[key] as? JsonArray
}
