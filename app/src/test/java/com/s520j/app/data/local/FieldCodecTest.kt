package com.s520j.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FieldCodec 与 BindState 的特征测试基线（纯 JVM，不依赖 Android 运行时）。 */
class FieldCodecTest {

    @Test
    fun emptyListEncodesToEmptyString() {
        assertEquals("", FieldCodec.encodeList(emptyList()))
    }

    @Test
    fun blankDecodesToEmptyList() {
        assertTrue(FieldCodec.decodeList("").isEmpty())
        assertTrue(FieldCodec.decodeList("   ").isEmpty())
    }

    @Test
    fun listRoundTrip() {
        val source = listOf("media-a", "media-b", "media-c")
        assertEquals(source, FieldCodec.decodeList(FieldCodec.encodeList(source)))
    }

    @Test
    fun decodeListSkipsBlankItems() {
        assertEquals(listOf("a", "b"), FieldCodec.decodeList("a||b|"))
    }

    @Test
    fun emptyMapEncodesToEmptyString() {
        assertEquals("", FieldCodec.encodeMap(emptyMap()))
    }

    @Test
    fun blankDecodesToEmptyMap() {
        assertTrue(FieldCodec.decodeMap("").isEmpty())
        assertTrue(FieldCodec.decodeMap("  ").isEmpty())
    }

    @Test
    fun mapRoundTrip() {
        val source = mapOf("category" to "cook", "星级" to "5", "note" to "少放盐")
        assertEquals(source, FieldCodec.decodeMap(FieldCodec.encodeMap(source)))
    }

    @Test
    fun decodeMapIgnoresEntriesWithoutKey() {
        assertEquals(mapOf("k" to "v"), FieldCodec.decodeMap("=orphan;k=v"))
    }

    @Test
    fun decodeMapKeepsEqualsInsideValue() {
        assertEquals(mapOf("expr" to "a=b"), FieldCodec.decodeMap("expr=a=b"))
    }

    @Test
    fun bindStateHasSevenStatesWithLabels() {
        val expected = mapOf(
            BindState.UNBOUND to "未绑定",
            BindState.INVITE_GENERATED to "已生成邀请",
            BindState.WAITING_PARTNER to "等待对方加入",
            BindState.REQUEST_PENDING to "绑定申请中",
            BindState.BOUND to "已绑定",
            BindState.FAILED to "绑定失败",
            BindState.FROZEN to "冻结归档"
        )
        assertEquals(7, BindState.entries.size)
        expected.forEach { (state, label) -> assertEquals(label, state.label) }
    }
}
