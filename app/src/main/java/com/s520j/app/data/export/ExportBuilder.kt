package com.s520j.app.data.export

import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.prefs.SessionProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 阶段 3 的导出：仅 JSON 文本，不含图片二进制（includesImages 恒为 false）。 */
object ExportBuilder {

    const val SCHEMA_VERSION = 1

    private val format = Json { prettyPrint = true }

    fun build(
        space: SpaceEntity?,
        session: SessionProfile,
        records: List<ContentRecord>,
        exportedAt: Long
    ): String {
        val root = buildJsonObject {
            put("version", SCHEMA_VERSION)
            put("exportedAt", exportedAt)
            put("includesImages", false)
            put("space", if (space == null) JsonNull else spaceJson(space))
            put(
                "session",
                buildJsonObject {
                    put("nickname", session.nickname)
                    put("partnerNickname", session.partnerNickname)
                    put("coupleStartAt", session.coupleStartAt)
                }
            )
            put(
                "records",
                buildJsonArray { records.forEach { add(recordJson(it)) } }
            )
        }
        return format.encodeToString(JsonObject.serializer(), root)
    }

    private fun spaceJson(space: SpaceEntity) = buildJsonObject {
        put("id", space.id)
        put("name", space.name)
        put("intro", space.intro)
        put("status", space.status)
        put("bindState", space.bindState)
        put("bindCode", space.bindCode)
        put("bindCodeExpiresAt", space.bindCodeExpiresAt)
        put("createdBy", space.createdBy)
        put("partnerUserId", space.partnerUserId)
        put("createdAt", space.createdAt)
        put("updatedAt", space.updatedAt)
        put("pendingAction", space.pendingAction)
        put("bindRequestedBy", space.bindRequestedBy)
        put("unbindRequestedBy", space.unbindRequestedBy)
        put("ownerConfirmedAt", space.ownerConfirmedAt)
        put("partnerConfirmedAt", space.partnerConfirmedAt)
    }

    private fun recordJson(record: ContentRecord) = buildJsonObject {
        put("id", record.id)
        put("module", record.module.name)
        put("moduleLabel", record.module.label)
        put("spaceId", record.spaceId)
        put("ownerName", record.ownerName)
        put("visibility", record.visibility.name)
        put("title", record.title)
        put("content", record.content)
        put("eventDate", record.eventDate)
        put("mediaIds", buildJsonArray { record.mediaIds.forEach { add(JsonPrimitive(it)) } })
        put("extra", buildJsonObject { record.extra.forEach { (key, value) -> put(key, JsonPrimitive(value)) } })
        put("createdAt", record.createdAt)
        put("updatedAt", record.updatedAt)
        put("deletedAt", record.deletedAt)
        put("version", record.version)
    }
}
