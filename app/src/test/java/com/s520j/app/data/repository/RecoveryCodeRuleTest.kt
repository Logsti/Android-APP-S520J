package com.s520j.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 密码规则的行为特征测试（技术文档 §3：8–32 位字母数字，区分大小写）。 */
class RecoveryCodeRuleTest {

    @Test
    fun `empty code reports missing input`() {
        assertEquals("请输入密码", RecoveryCodeRule.validate(""))
    }

    @Test
    fun `seven characters rejected as too short`() {
        assertEquals("密码至少 8 位", RecoveryCodeRule.validate("Abc1234"))
    }

    @Test
    fun `thirty three characters rejected as too long`() {
        val code = "A".repeat(33)
        assertEquals("密码最多 32 位", RecoveryCodeRule.validate(code))
    }

    @Test
    fun `non alphanumeric characters rejected`() {
        assertEquals("密码只能包含字母和数字", RecoveryCodeRule.validate("abc@1234"))
    }

    @Test
    fun `space inside code rejected`() {
        assertEquals("密码只能包含字母和数字", RecoveryCodeRule.validate("abc 1234"))
    }

    @Test
    fun `chinese characters rejected`() {
        assertEquals("密码只能包含字母和数字", RecoveryCodeRule.validate("密码abcd1234"))
    }

    @Test
    fun `eight characters accepted`() {
        assertNull(RecoveryCodeRule.validate("Abc12345"))
    }

    @Test
    fun `thirty two characters accepted`() {
        assertNull(RecoveryCodeRule.validate("A".repeat(32)))
    }

    @Test
    fun `letters are case sensitive and both accepted`() {
        assertNull(RecoveryCodeRule.validate("abc12345"))
        assertNull(RecoveryCodeRule.validate("ABC12345"))
    }
}
