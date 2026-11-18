package com.s520j.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SnackbarTimingTest {

    @Test
    fun `普通提示两秒自动消失`() {
        assertEquals(2_000L, SnackbarTiming.forMessage(hasAction = false))
    }

    @Test
    fun `带动作提示保留四秒`() {
        assertEquals(4_000L, SnackbarTiming.forMessage(hasAction = true))
    }

    @Test
    fun `碰一碰与挑战横幅两秒消失`() {
        assertEquals(2_000L, SnackbarTiming.BANNER_MS)
    }
}
