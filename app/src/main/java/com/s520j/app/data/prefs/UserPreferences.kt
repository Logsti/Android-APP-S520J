package com.s520j.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.s520j.app.data.CoupleStartDate
import com.s520j.app.data.model.Visibility
import com.s520j.app.domain.heart.PokeCatalog
import com.s520j.app.domain.us.ReminderSetting
import com.s520j.app.domain.us.ReminderSettingCodec
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "s520j_prefs")

data class SessionProfile(
    val userId: String,
    val nickname: String,
    val accountId: String,
    val partnerNickname: String,
    val coupleStartAt: Long,
    val loggedIn: Boolean
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val NICKNAME = stringPreferencesKey("nickname")
        val ACCOUNT_ID = stringPreferencesKey("account_id")
        val PARTNER_NICKNAME = stringPreferencesKey("partner_nickname")
        val COUPLE_START_AT = longPreferencesKey("couple_start_at")
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val GREETING = stringPreferencesKey("greeting")
        val CHALLENGE_STATE = stringPreferencesKey("challenge_state")
        val CUSTOM_POKES = stringPreferencesKey("custom_pokes")
        val TAXONOMY_V2_MIGRATED = booleanPreferencesKey("taxonomy_v2_migrated")
        val REMINDER_SETTING = stringPreferencesKey("reminder_setting")
        val DEFAULT_VISIBILITY = stringPreferencesKey("default_visibility")
        val MY_AVATAR_URI = stringPreferencesKey("my_avatar_uri")
        val PARTNER_AVATAR_URI = stringPreferencesKey("partner_avatar_uri")
    }

    val session: Flow<SessionProfile> = context.dataStore.data.map { p ->
        SessionProfile(
            userId = p[Keys.USER_ID] ?: "",
            nickname = p[Keys.NICKNAME] ?: "你",
            accountId = p[Keys.ACCOUNT_ID] ?: "",
            partnerNickname = p[Keys.PARTNER_NICKNAME] ?: "TA",
            coupleStartAt = p[Keys.COUPLE_START_AT] ?: CoupleStartDate.FIXED_COUPLE_START_MS,
            loggedIn = p[Keys.LOGGED_IN] ?: false
        )
    }

    val fontScale: Flow<Float> = context.dataStore.data.map { it[Keys.FONT_SCALE] ?: 1.0f }

    val greeting: Flow<String> = context.dataStore.data.map {
        it[Keys.GREETING] ?: "今天也要好好吃饭，好好想你。"
    }

    /** 今日挑战的按天状态（编码字符串，按天重置由领域层处理）。 */
    val challengeState: Flow<String?> = context.dataStore.data.map { it[Keys.CHALLENGE_STATE] }

    /** 自定义碰碰你动作（按顺序编码）。 */
    val customPokes: Flow<List<String>> = context.dataStore.data.map {
        PokeCatalog.decode(it[Keys.CUSTOM_POKES])
    }

    /** 本机提醒偏好（时间 + 三类开关）。 */
    val reminderSetting: Flow<ReminderSetting> = context.dataStore.data.map {
        ReminderSettingCodec.decode(it[Keys.REMINDER_SETTING])
    }

    /** 默认可见范围：未设置时按仅自己可见。 */
    val defaultVisibility: Flow<Visibility> = context.dataStore.data.map {
        Visibility.from(it[Keys.DEFAULT_VISIBILITY])
    }

    /** 我的头像图片 URI，未设置时为空。 */
    val myAvatar: Flow<String?> = context.dataStore.data.map { it[Keys.MY_AVATAR_URI] }

    /** 对方头像图片 URI，未设置时为空。 */
    val partnerAvatar: Flow<String?> = context.dataStore.data.map { it[Keys.PARTNER_AVATAR_URI] }

    suspend fun saveSession(profile: SessionProfile) {
        context.dataStore.edit { p ->
            p[Keys.USER_ID] = profile.userId
            p[Keys.NICKNAME] = profile.nickname
            p[Keys.ACCOUNT_ID] = profile.accountId
            p[Keys.PARTNER_NICKNAME] = profile.partnerNickname
            p[Keys.COUPLE_START_AT] = profile.coupleStartAt
            p[Keys.LOGGED_IN] = profile.loggedIn
        }
    }

    suspend fun setLoggedIn(value: Boolean) {
        context.dataStore.edit { it[Keys.LOGGED_IN] = value }
    }

    suspend fun setFontScale(value: Float) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = value.coerceIn(0.85f, 1.3f) }
    }

    suspend fun setGreeting(value: String) {
        context.dataStore.edit { it[Keys.GREETING] = value }
    }

    suspend fun setChallengeState(value: String) {
        context.dataStore.edit { it[Keys.CHALLENGE_STATE] = value }
    }

    suspend fun setCustomPokes(value: List<String>) {
        context.dataStore.edit { it[Keys.CUSTOM_POKES] = PokeCatalog.encode(value) }
    }

    suspend fun setReminderSetting(value: ReminderSetting) {
        context.dataStore.edit { it[Keys.REMINDER_SETTING] = ReminderSettingCodec.encode(value) }
    }

    suspend fun setDefaultVisibility(value: Visibility) {
        context.dataStore.edit { it[Keys.DEFAULT_VISIBILITY] = value.name }
    }

    suspend fun setMyAvatar(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.MY_AVATAR_URI) else prefs[Keys.MY_AVATAR_URI] = uri
        }
    }

    suspend fun setPartnerAvatar(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.PARTNER_AVATAR_URI) else prefs[Keys.PARTNER_AVATAR_URI] = uri
        }
    }

    /** 记录类分类体系 v2 迁移是否已执行（幂等守卫）。 */
    suspend fun taxonomyMigratedSnapshot(): Boolean =
        context.dataStore.data.map { it[Keys.TAXONOMY_V2_MIGRATED] ?: false }.first()

    suspend fun markTaxonomyMigrated() {
        context.dataStore.edit { it[Keys.TAXONOMY_V2_MIGRATED] = true }
    }

    suspend fun sessionSnapshot(): SessionProfile = session.first()

    suspend fun challengeSnapshot(): String? = challengeState.first()

    suspend fun customPokesSnapshot(): List<String> = customPokes.first()

    suspend fun reminderSettingSnapshot(): ReminderSetting = reminderSetting.first()
}
