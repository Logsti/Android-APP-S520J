package com.s520j.app.data.sync

import com.s520j.app.data.cloud.CloudApi
import com.s520j.app.data.cloud.CloudRequest
import com.s520j.app.data.cloud.CloudRequestException
import com.s520j.app.data.local.NotificationEntity
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.notifications.CoupleNotifier
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 将真实云端事件合并到本机通知中心；已读状态始终只以本机为准。 */
@Singleton
class NotificationSyncRepository @Inject constructor(
    private val api: CloudApi,
    private val db: S520JDatabase,
    private val prefs: UserPreferences,
    private val notifier: CoupleNotifier
) {

    suspend fun pull(token: String, accountId: String): Int {
        val response = api.call(
            "api/notification",
            "Bearer $token",
            CloudRequest("notification.list")
        )
        if (response.code != 0) throw CloudRequestException(response.code, response.message)
        val data = response.data as? JsonObject ?: throw IllegalStateException("通知返回数据异常")
        val items = (data["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            NotificationEntity(
                id = id,
                title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = item["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAt = item["createdAt"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                read = false
            )
        }

        val firstBaseline = !prefs.hasNotificationBaseline(accountId)
        var inserted = 0
        items.forEach { item ->
            val local = if (firstBaseline) item.copy(read = true) else item
            if (db.notificationDao().insertIfAbsent(local) >= 0L) {
                inserted++
                if (!firstBaseline) notifier.notifyUpdate(local.id, local.title, local.body)
            }
        }
        if (firstBaseline) prefs.markNotificationBaseline(accountId)
        return inserted
    }

    /** 本地明确异常终态也使用同一去重与系统通知路径。 */
    suspend fun showLocal(
        id: String,
        title: String,
        body: String,
        createdAt: Long = System.currentTimeMillis()
    ) {
        val entity = NotificationEntity(id, title, body, createdAt, read = false)
        if (db.notificationDao().insertIfAbsent(entity) >= 0L) {
            notifier.notifyUpdate(id, title, body)
        }
    }

    companion object {
        fun localAlertId(accountId: String, kind: String, signature: String, generation: Long): String {
            val source = "$accountId\u0000$kind\u0000$signature\u0000$generation"
            val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            return "local-" + digest.joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        }
    }
}
