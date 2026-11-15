package com.s520j.app.data.backup

import com.s520j.app.data.local.ContentEntity
import com.s520j.app.data.sync.CloudPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 备份文件的编解码：复用云端负载的字段格式，保证「云端 → 备份 → 恢复」字段一致。
 * 备份不含图片二进制，图片随导出 ZIP 单独打包。
 */
object BackupCodec {

    const val VERSION = 1

    fun encode(records: List<ContentEntity>, createdAt: Long): String {
        // 云端负载不含本机归属信息，备份额外补 ownerId / ownerName / spaceId，避免恢复后作者丢失。
        val arr = JsonArray(records.map { record ->
            JsonObject(
                CloudPayload.encodeRecord(record, record.spaceId) + mapOf(
                    "ownerId" to JsonPrimitive(record.ownerId),
                    "ownerName" to JsonPrimitive(record.ownerName),
                    "localSpaceId" to (record.spaceId?.let { JsonPrimitive(it) } ?: JsonNull)
                )
            )
        })
        return Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("version", JsonPrimitive(VERSION))
                put("createdAt", JsonPrimitive(createdAt))
                put("records", arr)
            }
        )
    }

    /** 解析备份；版本不符或格式错误返回 null，避免用坏文件覆盖本地数据。 */
    fun decode(json: String): List<ContentEntity>? = runCatching {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return null
        if (root["version"]?.let { (it as? JsonPrimitive)?.longOrNull } != VERSION.toLong()) return null
        (root["records"] as? JsonArray)?.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val decoded = CloudPayload.decodeRecord(obj, null) ?: return@mapNotNull null
            decoded.copy(
                ownerId = obj["ownerId"]?.jsonPrimitive?.contentOrNull ?: decoded.ownerId,
                ownerName = obj["ownerName"]?.jsonPrimitive?.contentOrNull ?: decoded.ownerName,
                spaceId = obj["localSpaceId"]?.jsonPrimitive?.contentOrNull ?: decoded.spaceId
            )
        } ?: return null
    }.getOrNull()

    fun fileName(createdAt: Long): String = "s520j-backup-$createdAt.json"
}
