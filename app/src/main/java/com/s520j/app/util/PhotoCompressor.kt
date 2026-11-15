package com.s520j.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 记录图片压缩。
 *
 * 关键约束：CloudBase HTTP 访问服务的**单次请求体上限约 100KB**
 * （实测 80KB 原图 base64 后即被 413 EXCEED_MAX_PAYLOAD_SIZE 拒绝），
 * base64 编码还会膨胀 4/3，因此原图必须压到 68KB 以内才能一次上传成功。
 * 逐级降低边长与质量，取第一张满足预算的 JPEG。
 */
object PhotoCompressor {

    const val MAX_EDGE = 1600

    /** 单张压缩后字节预算：68KB → base64 约 91KB，加上 JSON 外壳仍 < 100KB。 */
    const val MAX_BYTES = 68 * 1024

    private val EDGES = intArrayOf(1600, 1280, 1080, 900, 720, 600, 480)
    private val QUALITIES = intArrayOf(72, 62, 52, 44, 36, 30)

    /** 返回 (base64 不含前缀, 字节数)；解码失败返回 null。 */
    suspend fun toBase64(
        context: Context,
        uri: Uri,
        budget: Int = MAX_BYTES
    ): Pair<String, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            // 只开一次流：部分 content URI（picker / 分享）不可重复读。
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            if (bytes.isEmpty()) return@runCatching null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null

            val sampled = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize(width, height) }
            ) ?: return@runCatching null

            val compressed = compressToBudget(sampled, budget)
            sampled.recycle()
            val result = compressed ?: return@runCatching null
            android.util.Base64.encodeToString(result, android.util.Base64.NO_WRAP) to result.size
        }.getOrNull()
    }

    /**
     * 逐级降边长、再逐级降质量，返回第一张 ≤ budget 的 JPEG；
     * 所有组合都超预算时返回其中最小的一张（调用方可继续降预算重试）。
     */
    internal fun compressToBudget(src: Bitmap, budget: Int = MAX_BYTES): ByteArray? {
        var smallest: ByteArray? = null
        for (edge in EDGES) {
            val target = scaleToEdge(src, edge)
            var fit: ByteArray? = null
            for (quality in QUALITIES) {
                val out = ByteArrayOutputStream()
                target.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val candidate = out.toByteArray()
                if (smallest == null || candidate.size < smallest.size) smallest = candidate
                if (candidate.size <= budget) {
                    fit = candidate
                    break
                }
            }
            if (target !== src) target.recycle()
            if (fit != null) return fit
        }
        return smallest
    }

    internal fun sampleSize(width: Int, height: Int): Int {
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

    internal fun targetSize(width: Int, height: Int, max: Int = MAX_EDGE): Pair<Int, Int> {
        if (width <= max && height <= max) return width to height
        val ratio = max.toFloat() / maxOf(width, height)
        return (width * ratio).toInt().coerceAtLeast(1) to
            (height * ratio).toInt().coerceAtLeast(1)
    }

    private fun scaleToEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val (w, h) = targetSize(src.width, src.height, maxEdge)
        if (w == src.width && h == src.height) return src
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
