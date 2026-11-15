package com.s520j.app.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.s520j.app.data.cloud.CloudApi
import com.s520j.app.data.cloud.CloudAuthRepository
import com.s520j.app.data.cloud.CloudRequest
import com.s520j.app.util.PhotoCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 记录图片的云端通道：上传压缩后的 JPEG，按需下载并缓存到 filesDir/media。
 * 本机记录里的 mediaIds 始终是可显示路径（content:// 或缓存文件路径），
 * 跨设备只通过 mediaRefs（assetId）交换。
 */
@Singleton
class MediaRepository @Inject constructor(
    private val api: CloudApi,
    private val cloudAuth: CloudAuthRepository,
    @ApplicationContext private val context: Context
) {
    private val path = "api/media"

    /**
     * 上传一张本地图片，成功返回 assetId；失败返回 null。
     *
     * 云端 HTTP 网关单次请求体约 100KB，首次按 68KB 预算压缩；
     * 若仍被拒（例如 413 EXCEED_MAX_PAYLOAD_SIZE），自动降预算重压重试。
     */
    suspend fun upload(localUri: String): String? {
        val token = cloudAuth.ensureAccessToken() ?: return null
        for (budget in BUDGETS) {
            val compressed = PhotoCompressor.toBase64(context, Uri.parse(localUri), budget) ?: run {
                Log.w(TAG, "图片解码失败: $localUri")
                return null
            }
            val id = UUID.randomUUID().toString().replace("-", "")
            Log.w(TAG, "上传尝试 budget=${budget / 1024}KB actual=${compressed.second / 1024}KB")
            if (uploadOnce(token, id, compressed.first)) return id
        }
        Log.w(TAG, "图片上传失败（已尝试全部压缩预算）: $localUri")
        return null
    }

    /** 单次上传，仅对网络异常做短重试；返回是否成功。 */
    private suspend fun uploadOnce(token: String, id: String, base64: String): Boolean {
        repeat(MAX_RETRY + 1) { attempt ->
            try {
                val resp = api.call(path, "Bearer $token", CloudRequest("media.upload", buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("mime", JsonPrimitive("image/jpeg"))
                    put("data", JsonPrimitive(base64))
                }))
                if (resp.code == 0) return true
                Log.w(TAG, "上传被服务端拒绝(${resp.code}): ${resp.message}")
                return false
            } catch (e: IOException) {
                Log.w(TAG, "上传网络异常 attempt=${attempt + 1}/${MAX_RETRY + 1}: ${e.message}")
                if (attempt < MAX_RETRY) delay(BACKOFF_MS * (1L shl attempt)) else return false
            } catch (e: Exception) {
                // 413 等网关错误会让响应不符合 CloudResponse 结构，这里视为本次失败。
                Log.w(TAG, "上传失败: ${e.javaClass.simpleName} ${e.message}")
                return false
            }
        }
        return false
    }

    /** 批量上传：最多 2 张并发，避免弱网下多路大请求互相拖慢导致超时。 */
    suspend fun uploadAll(localUris: List<String>): List<String?> = coroutineScope {
        val gate = Semaphore(UPLOAD_CONCURRENCY)
        localUris.map { uri ->
            async {
                gate.withPermit { upload(uri) }
            }
        }.awaitAll()
    }

    /** 把云端引用解析成本机可显示路径；已缓存的直接复用，缺失的批量下载。 */
    suspend fun ensureLocal(refs: List<String>): List<String> = withContext(Dispatchers.IO) {
        val real = refs.filter { it.isNotBlank() && it != MISSING_REF }
        if (real.isEmpty()) return@withContext emptyList()
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val missing = real.filter { !File(dir, "$it.jpg").exists() }.distinct()
        if (missing.isNotEmpty()) download(missing, dir)
        real.mapNotNull { ref ->
            val file = File(dir, "$ref.jpg")
            if (file.exists() && file.length() > 0) file.absolutePath else null
        }
    }

    private suspend fun download(ids: List<String>, dir: File) {
        val token = cloudAuth.ensureAccessToken() ?: return
        ids.chunked(FETCH_BATCH).forEach { batch ->
            val resp = runCatching {
                api.call(path, "Bearer $token", CloudRequest("media.fetch", buildJsonObject {
                    put("ids", buildJsonArray { batch.forEach { add(JsonPrimitive(it)) } })
                }))
            }.getOrNull() ?: return
            if (resp.code != 0) return
            ((resp.data as? JsonObject)?.get("items") as? JsonArray)?.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val data = obj["data"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                runCatching {
                    val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                    File(dir, "$id.jpg").writeBytes(bytes)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MediaRepository"
        private const val FETCH_BATCH = 4
        private const val MAX_RETRY = 2
        private const val BACKOFF_MS = 300L
        private const val UPLOAD_CONCURRENCY = 2

        /** 上传失败占位符：写入 mediaRefs 以保持与 mediaIds 索引对齐，不参与下载。 */
        const val MISSING_REF = "-"

        /** 逐级收紧的压缩预算：首次 68KB，仍被拒则 56KB / 44KB。 */
        private val BUDGETS = intArrayOf(
            PhotoCompressor.MAX_BYTES,
            56 * 1024,
            44 * 1024
        )
    }
}
