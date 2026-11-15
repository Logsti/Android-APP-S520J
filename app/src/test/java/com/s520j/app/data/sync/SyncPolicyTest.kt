package com.s520j.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 9.3 同步可见化：自动同步节流、状态条文案、未绑定时的共同可见内容延后推送。 */
class SyncPolicyTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `从未同步过时立即同步`() {
        assertTrue(SyncPolicy.shouldAutoSync(lastAttemptAt = 0L, now = now, force = false))
    }

    @Test
    fun `距上次不足 20 秒不再同步`() {
        assertFalse(
            SyncPolicy.shouldAutoSync(lastAttemptAt = now - 19_999L, now = now, force = false)
        )
    }

    @Test
    fun `距上次满 20 秒触发同步`() {
        assertTrue(
            SyncPolicy.shouldAutoSync(lastAttemptAt = now - 20_000L, now = now, force = false)
        )
    }

    @Test
    fun `强制同步不受节流限制`() {
        assertTrue(
            SyncPolicy.shouldAutoSync(lastAttemptAt = now - 1L, now = now, force = true)
        )
    }

    @Test
    fun `时钟回拨时不自动同步`() {
        assertFalse(
            SyncPolicy.shouldAutoSync(lastAttemptAt = now + 60_000L, now = now, force = false)
        )
    }

    @Test
    fun `离线状态提示可点击重试`() {
        assertEquals("当前离线，云端同步暂停 · 点击重试", SyncPolicy.statusBarText(SyncPhase.OFFLINE))
    }

    @Test
    fun `失败状态提示可点击重试`() {
        assertEquals("同步失败 · 点击重试", SyncPolicy.statusBarText(SyncPhase.FAILED))
    }

    @Test
    fun `空闲和同步中不显示状态条`() {
        assertNull(SyncPolicy.statusBarText(SyncPhase.IDLE))
        assertNull(SyncPolicy.statusBarText(SyncPhase.SYNCING))
    }

    @Test
    fun `共同可见内容在未绑定时延后推送`() {
        assertTrue(SyncPolicy.shouldDeferPush(visibility = "couple", spaceId = null))
        assertTrue(SyncPolicy.shouldDeferPush(visibility = "couple", spaceId = ""))
    }

    @Test
    fun `共同可见内容已绑定后正常推送`() {
        assertFalse(SyncPolicy.shouldDeferPush(visibility = "couple", spaceId = "sp-1"))
    }

    @Test
    fun `私有内容不受空间绑定影响`() {
        assertFalse(SyncPolicy.shouldDeferPush(visibility = "private", spaceId = null))
    }

    @Test
    fun `大写 COUPLE 也应正确判断延后推送`() {
        assertTrue(SyncPolicy.shouldDeferPush(visibility = "COUPLE", spaceId = null))
        assertFalse(SyncPolicy.shouldDeferPush(visibility = "COUPLE", spaceId = "sp-1"))
    }

    @Test
    fun `同一账号连续三次自动失败且有待上传才提醒一次`() {
        var track = SyncFailureTrack(accountId = "user-a")
        var update = SyncPolicy.autoSyncFailed(track, "user-a", "timeout", pendingUpload = true)
        track = update.track
        assertFalse(update.shouldAlert)
        update = SyncPolicy.autoSyncFailed(track, "user-a", "timeout", pendingUpload = true)
        track = update.track
        assertFalse(update.shouldAlert)
        update = SyncPolicy.autoSyncFailed(track, "user-a", "timeout", pendingUpload = true)
        track = update.track
        assertTrue(update.shouldAlert)
        assertTrue(track.alerted)

        update = SyncPolicy.autoSyncFailed(track, "user-a", "timeout", pendingUpload = true)
        assertFalse("恢复前同签名不得重复提醒", update.shouldAlert)
    }

    @Test
    fun `连续失败没有待上传内容时不提醒`() {
        var track = SyncFailureTrack(accountId = "user-a")
        repeat(3) {
            track = SyncPolicy.autoSyncFailed(track, "user-a", "offline", pendingUpload = false).track
        }
        val update = SyncPolicy.autoSyncFailed(track, "user-a", "offline", pendingUpload = false)
        assertFalse(update.shouldAlert)
    }

    @Test
    fun `同步恢复后相同签名的新持续失败可以再次提醒`() {
        var track = SyncFailureTrack(accountId = "user-a")
        repeat(3) {
            track = SyncPolicy.autoSyncFailed(track, "user-a", "timeout", pendingUpload = true).track
        }
        track = SyncPolicy.syncRecovered(track, "user-a")
        assertEquals(0, track.consecutive)
        assertFalse(track.alerted)

        repeat(3) {
            val update = SyncPolicy.autoSyncFailed(track, "user-a", "timeout", pendingUpload = true)
            track = update.track
            if (update.track.consecutive == 3) assertTrue(update.shouldAlert)
        }
        assertEquals(1L, track.generation)
    }

    @Test
    fun `同一账号失败签名变化仍累计连续次数但账号变化会重新计算`() {
        var track = SyncFailureTrack(accountId = "user-a")
        repeat(2) {
            track = SyncPolicy.autoSyncFailed(track, "user-a", "timeout", pendingUpload = true).track
        }
        var update = SyncPolicy.autoSyncFailed(track, "user-a", "http-500", pendingUpload = true)
        assertEquals(3, update.track.consecutive)
        assertTrue(update.shouldAlert)
        update = SyncPolicy.autoSyncFailed(update.track, "user-b", "http-500", pendingUpload = true)
        assertEquals(1, update.track.consecutive)
        assertEquals("user-b", update.track.accountId)
    }

    @Test
    fun `账号权限异常可分类且空间冻结不生成本地异常`() {
        assertEquals("登录已失效", SyncPolicy.requiredAccountAlert(401, "登录已过期")?.title)
        assertEquals("账号不可用", SyncPolicy.requiredAccountAlert(400, "账号不存在或已删除")?.title)
        assertEquals("同步权限需要检查", SyncPolicy.requiredAccountAlert(403, "只有共同空间成员可访问")?.title)
        assertNull(SyncPolicy.requiredAccountAlert(403, "共同空间已冻结归档，仅可查看与导出"))
        assertTrue(SyncPolicy.isSpaceFrozenFailure(403, "共同空间已冻结归档"))
        assertNull(SyncPolicy.requiredAccountAlert(500, "服务暂时不可用"))
    }
}
