package com.s520j.app.data.space

import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.SpaceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpaceMirrorTest {

    private val self = "user-a"
    private val now = 5_000L

    private fun remote(
        members: List<SpaceMirror.Member> = listOf(SpaceMirror.Member(self, "A", "creator")),
        status: String = "active",
        codeExpiresAt: Long? = null,
        pending: SpaceMirror.BindRequest? = null
    ) = SpaceMirror.Remote(
        id = "space-cloud",
        name = "我们的小屋",
        intro = "一起记录",
        status = status,
        createdBy = "user-a",
        members = members,
        codeExpiresAt = codeExpiresAt,
        pending = pending
    )

    private fun local(
        id: String = "space-cloud",
        bindCode: String? = "ABC123",
        expiresAt: Long? = 9_000L,
        bindState: BindState = BindState.INVITE_GENERATED
    ) = SpaceEntity(
        id = id,
        name = "旧名字",
        intro = "旧简介",
        status = "ACTIVE",
        bindState = bindState.name,
        bindCode = bindCode,
        bindCodeExpiresAt = expiresAt,
        createdBy = "user-a",
        partnerUserId = null,
        createdAt = 1_000L,
        updatedAt = 2_000L
    )

    @Test
    fun `双人空间映射为已绑定并记录对方`() {
        val merged = SpaceMirror.merge(
            remote = remote(
                members = listOf(
                    SpaceMirror.Member(self, "A", "creator"),
                    SpaceMirror.Member("user-b", "B", "partner")
                )
            ),
            existing = null,
            selfId = self,
            now = now
        )
        assertEquals(BindState.BOUND.name, merged.bindState)
        assertEquals("user-b", merged.partnerUserId)
        assertEquals("我们的小屋", merged.name)
        assertNull(merged.bindCode)
    }

    @Test
    fun `待确认申请映射为绑定申请中并保存申请 id`() {
        val merged = SpaceMirror.merge(
            remote = remote(
                pending = SpaceMirror.BindRequest(
                    id = "req-1", fromUser = "user-b", toUser = self,
                    status = "pending", confirmedByB = "user-b"
                )
            ),
            existing = null,
            selfId = self,
            now = now
        )
        assertEquals(BindState.REQUEST_PENDING.name, merged.bindState)
        assertEquals("req-1", merged.bindRequestId)
        assertEquals("user-b", merged.partnerUserId)
        assertEquals(PendingAction.BIND.name, merged.pendingAction)
        assertNull(merged.ownerConfirmedAt)
        assertEquals(1L, merged.partnerConfirmedAt)
    }

    @Test
    fun `待确认解绑映射为解绑申请中并记录发起方`() {
        val merged = SpaceMirror.merge(
            remote = remote(
                members = listOf(
                    SpaceMirror.Member(self, "A", "creator"),
                    SpaceMirror.Member("user-b", "B", "partner")
                ),
                pending = SpaceMirror.BindRequest(
                    id = "unbind-1", fromUser = "user-b", toUser = self,
                    status = "unbind_pending"
                )
            ),
            existing = null,
            selfId = self,
            now = now
        )
        assertEquals(BindState.REQUEST_PENDING.name, merged.bindState)
        assertEquals(PendingAction.UNBIND.name, merged.pendingAction)
        assertEquals("user-b", merged.unbindRequestedBy)
        assertEquals("unbind-1", merged.bindRequestId)
        assertEquals("user-b", merged.partnerUserId)
        assertNull(merged.ownerConfirmedAt)
        assertNull(merged.partnerConfirmedAt)
    }

    @Test
    fun `解绑申请被拒绝或撤回后双人空间回到已绑定`() {
        val merged = SpaceMirror.merge(
            remote = remote(
                members = listOf(
                    SpaceMirror.Member(self, "A", "creator"),
                    SpaceMirror.Member("user-b", "B", "partner")
                ),
                pending = null
            ),
            existing = local(bindState = BindState.REQUEST_PENDING).copy(
                partnerUserId = "user-b",
                pendingAction = PendingAction.UNBIND.name,
                bindRequestId = "unbind-1"
            ),
            selfId = self,
            now = now
        )
        assertEquals(BindState.BOUND.name, merged.bindState)
        assertEquals("ACTIVE", merged.status)
        assertEquals("user-b", merged.partnerUserId)
        assertNull(merged.pendingAction)
        assertNull(merged.bindRequestId)
    }

    @Test
    fun `绑定申请仍映射为绑定动作而不受解绑分支影响`() {
        val merged = SpaceMirror.merge(
            remote = remote(
                pending = SpaceMirror.BindRequest(
                    id = "req-2", fromUser = self, toUser = "user-b",
                    status = "pending"
                )
            ),
            existing = null,
            selfId = self,
            now = now
        )
        assertEquals(BindState.REQUEST_PENDING.name, merged.bindState)
        assertEquals(PendingAction.BIND.name, merged.pendingAction)
        assertEquals("req-2", merged.bindRequestId)
        assertEquals(self, merged.bindRequestedBy)
        assertNull(merged.unbindRequestedBy)
    }

    @Test
    fun `冻结空间映射为冻结归档`() {
        val merged = SpaceMirror.merge(
            remote = remote(status = "frozen"),
            existing = local(),
            selfId = self,
            now = now
        )
        assertEquals(BindState.FROZEN.name, merged.bindState)
        assertEquals("FROZEN", merged.status)
    }

    @Test
    fun `单人空间保留本地已生成的绑定码`() {
        val merged = SpaceMirror.merge(
            remote = remote(codeExpiresAt = 8_000L),
            existing = local(),
            selfId = self,
            now = now
        )
        assertEquals(BindState.INVITE_GENERATED.name, merged.bindState)
        assertEquals("ABC123", merged.bindCode)
        assertEquals(8_000L, merged.bindCodeExpiresAt)
    }

    @Test
    fun `云端未下发有效期时沿用本地绑定码与状态`() {
        val merged = SpaceMirror.merge(
            remote = remote(),
            existing = local(bindState = BindState.WAITING_PARTNER),
            selfId = self,
            now = now
        )
        assertEquals(BindState.WAITING_PARTNER.name, merged.bindState)
        assertEquals("ABC123", merged.bindCode)
    }

    @Test
    fun `无本地空间且无绑定码时回到未绑定`() {
        val merged = SpaceMirror.merge(
            remote = remote(),
            existing = null,
            selfId = self,
            now = now
        )
        assertEquals(BindState.UNBOUND.name, merged.bindState)
        assertNull(merged.bindCode)
    }
}
