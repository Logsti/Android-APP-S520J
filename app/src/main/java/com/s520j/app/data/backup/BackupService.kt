package com.s520j.app.data.backup

import android.content.Context
import android.net.Uri
import com.s520j.app.data.local.FieldCodec
import com.s520j.app.data.local.S520JDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本机备份：成长记录 JSON 落盘到 filesDir/backups，支持创建、列出、恢复、删除；
 * 另外提供「JSON + 图片」ZIP 导出（通过系统文件选择器保存，不加密）。
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: S520JDatabase
) {
    data class BackupInfo(val name: String, val createdAt: Long, val bytes: Long)

    private fun dir(): File = File(context.filesDir, "backups").apply { mkdirs() }

    fun list(): List<BackupInfo> =
        dir().listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { file ->
                BackupInfo(
                    name = file.name,
                    createdAt = file.name.removePrefix("s520j-backup-").removeSuffix(".json")
                        .toLongOrNull() ?: file.lastModified(),
                    bytes = file.length()
                )
            }
            ?.sortedByDescending { it.createdAt }
            .orEmpty()

    suspend fun create(): BackupInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val records = db.contentDao().listAll()
        val file = File(dir(), BackupCodec.fileName(now))
        file.writeText(BackupCodec.encode(records, now), Charsets.UTF_8)
        BackupInfo(name = file.name, createdAt = now, bytes = file.length())
    }

    /** 恢复备份：按 id 覆盖写入，返回恢复条数；文件无效返回 -1。 */
    suspend fun restore(name: String): Int = withContext(Dispatchers.IO) {
        val file = File(dir(), name)
        if (!file.exists()) return@withContext -1
        val records = BackupCodec.decode(file.readText(Charsets.UTF_8)) ?: return@withContext -1
        if (records.isNotEmpty()) db.contentDao().upsertAll(records)
        records.size
    }

    fun delete(name: String): Boolean = File(dir(), name).delete()

    /** 仅清理本机内容，云端与备份文件不受影响。 */
    suspend fun clearLocal(): Boolean = withContext(Dispatchers.IO) {
        runCatching { db.contentDao().clearAll() }.isSuccess
    }

    /** 导出 ZIP：data.json + images/（本机可读的原始图片与云端缓存图片）。 */
    suspend fun exportZip(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val records = db.contentDao().listAll()
            val json = BackupCodec.encode(records, System.currentTimeMillis())
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                    zip.nextEntry("data.json", json.toByteArray(Charsets.UTF_8))
                    records.forEach { record ->
                        FieldCodec.decodeList(record.mediaIds).forEachIndexed { index, path ->
                            val bytes = readImage(path) ?: return@forEachIndexed
                            zip.nextEntry("images/${record.id}-$index.jpg", bytes)
                        }
                    }
                    File(context.filesDir, "media").listFiles()
                        ?.filter { it.isFile && it.length() > 0 }
                        ?.forEach { file -> zip.nextEntry("images/cache-${file.name}", file.readBytes()) }
                }
                true
            } ?: false
        }.getOrDefault(false)
    }

    private fun ZipOutputStream.nextEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun readImage(path: String): ByteArray? = runCatching {
        if (path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
        } else {
            File(path).takeIf { it.exists() }?.readBytes()
        }
    }.getOrNull()
}
