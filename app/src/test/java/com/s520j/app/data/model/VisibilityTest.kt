package com.s520j.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibilityTest {

    @Test
    fun `from 支持大写 COUPLE`() {
        assertEquals(Visibility.COUPLE, Visibility.from("COUPLE"))
    }

    @Test
    fun `from 支持小写 couple`() {
        assertEquals(Visibility.COUPLE, Visibility.from("couple"))
    }

    @Test
    fun `from 未知值回退为 PRIVATE`() {
        assertEquals(Visibility.PRIVATE, Visibility.from("unknown"))
        assertEquals(Visibility.PRIVATE, Visibility.from(null))
    }
}
