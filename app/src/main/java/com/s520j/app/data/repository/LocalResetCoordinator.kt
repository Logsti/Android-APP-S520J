package com.s520j.app.data.repository

import android.content.Context
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** v1.2.0 升级时执行一次；完成标记独立于被清空的 DataStore，仅存本机且不参与云同步。 */
@Singleton
class LocalResetCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    db: S520JDatabase,
    prefs: UserPreferences
) {
    private val marker = context.getSharedPreferences(MARKER_FILE, Context.MODE_PRIVATE)
    private val reset = OneTimeReset(
        isCompleted = { marker.getBoolean(MARKER_KEY, false) },
        clearLocalData = {
            withContext(Dispatchers.IO) {
                // Room 全表包含 users/contents/outbox 等，DataStore 全清包含会话、偏好与同步游标。
                db.clearAllTables()
                prefs.clearForOneTimeReset()
            }
        },
        markCompleted = {
            withContext(Dispatchers.IO) {
                check(marker.edit().putBoolean(MARKER_KEY, true).commit()) {
                    "无法写入本机一次性重置完成标记"
                }
            }
        }
    )

    suspend fun ensureCompleted() = reset.ensureCompleted()

    private companion object {
        const val MARKER_FILE = "s520j_one_time_reset"
        const val MARKER_KEY = "account_environment_reset_v1_completed"
    }
}
