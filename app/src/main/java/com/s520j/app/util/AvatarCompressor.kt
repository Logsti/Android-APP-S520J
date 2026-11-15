package com.s520j.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把用户选择的图片 URI 压缩成 ≤256px 的 JPEG base64 数据 URL，供云端头像存储。
 * 256px JPEG 80 质量约 10~30KB，远小于服务端 2MB 上限。
 */
object AvatarCompressor {

    private const val MAX_PX = 256
    private const val JPEG_QUALITY = 80

    suspend fun toDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            // 1. 只读边界，避免大图直接解码进内存 OOM。
            val (width, height) = context.contentResolver.openInputStream(uri)?.use { stream ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, bounds)
                bounds.outWidth to bounds.outHeight
            } ?: return@runCatching null
            if (width <= 0 || height <= 0) return@runCatching null

            // 2. 用 inSampleSize 粗采样后再缩放，节省内存。
            val sample = calculateInSampleSize(width, height)
            val sampled = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                })
            } ?: return@runCatching null

            // 3. 精确缩放到 ≤256px。
            val scaled = scaleToMax(sampled, MAX_PX)
            if (scaled !== sampled) sampled.recycle()

            // 4. JPEG 压缩 + base64。
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
            scaled.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    internal fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        // 目标降到 MAX_PX*2 以内，再由 createScaledBitmap 精确缩放。
        while (w > MAX_PX * 2 || h > MAX_PX * 2) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    /** 等比缩放到最长边不超过 max，返回 (宽, 高)，至少 1px。 */
    internal fun targetSize(width: Int, height: Int, max: Int = MAX_PX): Pair<Int, Int> {
        if (width <= max && height <= max) return width to height
        val ratio = max.toFloat() / maxOf(width, height)
        return (width * ratio).toInt().coerceAtLeast(1) to
            (height * ratio).toInt().coerceAtLeast(1)
    }

    private fun scaleToMax(src: Bitmap, max: Int): Bitmap {
        if (src.width <= max && src.height <= max) return src
        val (targetW, targetH) = targetSize(src.width, src.height, max)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }
}
