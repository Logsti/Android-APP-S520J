package com.s520j.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalResetCoordinatorTest {

    @Test
    fun `首次升级先清账号偏好游标和outbox再写完成标记`() = runTest {
        val state = FakeLocalState(hasLegacyData = true)

        state.newResetCoordinator().ensureCompleted()

        assertTrue(state.completed)
        assertFalse(state.loggedIn)
        assertEquals("", state.localPreference)
        assertEquals(0L, state.syncCursor)
        assertTrue(state.users.isEmpty())
        assertTrue(state.outbox.isEmpty())
        assertEquals(listOf("clear:start", "clear:end", "mark"), state.events)
    }

    @Test
    fun `新协调器模拟重启后不重复清理新账号数据`() = runTest {
        val state = FakeLocalState(hasLegacyData = true)
        state.newResetCoordinator().ensureCompleted()
        state.users += "new-user"
        state.outbox += "new-operation"
        state.loggedIn = true
        state.localPreference = "new-preference"
        state.syncCursor = 8L

        state.newResetCoordinator().ensureCompleted()

        assertEquals(1, state.clearCount)
        assertEquals(listOf("new-user"), state.users)
        assertEquals(listOf("new-operation"), state.outbox)
        assertTrue(state.loggedIn)
        assertEquals("new-preference", state.localPreference)
        assertEquals(8L, state.syncCursor)
    }

    @Test
    fun `新安装重置完成后保持干净未登录状态`() = runTest {
        val state = FakeLocalState(hasLegacyData = false)

        state.newResetCoordinator().ensureCompleted()

        assertTrue(state.completed)
        assertFalse(state.loggedIn)
        assertTrue(state.users.isEmpty())
        assertTrue(state.outbox.isEmpty())
        assertEquals(0L, state.syncCursor)
    }

    @Test
    fun `同步等待重置完成因此旧outbox不会上传`() = runTest {
        val state = FakeLocalState(hasLegacyData = true)
        val uploaded = mutableListOf<String>()

        state.newResetCoordinator().ensureCompleted()
        state.events += "sync"
        uploaded += state.outbox

        assertTrue(uploaded.isEmpty())
        assertTrue(state.outbox.isEmpty())
        assertEquals(listOf("clear:start", "clear:end", "mark", "sync"), state.events)
    }

    private class FakeLocalState(hasLegacyData: Boolean) {
        var completed = false
        var loggedIn = hasLegacyData
        var localPreference = if (hasLegacyData) "legacy-preference" else ""
        var syncCursor = if (hasLegacyData) 99L else 0L
        var clearCount = 0
        val users = mutableListOf<String>().apply {
            if (hasLegacyData) add("legacy-user")
        }
        val outbox = mutableListOf<String>().apply {
            if (hasLegacyData) add("legacy-operation")
        }
        val events = mutableListOf<String>()

        fun newResetCoordinator() = OneTimeReset(
            isCompleted = { completed },
            clearLocalData = {
                events += "clear:start"
                clearCount++
                users.clear()
                outbox.clear()
                loggedIn = false
                localPreference = ""
                syncCursor = 0L
                events += "clear:end"
            },
            markCompleted = {
                events += "mark"
                completed = true
            }
        )
    }
}
