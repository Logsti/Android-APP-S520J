package com.s520j.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 头像压缩的尺寸计算：目标是 256px 内、等比、不放大。 */
class AvatarCompressorTest {

    @Test
    fun `小图不放大`() {
        assertEquals(100 to 100, AvatarCompressor.targetSize(100, 100))
        assertEquals(100 to 80, AvatarCompressor.targetSize(100, 80))
    }

    @Test
    fun `横图按最长边缩到 256`() {
        assertEquals(256 to 144, AvatarCompressor.targetSize(1920, 1080))
    }

    @Test
    fun `竖图按最长边缩到 256`() {
        assertEquals(144 to 256, AvatarCompressor.targetSize(1080, 1920))
    }

    @Test
    fun `极端比例也不会出现零尺寸`() {
        val (w, h) = AvatarCompressor.targetSize(10_000, 1)
        assertEquals(256, w)
        assertEquals(1, h)
    }

    @Test
    fun `采样率按 2 的幂递减到目标两倍以内`() {
        assertEquals(1, AvatarCompressor.calculateInSampleSize(256, 256))
        assertEquals(2, AvatarCompressor.calculateInSampleSize(1024, 1024))
        assertEquals(8, AvatarCompressor.calculateInSampleSize(4096, 4096))
        // 采样后尺寸仍需 <= 512，保证内存占用可控。
        val sample = AvatarCompressor.calculateInSampleSize(3000, 2000)
        assertEquals(true, 3000 / sample <= 512 && 2000 / sample <= 512)
    }
}
