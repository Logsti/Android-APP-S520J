package com.s520j.app.data.sync

import com.s520j.app.data.local.toRecord
import com.s520j.app.data.model.ContentRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** 冲突选择（纯函数）：用户决定保留本机还是云端版本后如何回写。 */
object ConflictResolver {

    /**
     * 解析「保留本机」：取冲突发生时留存的本机快照，版本号对齐云端最近版本，
     * 这样下一次推送的 baseVersion 能通过乐观锁校验，由服务端完成 +1。
     * 同时清除删除标记（用户主动选择保留即视为恢复该条内容）。
     */
    fun keepLocal(
        localJson: String,
        serverJson: String,
        now: Long,
        fallbackSpaceId: String?
    ): ContentRecord? {
        val local = decode(localJson, fallbackSpaceId) ?: return null
        val server = decode(serverJson, fallbackSpaceId)
        return local.copy(
            version = server?.version ?: local.version,
            updatedAt = now,
            deletedAt = null
        )
    }

    private fun decode(json: String, fallbackSpaceId: String?): ContentRecord? =
        runCatching {
            CloudPayload.decodeRecord(Json.parseToJsonElement(json).jsonObject, fallbackSpaceId)
        }.getOrNull()?.toRecord()?.takeIf { it.id.isNotBlank() }

    /** 冲突页展示用：把快照 JSON 还原成可读取的字段。 */
    fun snapshot(json: String, fallbackSpaceId: String? = null): ContentRecord? =
        decode(json, fallbackSpaceId)
}
