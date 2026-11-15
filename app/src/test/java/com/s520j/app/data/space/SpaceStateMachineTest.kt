package com.s520j.app.data.space

import com.s520j.app.data.local.BindState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 七态绑定状态机的边界用例（纯 JVM，时间全部由参数注入）。 */
class SpaceStateMachineTest {

    private val ttl = SpaceStateMachine.BIND_CODE_TTL_MS

    @Test
    fun afterCreateGoesToInviteGenerated() {
        assertEquals(BindState.INVITE_GENERATED, SpaceStateMachine.afterCreate())
    }

    @Test
    fun afterShareGoesToWaitingPartner() {
        assertEquals(BindState.WAITING_PARTNER, SpaceStateMachine.afterShare())
    }

    @Test
    fun bindCodeValidInside24Hours() {
        val issuedAt = 1_000L
        assertFalse(SpaceStateMachine.isBindCodeExpired(issuedAt + ttl, issuedAt + ttl - 1))
    }

    @Test
    fun bindCodeExpiredExactlyAt24Hours() {
        val issuedAt = 1_000L
        assertTrue(SpaceStateMachine.isBindCodeExpired(issuedAt + ttl, issuedAt + ttl))
    }

    @Test
    fun bindCodeExpiredAfter24Hours() {
        val issuedAt = 1_000L
        assertTrue(SpaceStateMachine.isBindCodeExpired(issuedAt + ttl, issuedAt + ttl + 1))
    }

    @Test
    fun bindCodeWithoutExpiryNeverExpires() {
        assertFalse(SpaceStateMachine.isBindCodeExpired(null, Long.MAX_VALUE / 2))
    }

    @Test
    fun submitSuccessGoesToRequestPendingWithoutError() {
        val (state, error) = SpaceStateMachine.afterSubmit(expired = false, occupied = false)
        assertEquals(BindState.REQUEST_PENDING, state)
        assertNull(error)
    }

    @Test
    fun submitExpiredGoesToFailedWithMessage() {
        val (state, error) = SpaceStateMachine.afterSubmit(expired = true, occupied = false)
        assertEquals(BindState.FAILED, state)
        assertEquals("绑定码已过期，请让对方重新生成", error)
    }

    @Test
    fun submitOccupiedGoesToFailedWithMessage() {
        val (state, error) = SpaceStateMachine.afterSubmit(expired = false, occupied = true)
        assertEquals(BindState.FAILED, state)
        assertEquals("该空间已满，一个空间最多两人", error)
    }

    @Test
    fun singleConfirmKeepsBindRequestPending() {
        assertEquals(
            BindState.REQUEST_PENDING,
            SpaceStateMachine.afterConfirm(PendingAction.BIND, ownerConfirmed = true, partnerConfirmed = false)
        )
        assertEquals(
            BindState.REQUEST_PENDING,
            SpaceStateMachine.afterConfirm(PendingAction.BIND, ownerConfirmed = false, partnerConfirmed = true)
        )
    }

    @Test
    fun bothConfirmsCompleteBind() {
        assertEquals(
            BindState.BOUND,
            SpaceStateMachine.afterConfirm(PendingAction.BIND, ownerConfirmed = true, partnerConfirmed = true)
        )
    }

    @Test
    fun unbindRequestKeepsRequestPendingBeforeAnyConfirm() {
        assertEquals(
            BindState.REQUEST_PENDING,
            SpaceStateMachine.afterConfirm(PendingAction.UNBIND, ownerConfirmed = false, partnerConfirmed = false)
        )
    }

    @Test
    fun singleUnbindConfirmKeepsRequestPending() {
        assertEquals(
            BindState.REQUEST_PENDING,
            SpaceStateMachine.afterConfirm(PendingAction.UNBIND, ownerConfirmed = true, partnerConfirmed = false)
        )
        assertEquals(
            BindState.REQUEST_PENDING,
            SpaceStateMachine.afterConfirm(PendingAction.UNBIND, ownerConfirmed = false, partnerConfirmed = true)
        )
    }

    @Test
    fun bothUnbindConfirmsFreezeSpace() {
        assertEquals(
            BindState.FROZEN,
            SpaceStateMachine.afterConfirm(PendingAction.UNBIND, ownerConfirmed = true, partnerConfirmed = true)
        )
    }

    @Test
    fun rebindGoesBackToBound() {
        assertEquals(BindState.BOUND, SpaceStateMachine.afterRebind())
    }

    @Test
    fun pendingActionFromRawString() {
        assertEquals(PendingAction.BIND, PendingAction.from("BIND"))
        assertEquals(PendingAction.UNBIND, PendingAction.from("UNBIND"))
        assertNull(PendingAction.from(null))
        assertNull(PendingAction.from("OTHER"))
    }

    @Test
    fun frozenSpaceIsReadOnly() {
        assertFalse(SpaceStateMachine.canEdit(BindState.FROZEN))
    }

    @Test
    fun otherStatesAreEditable() {
        assertTrue(SpaceStateMachine.canEdit(BindState.BOUND))
        assertTrue(SpaceStateMachine.canEdit(BindState.REQUEST_PENDING))
        assertTrue(SpaceStateMachine.canEdit(BindState.INVITE_GENERATED))
        assertTrue(SpaceStateMachine.canEdit(null))
    }

    @Test
    fun exportIsAlwaysAllowed() {
        BindState.entries.forEach { assertTrue(SpaceStateMachine.canExport(it)) }
        assertTrue(SpaceStateMachine.canExport(null))
    }

    @Test
    fun createIsAllowedWhenUnboundOrAfterFailure() {
        assertTrue(SpaceStateMachine.canCreate(null))
        assertTrue(SpaceStateMachine.canCreate(BindState.UNBOUND))
        assertTrue(SpaceStateMachine.canCreate(BindState.FAILED))
        // 已生成绑定码 / 等待对方 / 待确认 / 已绑定 / 已冻结都不允许再创建。
        BindState.entries
            .filter { it != BindState.UNBOUND && it != BindState.FAILED }
            .forEach { assertFalse(SpaceStateMachine.canCreate(it)) }
    }

    @Test
    fun stateOfParsesPersistedValue() {
        assertEquals(BindState.BOUND, SpaceStateMachine.stateOf("BOUND"))
        assertEquals(BindState.FROZEN, SpaceStateMachine.stateOf("FROZEN"))
        assertNull(SpaceStateMachine.stateOf("unknown"))
    }
}
