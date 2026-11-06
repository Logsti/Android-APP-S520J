package com.s520j.app.data.repository

import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.UserDao
import com.s520j.app.data.local.UserEntity
import com.s520j.app.data.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AuthRepository.changeRecoveryCode 的行为特征测试（阶段 2「账号与密码闭环」）。
 * 用 mockk mock UserDao + S520JDatabase + UserPreferences，手动构造 AuthRepository。
 */
class ChangePasswordTest {

    private lateinit var db: S520JDatabase
    private lateinit var userDao: UserDao
    private lateinit var prefs: UserPreferences
    private lateinit var repo: AuthRepository

    private val salt = "testsalt"
    private val validOld = "OldPass123"
    private val validNew = "NewPass456"
    private lateinit var existingUser: UserEntity

    @Before
    fun setUp() {
        userDao = mockk(relaxed = true)
        db = mockk(relaxed = true)
        every { db.userDao() } returns userDao
        prefs = mockk(relaxed = true)
        repo = AuthRepository(db, prefs)

        existingUser = UserEntity(
            id = "user-test",
            nickname = "测试",
            accountId = "ABCDEFGH",
            recoverySalt = salt,
            recoveryHash = repo.hash(validOld, salt),
            avatar = null,
            coupleStartAt = 0L,
            createdAt = 0L
        )
    }

    // ---------- 新增校验场景 ----------

    @Test
    fun `oldCode 为空时返回 Failure 且消息包含旧密码`() = runTest {
        coEvery { userDao.currentUser() } returns existingUser
        val result = repo.changeRecoveryCode("", validNew)
        assertTrue(result is AuthResult.Failure)
        assertTrue((result as AuthResult.Failure).message.contains("旧密码"))
    }

    @Test
    fun `newCode 过短时返回 Failure 且消息包含密码至少`() = runTest {
        coEvery { userDao.currentUser() } returns existingUser
        val result = repo.changeRecoveryCode(validOld, "Abc12")
        assertTrue(result is AuthResult.Failure)
        assertTrue((result as AuthResult.Failure).message.contains("密码至少"))
    }

    @Test
    fun `newCode 含特殊字符时返回 Failure 且消息包含只能包含字母和数字`() = runTest {
        coEvery { userDao.currentUser() } returns existingUser
        val result = repo.changeRecoveryCode(validOld, "abc@1234")
        assertTrue(result is AuthResult.Failure)
        assertTrue((result as AuthResult.Failure).message.contains("只能包含字母和数字"))
    }

    @Test
    fun `newCode 与 oldCode 相同时返回 Failure 且消息包含新旧密码不能相同`() = runTest {
        coEvery { userDao.currentUser() } returns existingUser
        val result = repo.changeRecoveryCode(validOld, validOld)
        assertTrue(result is AuthResult.Failure)
        assertTrue((result as AuthResult.Failure).message.contains("新旧密码不能相同"))
    }

    @Test
    fun `oldCode 不匹配存储密码时返回 Failure 且消息包含旧密码不正确`() = runTest {
        coEvery { userDao.currentUser() } returns existingUser
        val result = repo.changeRecoveryCode("WrongPass1", validNew)
        assertTrue(result is AuthResult.Failure)
        assertTrue((result as AuthResult.Failure).message.contains("旧密码不正确"))
    }

    @Test
    fun `全部合法时返回 Success 且 upsert 被调用`() = runTest {
        coEvery { userDao.currentUser() } returns existingUser
        coEvery { userDao.upsert(any()) } returns Unit

        val result = repo.changeRecoveryCode(validOld, validNew)
        assertTrue(result is AuthResult.Success)
        // 验证 upsert 被调用，且 hash 已经改变（salt 改变导致 hash 变了，这里只验证调用发生）
        coVerify(exactly = 1) { userDao.upsert(any()) }
    }

    @Test
    fun `未登录时返回 Failure`() = runTest {
        coEvery { userDao.currentUser() } returns null
        val result = repo.changeRecoveryCode(validOld, validNew)
        assertTrue(result is AuthResult.Failure)
        assertEquals("请先登录", (result as AuthResult.Failure).message)
    }

    @Test
    fun `hash 方法对相同输入产生固定输出`() {
        val h1 = repo.hash("abc12345", "salt")
        val h2 = repo.hash("abc12345", "salt")
        assertEquals(h1, h2)
        assertNotNull(h1)
        assertTrue(h1.isNotBlank())
    }
}
