package com.s520j.app.domain.heart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPhotoLayoutTest {

    @Test
    fun `一张图只占左上格`() {
        assertEquals(listOf(0), GalleryPhotoLayout.cells(1))
    }

    @Test
    fun `两张图走对角线`() {
        assertEquals(listOf(0, 3), GalleryPhotoLayout.cells(2))
    }

    @Test
    fun `三张图上排居中下排两张`() {
        assertEquals(listOf(1, 2, 3), GalleryPhotoLayout.cells(3))
    }

    @Test
    fun `四张图填满四宫格`() {
        assertEquals(listOf(0, 1, 2, 3), GalleryPhotoLayout.cells(4))
    }

    @Test
    fun `无图返回空格子`() {
        assertTrue(GalleryPhotoLayout.cells(0).isEmpty())
    }

    @Test
    fun `超过四张只取前四张对应的四格`() {
        assertEquals(listOf(0, 1, 2, 3), GalleryPhotoLayout.cells(5))
        assertEquals(listOf(0, 1, 2, 3), GalleryPhotoLayout.cells(9))
    }

    @Test
    fun `格子序号不重复且都在 0 到 3 之间`() {
        (0..8).forEach { count ->
            val cells = GalleryPhotoLayout.cells(count)
            assertEquals("count=$count 不应重复", cells.size, cells.distinct().size)
            assertTrue("count=$count 越界", cells.all { it in 0..3 })
        }
    }
}
