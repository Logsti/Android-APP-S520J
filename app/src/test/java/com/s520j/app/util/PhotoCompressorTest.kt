package com.s520j.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 记录图片压缩的尺寸策略：最长边 1600px、等比、不放大。 */
class PhotoCompressorTest {

    @Test
    fun `小图不放大`() {
        assertEquals(800 to 600, PhotoCompressor.targetSize(800, 600))
    }

    @Test
    fun `横图最长边缩到 1600`() {
        assertEquals(1600 to 1200, PhotoCompressor.targetSize(4000, 3000))
    }

    @Test
    fun `竖图最长边缩到 1600`() {
        assertEquals(1200 to 1600, PhotoCompressor.targetSize(3000, 4000))
    }

    @Test
    fun `采样率保证内存可控`() {
        val sample = PhotoCompressor.sampleSize(6000, 4000)
        assertEquals(true, 6000 / sample <= PhotoCompressor.MAX_EDGE * 2)
        assertEquals(true, 4000 / sample <= PhotoCompressor.MAX_EDGE * 2)
        assertEquals(1, PhotoCompressor.sampleSize(1600, 1600))
    }

    @Test
    fun `压缩预算保证单次上传请求体小于网关上限`() {
        // 云端 HTTP 网关单次请求体上限实测约 100KB；base64 膨胀 4/3。
        assertEquals(68 * 1024, PhotoCompressor.MAX_BYTES)
        val raw = PhotoCompressor.MAX_BYTES
        val base64Len = ((raw + 2) / 3) * 4
        assertEquals(true, base64Len + 512 < 100 * 1024)
    }
}
