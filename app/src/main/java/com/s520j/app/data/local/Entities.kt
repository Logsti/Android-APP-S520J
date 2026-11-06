package com.s520j.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val accountId: String,
    val recoverySalt: String,
    val recoveryHash: String,
    val avatar: String?,
    val coupleStartAt: Long,
    val createdAt: Long
)

/** 绑定状态机（技术文档 §4.2）。 */
enum class BindState {
    UNBOUND, INVITE_GENERATED, WAITING_PARTNER, REQUEST_PENDING, BOUND, FAILED, FROZEN;

    val label: String
        get() = when (this) {
            UNBOUND -> "未绑定"
            INVITE_GENERATED -> "已生成邀请"
            WAITING_PARTNER -> "等待对方加入"
            REQUEST_PENDING -> "绑定申请中"
            BOUND -> "已绑定"
            FAILED -> "绑定失败"
            FROZEN -> "冻结归档"
        }
}

@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val intro: String,
    val status: String,
    val bindState: String,
    val bindCode: String?,
    val bindCodeExpiresAt: Long?,
    val createdBy: String,
    val partnerUserId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val pendingAction: String? = null,
    val bindRequestedBy: String? = null,
    val unbindRequestedBy: String? = null,
    val ownerConfirmedAt: Long? = null,
    val partnerConfirmedAt: Long? = null
)

@Entity(tableName = "space_members", primaryKeys = ["spaceId", "userId"])
data class SpaceMemberEntity(
    val spaceId: String,
    val userId: String,
    val role: String,
    val joinedAt: Long
)

@Entity(tableName = "contents", indices = [Index("module"), Index("spaceId"), Index("deletedAt")])
data class ContentEntity(
    @PrimaryKey val id: String,
    val module: String,
    val spaceId: String?,
    val ownerId: String,
    val ownerName: String,
    val visibility: String,
    val title: String,
    val content: String,
    val eventDate: Long?,
    val mediaIds: String,
    val extraJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val version: Int
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entityId: String,
    val op: String,
    val createdAt: Long,
    val attempts: Int = 0
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean
)

@Entity(tableName = "media_assets")
data class MediaEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val type: String,
    val localUri: String?,
    val remoteKey: String?,
    val thumbKey: String?,
    val size: Long,
    val status: String,
    val visibility: String
)

/** 列表字段编码：mediaIds 用 `|` 分隔，extra 用 `k=v;` 分隔，避免引入额外序列化依赖。 */
object FieldCodec {
    fun encodeList(list: List<String>): String = list.joinToString("|")

    fun decodeList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }

    fun encodeMap(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeMap(raw: String): Map<String, String> =
        if (raw.isBlank()) emptyMap() else raw.split(";")
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx <= 0) null else entry.substring(0, idx) to entry.substring(idx + 1)
            }.toMap()
}

fun ContentEntity.toRecord(): ContentRecord = ContentRecord(
    id = id,
    module = ContentModule.from(module),
    spaceId = spaceId,
    ownerId = ownerId,
    ownerName = ownerName,
    visibility = Visibility.from(visibility),
    title = title,
    content = content,
    eventDate = eventDate,
    mediaIds = FieldCodec.decodeList(mediaIds),
    extra = FieldCodec.decodeMap(extraJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    version = version
)

fun ContentRecord.toEntity(): ContentEntity = ContentEntity(
    id = id,
    module = module.name,
    spaceId = spaceId,
    ownerId = ownerId,
    ownerName = ownerName,
    visibility = visibility.name,
    title = title,
    content = content,
    eventDate = eventDate,
    mediaIds = FieldCodec.encodeList(mediaIds),
    extraJson = FieldCodec.encodeMap(extra),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    version = version
)
