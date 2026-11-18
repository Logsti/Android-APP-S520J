package com.s520j.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 点菜页自定义头图：相册选图后压缩并复制到 APP 私有目录。
 * 只保存图片与路径，不上传云端；恢复默认时删除副本。
 */
@Singleton
class DishHeaderStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dir: File get() = File(context.filesDir, DIR_NAME)
    private val file: File get() = File(dir, FILE_NAME)

    /** 复制并压缩为最长边 1600px 的 JPEG；失败返回 null。 */
    suspend fun save(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    .also { BitmapFactory.decodeStream(stream, null, it) }
            } ?: return@runCatching null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                    }
                )
            } ?: return@runCatching null

            val scaled = scaleToMax(sampled, MAX_EDGE)
            if (scaled !== sampled) sampled.recycle()
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
            scaled.recycle()

            dir.mkdirs()
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    /** 恢复默认：删除自定义副本。 */
    suspend fun deleteCustom(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (file.exists()) file.delete() else true
        }.getOrDefault(false)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w > MAX_EDGE * 2 || h > MAX_EDGE * 2) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    private fun scaleToMax(src: Bitmap, max: Int): Bitmap {
        if (src.width <= max && src.height <= max) return src
        val ratio = max.toFloat() / maxOf(src.width, src.height)
        val width = (src.width * ratio).toInt().coerceAtLeast(1)
        val height = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, width, height, true)
    }

    private companion object {
        const val DIR_NAME = "dish_header"
        const val FILE_NAME = "header.jpg"
        const val MAX_EDGE = 1600
    }
}
