package com.s520j.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.s520j.app.data.cloud.CloudApi
import com.s520j.app.data.cloud.CloudAuthRepository
import com.s520j.app.data.cloud.CloudRequest
import com.s520j.app.data.cloud.CloudRequestException
import com.s520j.app.data.cloud.CloudResponse
import com.s520j.app.data.local.OutboxEntity
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.SyncConflictEntity
import com.s520j.app.data.local.toEntity
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.data.repository.LocalResetCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

enum class SyncPhase { IDLE, SYNCING, OFFLINE, FAILED }

data class SyncState(
    val phase: SyncPhase = SyncPhase.IDLE,
    val pending: Int = 0,
    val conflicts: Int = 0,
    val lastSyncAt: Long = 0L,
    val message: String = ""
)

/** 本地优先 + 云端增量同步：outbox 推送 → 按游标拉取 → 冲突快照留存。 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: CloudApi,
    private val db: S520JDatabase,
    private val prefs: UserPreferences,
    private val cloudAuth: CloudAuthRepository,
    private val localReset: LocalResetCoordinator,
    private val mediaRepository: com.s520j.app.data.media.MediaRepository,
    private val notificationSync: NotificationSyncRepository,
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    val pendingCount: Flow<Int> = db.outboxDao().observeCount()
    val conflictCount: Flow<Int> = db.syncConflictDao().observeCount()

    /** 待处理的冲突快照（8B.2），供「保留本机 / 保留云端」选择。 */
    val conflicts: Flow<List<SyncConflictEntity>> = db.syncConflictDao().observeAll()

    init {
        // 上次同步时间落盘，重启后「我们」页仍能显示相对时间。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            localReset.ensureCompleted()
            val last = prefs.lastSyncAtSnapshot()
            if (last > 0L) _state.update { it.copy(lastSyncAt = last) }
        }
    }

    /** 立即执行一次同步；自动同步与手动同步分别计入低打扰失败提醒。 */
    suspend fun syncNow(automatic: Boolean = false): SyncState {
        localReset.ensureCompleted()
        return mutex.withLock {
            val previousSession = prefs.cloudSessionSnapshot()
            val previousAccount = previousSession?.userId.orEmpty()
            val token = cloudAuth.ensureAccessToken()
            if (token == null) {
                val invalidated = previousSession != null && prefs.cloudSessionSnapshot() == null
                if (invalidated) {
                    handleSyncFailure(
                        CloudRequestException(401, "登录已失效，请重新登录"),
                        automatic,
                        previousAccount
                    )
                    return@withLock finish(SyncPhase.FAILED, message = "登录已失效，请重新登录")
                }
                if (previousSession != null) {
                    handleSyncFailure(IllegalStateException("暂时无法获取登录凭证"), automatic, previousAccount)
                }
                return@withLock finish(SyncPhase.OFFLINE, message = "未登录云端")
            }

            _state.value = _state.value.copy(phase = SyncPhase.SYNCING, message = "")
            val accountId = prefs.cloudSessionSnapshot()?.userId ?: previousAccount
            var pushed = 0
            var pulled = 0
            var failure: Exception? = null
            try {
                val spaceId = cloudAuth.resolveSpaceIdForSync(token)
                pushed = push(token, spaceId)
                pulled = pull(token, spaceId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failure = e
            }

            // 内容写操作失败也不能阻断空间冻结等云端通知的接收。
            if (accountId.isBlank()) {
                if (failure == null) failure = CloudRequestException(401, "登录信息不完整，请重新登录")
            } else {
                try {
                    notificationSync.pull(token, accountId)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (failure == null) failure = e
                }
            }

            val error = failure
            if (error == null) {
                handleSyncRecovered(accountId)
                finish(
                    SyncPhase.IDLE,
                    message = "已同步：上传 $pushed 条，接收 $pulled 条"
                )
            } else {
                handleSyncFailure(error, automatic, accountId)
                val cloud = error as? CloudRequestException
                val message = when {
                    cloud != null && SyncPolicy.isSpaceFrozenFailure(cloud.code, cloud.message) ->
                        "共同空间已冻结，仅可查看与导出"
                    cloud?.code == 401 -> "登录已失效，请重新登录"
                    cloud?.code == 403 -> "同步权限不足，请检查账号"
                    else -> "同步失败，请稍后重试"
                }
                finish(SyncPhase.FAILED, message = message)
            }
        }
    }

    private suspend fun handleSyncRecovered(accountId: String) {
        val track = prefs.syncFailureTrackSnapshot()
        if (track.accountId != accountId || track.consecutive > 0 || track.alerted) {
            prefs.saveSyncFailureTrack(SyncPolicy.syncRecovered(track, accountId))
        }
    }

    private suspend fun handleSyncFailure(error: Exception, automatic: Boolean, accountId: String) {
        val cloud = error as? CloudRequestException
        val code = cloud?.code ?: 0
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        if (cloud != null && SyncPolicy.isSpaceFrozenFailure(code, detail)) return

        val track = prefs.syncFailureTrackSnapshot()
        val accountAlert = cloud?.let { SyncPolicy.requiredAccountAlert(it.code, it.message) }
        if (accountAlert != null) {
            prefs.saveSyncFailureTrack(
                track.copy(accountId = accountId, signature = "account:$code", alerted = true)
            )
            notificationSync.showLocal(
                NotificationSyncRepository.localAlertId(accountId, accountAlert.kind, code.toString(), track.generation),
                accountAlert.title,
                accountAlert.body
            )
            return
        }
        if (!automatic) return

        val signature = if (cloud != null) "cloud:$code:$detail" else "${error.javaClass.simpleName}:$detail"
        val update = SyncPolicy.autoSyncFailed(
            track,
            accountId,
            signature,
            pendingUpload = db.outboxDao().count() > 0
        )
        prefs.saveSyncFailureTrack(update.track)
        if (update.shouldAlert) {
            notificationSync.showLocal(
                NotificationSyncRepository.localAlertId(accountId, "sync", signature, track.generation),
                "同步需要检查",
                "已连续 3 次自动同步失败，仍有内容等待上传。请检查网络或同步状态后重试。"
            )
        }
    }

    private suspend fun finish(phase: SyncPhase, message: String): SyncState {
        val lastSyncAt = if (phase == SyncPhase.IDLE) System.currentTimeMillis() else _state.value.lastSyncAt
        if (phase == SyncPhase.IDLE) prefs.setLastSyncAt(lastSyncAt)
        val snapshot = SyncState(
            phase = phase,
            pending = db.outboxDao().count(),
            conflicts = db.syncConflictDao().list().size,
            lastSyncAt = lastSyncAt,
            message = message
        )
        _state.value = snapshot
        return snapshot
    }

    // ------------------------------------------------------------ push

    private suspend fun push(token: String, spaceId: String?): Int {
        val pending = db.outboxDao().pending()
        if (pending.isEmpty()) return 0
        val ops = mutableListOf<JsonObject>()
        pending.forEach { item ->
            if (CloudPayload.isSeed(item.entityId)) {
                db.outboxDao().remove(item.seq)
                return@forEach
            }
            when (item.op) {
                "UPSERT" -> db.contentDao().findById(item.entityId)?.let {
                    // 9.3：未绑定共同空间时，共同可见内容没有 spaceId，云端会 400 拒绝。
                    // 这里跳过但保留 outbox 项，等绑定后再推，避免内容被当成业务拒绝直接丢弃。
                    if (SyncPolicy.shouldDeferPush(it.visibility, spaceId)) return@let
                    ops += CloudPayload.pushOp(it, item.seq, spaceId)
                }
                "RESTORE" -> ops += CloudPayload.deleteOp(item.entityId, item.seq, "RESTORE")
                "PURGE" -> ops += CloudPayload.deleteOp(item.entityId, item.seq, "PURGE")
                else -> ops += CloudPayload.deleteOp(item.entityId, item.seq, "SOFT_DELETE")
            }
        }
        if (ops.isEmpty()) return 0

        var pushed = 0
        ops.chunked(BATCH).forEach { batch ->
            val entityOf = batch.associate { op ->
                (op["seq"]?.jsonPrimitive?.longOrNull ?: 0L) to
                    (op["entityId"]?.jsonPrimitive?.contentOrNull ?: "")
            }
            val data = call("api/sync", token, "sync.push", buildJsonObject {
                put("ops", JsonArray(batch))
            }) ?: throw IllegalStateException("push failed")
            val results = data["results"] as? JsonArray ?: return@forEach
            results.forEach { element ->
                val result = element.jsonObject
                val seq = result["seq"]?.jsonPrimitive?.longOrNull ?: return@forEach
                if (result["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                    val code = result["code"]?.jsonPrimitive?.intOrNull ?: 500
                    val message = result["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    // 鉴权/权限/账号错误必须保留 outbox 并进入异常分类，不能当业务拒绝静默丢弃。
                    if (code !in 400..499 || code == 401 || code == 403 ||
                        SyncPolicy.requiredAccountAlert(code, message) != null
                    ) {
                        throw CloudRequestException(code, message)
                    }
                    db.outboxDao().remove(seq)
                    return@forEach
                }
                val payload = result["data"] as? JsonObject
                db.outboxDao().remove(seq)
                if (payload?.get("conflict")?.jsonPrimitive?.booleanOrNull == true) {
                    saveConflict(payload, spaceId)
                } else {
                    val entityId = entityOf[seq].orEmpty()
                    if (db.outboxDao().countByEntity(entityId) == 0) {
                        CloudPayload.decodeServerRecord(payload ?: JsonObject(emptyMap()), spaceId)
                            ?.let { db.contentDao().upsert(it) }
                    }
                    pushed++
                }
            }
        }
        return pushed
    }

    /** 服务器版本优先，同时保存双方快照 30 天，供用户选择“保留哪一份”。 */
    private suspend fun saveConflict(payload: JsonObject, spaceId: String?) {
        val server = payload["server"] as? JsonObject ?: return
        val id = server["id"]?.jsonPrimitive?.contentOrNull ?: return
        val local = db.contentDao().findById(id)
        val now = System.currentTimeMillis()
        db.syncConflictDao().upsert(
            SyncConflictEntity(
                id = UUID.randomUUID().toString(),
                entityId = id,
                serverJson = server.toString(),
                localJson = local?.let { CloudPayload.encodeRecord(it, spaceId).toString() }.orEmpty(),
                createdAt = now,
                expireAt = SyncApply.conflictExpireAt(now)
            )
        )
        CloudPayload.decodeRecord(server, spaceId)?.let { db.contentDao().upsert(it) }
    }

    /**
     * 用户处理冲突：保留云端则直接丢弃快照；保留本机则以提升后的版本号重新入队推送。
     */
    suspend fun resolveConflict(conflictId: String, keepLocal: Boolean) {
        val conflict = db.syncConflictDao().findById(conflictId) ?: return
        if (keepLocal) {
            val spaceId = prefs.cloudSessionSnapshot()?.spaceId
            val record = ConflictResolver.keepLocal(
                localJson = conflict.localJson,
                serverJson = conflict.serverJson,
                now = System.currentTimeMillis(),
                fallbackSpaceId = spaceId
            )
            if (record != null) {
                db.contentDao().upsert(record.toEntity())
                db.outboxDao().enqueue(
                    OutboxEntity(
                        entityId = record.id,
                        op = "UPSERT",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
        db.syncConflictDao().deleteById(conflictId)
        _state.value = _state.value.copy(
            conflicts = db.syncConflictDao().list().size,
            message = if (keepLocal) "已保留本机版本，下次同步重新上传" else "已保留云端版本"
        )
    }

    // ------------------------------------------------------------ pull

    private suspend fun pull(token: String, spaceId: String?): Int {
        var cursor = prefs.syncCursorSnapshot()
        var total = 0
        var hasMore = true
        while (hasMore) {
            val data = call("api/sync", token, "sync.pull", buildJsonObject {
                put("cursor", JsonPrimitive(cursor))
            }) ?: throw IllegalStateException("pull failed")
            val records = data["records"] as? JsonArray ?: JsonArray(emptyList())
            // 对方把「共同可见」改回「仅自己」：云端只回 id，这里删除本机旧副本。
            CloudPayload.decodeRevokedIds(data).forEach { id ->
                if (db.outboxDao().countByEntity(id) > 0) return@forEach
                db.contentDao().hardDelete(id)
            }
            records.forEach { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                // 本机还有未推送的修改时，暂不覆盖。
                if (db.outboxDao().countByEntity(id) > 0) return@forEach
                CloudPayload.decodeRecord(obj, spaceId)?.let { entity ->
                    // 本机路径不参与同步：按云端引用把图片下载到本地缓存后再落库。
                    val refs = com.s520j.app.data.local.FieldCodec.decodeList(entity.mediaRefs)
                    val local = if (refs.isEmpty()) emptyList() else mediaRepository.ensureLocal(refs)
                    db.contentDao().upsert(
                        entity.copy(
                            mediaIds = com.s520j.app.data.local.FieldCodec.encodeList(local)
                        )
                    )
                    total++
                }
            }
            val next = data["cursor"]?.jsonPrimitive?.longOrNull ?: cursor
            prefs.setSyncCursor(next)
            cursor = next
            hasMore = data["hasMore"]?.jsonPrimitive?.booleanOrNull == true
        }
        return total
    }

    // ---------------------------------------------------------- cleanup

    /** 回收站满 30 天彻底清理；冲突快照同步过期清理。 */
    suspend fun cleanupExpired(now: Long = System.currentTimeMillis()) {
        val threshold = now - RecyclePolicy.RETENTION_DAYS * 24L * 60 * 60 * 1000
        db.contentDao().purgeDeletedBefore(threshold)
        db.syncConflictDao().purgeExpired(now)
    }

    // -------------------------------------------------------- scheduling

    /** 登录后开启 15 分钟周期同步（系统省电策略下可能延后）。 */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** 退出登录时停止后台同步，避免无谓唤醒。 */
    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
    }

    private suspend fun call(
        path: String,
        token: String,
        action: String,
        payload: JsonObject
    ): JsonObject? {
        val resp: CloudResponse = api.call(path, "Bearer $token", CloudRequest(action, payload))
        if (resp.code != 0) throw CloudRequestException(resp.code, resp.message)
        return resp.data as? JsonObject
    }

    companion object {
        private const val BATCH = 30
        private const val WORK_PERIODIC = "s520j-periodic-sync"
    }
}
