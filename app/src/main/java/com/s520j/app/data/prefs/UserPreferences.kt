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
import com.s520j.app.data.sync.SyncFailureTrack
import com.s520j.app.domain.heart.PokeCatalog
import com.s520j.app.domain.us.ReminderSetting
import com.s520j.app.domain.us.ReminderSettingCodec
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        // 云端会话（8B.1）：access/refresh token 只存本机，不同步、不写日志。
        val CLOUD_ACCESS_TOKEN = stringPreferencesKey("cloud_access_token")
        val CLOUD_REFRESH_TOKEN = stringPreferencesKey("cloud_refresh_token")
        val CLOUD_TOKEN_EXPIRES_AT = longPreferencesKey("cloud_token_expires_at")
        val CLOUD_USER_ID = stringPreferencesKey("cloud_user_id")
        val CLOUD_SPACE_ID = stringPreferencesKey("cloud_space_id")
        val SYNC_CURSOR = longPreferencesKey("sync_cursor")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val EVENT_CURSOR = longPreferencesKey("event_cursor")
        val POKE_NOTIFY_ASKED = booleanPreferencesKey("poke_notify_asked")
        val NOTIFICATION_BASELINE_USERS = stringSetPreferencesKey("notification_baseline_users")
        val SYNC_FAILURE_ACCOUNT = stringPreferencesKey("sync_failure_account")
        val SYNC_FAILURE_COUNT = intPreferencesKey("sync_failure_count")
        val SYNC_FAILURE_SIGNATURE = stringPreferencesKey("sync_failure_signature")
        val SYNC_FAILURE_ALERTED = booleanPreferencesKey("sync_failure_alerted")
        val SYNC_FAILURE_GENERATION = longPreferencesKey("sync_failure_generation")
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

    /** 绑定后从 space.get 同步对方昵称。 */
    suspend fun setPartnerNickname(name: String?) {
        context.dataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(Keys.PARTNER_NICKNAME) else prefs[Keys.PARTNER_NICKNAME] = name
        }
    }

    /** 记录类分类体系 v2 迁移是否已执行（幂等守卫）。 */
    suspend fun taxonomyMigratedSnapshot(): Boolean =
        context.dataStore.data.map { it[Keys.TAXONOMY_V2_MIGRATED] ?: false }.first()

    suspend fun markTaxonomyMigrated() {
        context.dataStore.edit { it[Keys.TAXONOMY_V2_MIGRATED] = true }
    }

    suspend fun sessionSnapshot(): SessionProfile = session.first()

    /** 一次性环境重置：清除登录态、全部本机偏好及同步/事件游标。 */
    suspend fun clearForOneTimeReset() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun challengeSnapshot(): String? = challengeState.first()

    // ---------- 云端会话与同步游标（8B） ----------

    data class CloudSession(
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val spaceId: String?
    )

    suspend fun cloudSessionSnapshot(): CloudSession? {
        val p = context.dataStore.data.first()
        val access = p[Keys.CLOUD_ACCESS_TOKEN] ?: return null
        val refresh = p[Keys.CLOUD_REFRESH_TOKEN] ?: return null
        return CloudSession(
            userId = p[Keys.CLOUD_USER_ID].orEmpty(),
            accessToken = access,
            refreshToken = refresh,
            expiresAt = p[Keys.CLOUD_TOKEN_EXPIRES_AT] ?: 0L,
            spaceId = p[Keys.CLOUD_SPACE_ID]
        )
    }

    suspend fun saveCloudSession(
        userId: String,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long
    ) {
        context.dataStore.edit { p ->
            p[Keys.CLOUD_USER_ID] = userId
            p[Keys.CLOUD_ACCESS_TOKEN] = accessToken
            p[Keys.CLOUD_REFRESH_TOKEN] = refreshToken
            p[Keys.CLOUD_TOKEN_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun clearCloudSession() {
        context.dataStore.edit { p ->
            p.remove(Keys.CLOUD_ACCESS_TOKEN)
            p.remove(Keys.CLOUD_REFRESH_TOKEN)
            p.remove(Keys.CLOUD_TOKEN_EXPIRES_AT)
            p.remove(Keys.CLOUD_USER_ID)
            p.remove(Keys.CLOUD_SPACE_ID)
            p.remove(Keys.SYNC_CURSOR)
            p.remove(Keys.LAST_SYNC_AT)
            p.remove(Keys.EVENT_CURSOR)
        }
    }

    suspend fun setCloudSpaceId(spaceId: String?) {
        context.dataStore.edit { p ->
            if (spaceId.isNullOrBlank()) p.remove(Keys.CLOUD_SPACE_ID) else p[Keys.CLOUD_SPACE_ID] = spaceId
        }
    }

    suspend fun syncCursorSnapshot(): Long = context.dataStore.data.first()[Keys.SYNC_CURSOR] ?: 0L

    suspend fun setSyncCursor(cursor: Long) {
        context.dataStore.edit { it[Keys.SYNC_CURSOR] = cursor }
    }

    /** 最近一次成功同步时间（9.3）：重启后仍可显示「上次同步」。 */
    suspend fun lastSyncAtSnapshot(): Long = context.dataStore.data.first()[Keys.LAST_SYNC_AT] ?: 0L

    suspend fun setLastSyncAt(ts: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_AT] = ts }
    }

    suspend fun customPokesSnapshot(): List<String> = customPokes.first()

    suspend fun reminderSettingSnapshot(): ReminderSetting = reminderSetting.first()

    /** 双人事件拉取游标：按 created_at 递增，重装/断网后不会重复提醒。 */
    suspend fun eventCursorSnapshot(): Long = context.dataStore.data.first()[Keys.EVENT_CURSOR] ?: 0L

    suspend fun setEventCursor(cursor: Long) {
        context.dataStore.edit { it[Keys.EVENT_CURSOR] = cursor }
    }

    /** 通知权限是否已询问过：用户拒绝后不再重复弹系统请求。 */
    suspend fun pokeNotifyAskedSnapshot(): Boolean =
        context.dataStore.data.first()[Keys.POKE_NOTIFY_ASKED] ?: false

    suspend fun markPokeNotifyAsked() {
        context.dataStore.edit { it[Keys.POKE_NOTIFY_ASKED] = true }
    }

    /** 通知基线按本机 DataStore + 云端账号保存；普通退出登录不清除。 */
    suspend fun hasNotificationBaseline(userId: String): Boolean =
        context.dataStore.data.first()[Keys.NOTIFICATION_BASELINE_USERS]?.contains(userId) == true

    suspend fun markNotificationBaseline(userId: String) {
        context.dataStore.edit { p ->
            p[Keys.NOTIFICATION_BASELINE_USERS] =
                (p[Keys.NOTIFICATION_BASELINE_USERS] ?: emptySet()) + userId
        }
    }

    suspend fun syncFailureTrackSnapshot(): SyncFailureTrack {
        val p = context.dataStore.data.first()
        return SyncFailureTrack(
            accountId = p[Keys.SYNC_FAILURE_ACCOUNT].orEmpty(),
            consecutive = p[Keys.SYNC_FAILURE_COUNT] ?: 0,
            signature = p[Keys.SYNC_FAILURE_SIGNATURE].orEmpty(),
            alerted = p[Keys.SYNC_FAILURE_ALERTED] ?: false,
            generation = p[Keys.SYNC_FAILURE_GENERATION] ?: 0L
        )
    }

    suspend fun saveSyncFailureTrack(track: SyncFailureTrack) {
        context.dataStore.edit { p ->
            p[Keys.SYNC_FAILURE_ACCOUNT] = track.accountId
            p[Keys.SYNC_FAILURE_COUNT] = track.consecutive
            p[Keys.SYNC_FAILURE_SIGNATURE] = track.signature
            p[Keys.SYNC_FAILURE_ALERTED] = track.alerted
            p[Keys.SYNC_FAILURE_GENERATION] = track.generation
        }
    }
}
