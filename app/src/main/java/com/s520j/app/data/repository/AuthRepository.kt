package com.s520j.app.data.repository

import com.s520j.app.data.CoupleStartDate
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.UserEntity
import com.s520j.app.data.prefs.SessionProfile
import com.s520j.app.data.prefs.UserPreferences
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data class Success(val accountId: String, val nickname: String) : AuthResult
    data class Failure(val message: String) : AuthResult
}

/** 密码规则（用户界面叫「密码」，内部实现沿用恢复码命名）：8–32 位字母数字，区分大小写。 */
object RecoveryCodeRule {
    private val valid = Regex("[A-Za-z0-9]{8,32}")

    fun validate(code: String): String? = when {
        code.isEmpty() -> "请输入密码"
        code.length < 8 -> "密码至少 8 位"
        code.length > 32 -> "密码最多 32 位"
        !valid.matches(code) -> "密码只能包含字母和数字"
        else -> null
    }
}

/** 账号 ID 规则：用户可自行设置，8–20 位字母数字。 */
object AccountIdRule {
    private val valid = Regex("[A-Za-z0-9]{8,20}")

    fun validate(id: String): String? = when {
        id.isEmpty() -> "请输入账号 ID"
        id.length < 8 -> "账号 ID 至少 8 位"
        id.length > 20 -> "账号 ID 最多 20 位"
        !valid.matches(id) -> "账号 ID 只能包含字母和数字"
        else -> null
    }
}

@Singleton
class AuthRepository @Inject constructor(
    private val db: S520JDatabase,
    private val prefs: UserPreferences
) {
    private val userDao get() = db.userDao()

    suspend fun register(
        accountIdRaw: String,
        nickname: String,
        recoveryCode: String,
        cloudUserId: String? = null
    ): AuthResult {
        val accountId = accountIdRaw.trim().uppercase()
        AccountIdRule.validate(accountId)?.let { return AuthResult.Failure(it) }
        val name = nickname.trim()
        if (name.isEmpty()) return AuthResult.Failure("请输入昵称")
        RecoveryCodeRule.validate(recoveryCode)?.let { return AuthResult.Failure(it) }
        if (userDao.countByAccountId(accountId) > 0) {
            return AuthResult.Failure("该账号 ID 已被占用，请换一个")
        }

        val salt = randomSalt()
        val entity = UserEntity(
            id = cloudUserId ?: "user-$accountId",
            nickname = name,
            accountId = accountId,
            recoverySalt = salt,
            recoveryHash = hash(recoveryCode, salt),
            avatar = null,
            coupleStartAt = CoupleStartDate.FIXED_COUPLE_START_MS,
            createdAt = System.currentTimeMillis()
        )
        userDao.upsert(entity)
        prefs.saveSession(
            SessionProfile(
                userId = entity.id,
                nickname = entity.nickname,
                accountId = entity.accountId,
                partnerNickname = "TA",
                coupleStartAt = entity.coupleStartAt,
                loggedIn = true
            )
        )
        return AuthResult.Success(accountId, name)
    }

    /**
     * 云端认证成功后落地本机账号：账号 ID 与密码以云端为准，
     * 保证断网时仍可用同密码本机登录，并以云端 userId 关联内容。
     */
    suspend fun adoptCloudAccount(
        accountIdRaw: String,
        nickname: String,
        recoveryCode: String,
        cloudUserId: String
    ): AuthResult {
        val accountId = accountIdRaw.trim().uppercase()
        AccountIdRule.validate(accountId)?.let { return AuthResult.Failure(it) }
        RecoveryCodeRule.validate(recoveryCode)?.let { return AuthResult.Failure(it) }
        val name = nickname.trim().ifEmpty { accountId }
        val existing = userDao.findByAccountId(accountId)
        val salt = randomSalt()
        val entity = UserEntity(
            id = cloudUserId.ifBlank { existing?.id ?: "user-$accountId" },
            nickname = name,
            accountId = accountId,
            recoverySalt = salt,
            recoveryHash = hash(recoveryCode, salt),
            avatar = existing?.avatar,
            coupleStartAt = existing?.coupleStartAt ?: CoupleStartDate.FIXED_COUPLE_START_MS,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        userDao.upsert(entity)
        prefs.saveSession(
            SessionProfile(
                userId = entity.id,
                nickname = entity.nickname,
                accountId = entity.accountId,
                partnerNickname = "TA",
                coupleStartAt = entity.coupleStartAt,
                loggedIn = true
            )
        )
        return AuthResult.Success(accountId, name)
    }

    suspend fun login(accountId: String, recoveryCode: String): AuthResult {
        val id = accountId.trim().uppercase()
        if (id.isEmpty()) return AuthResult.Failure("请输入账号 ID")
        val user = userDao.findByAccountId(id) ?: return AuthResult.Failure("账号不存在，请检查账号 ID")
        if (hash(recoveryCode, user.recoverySalt) != user.recoveryHash) {
            return AuthResult.Failure("密码不正确")
        }
        prefs.saveSession(
            SessionProfile(
                userId = user.id,
                nickname = user.nickname,
                accountId = user.accountId,
                partnerNickname = "TA",
                coupleStartAt = user.coupleStartAt,
                loggedIn = true
            )
        )
        return AuthResult.Success(user.accountId, user.nickname)
    }

    suspend fun changeRecoveryCode(oldCode: String, newCode: String): AuthResult {
        if (oldCode.isEmpty()) return AuthResult.Failure("请输入旧密码")
        if (oldCode == newCode) return AuthResult.Failure("新旧密码不能相同")
        RecoveryCodeRule.validate(newCode)?.let { return AuthResult.Failure(it) }
        val user = userDao.currentUser() ?: return AuthResult.Failure("请先登录")
        if (hash(oldCode, user.recoverySalt) != user.recoveryHash) {
            return AuthResult.Failure("旧密码不正确")
        }
        val salt = randomSalt()
        userDao.upsert(user.copy(recoverySalt = salt, recoveryHash = hash(newCode, salt)))
        return AuthResult.Success(user.accountId, user.nickname)
    }

    suspend fun logout() = prefs.setLoggedIn(false)

    private fun randomSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** PBKDF2 加盐哈希：数据库中永不出现密码明文。 */
    fun hash(code: String, salt: String): String {
        val spec = PBEKeySpec(code.toCharArray(), salt.toByteArray(), 10_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }
}
