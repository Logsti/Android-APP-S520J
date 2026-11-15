package com.s520j.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** 周期同步 + 回收站/冲突快照过期清理。 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun syncRepository(): SyncRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncEntryPoint::class.java
        ).syncRepository()
        repository.cleanupExpired()
        repository.syncNow(automatic = true)
        // 失败也返回 success，避免 WorkManager 快速重试耗电；下个周期会再试。
        return Result.success()
    }
}
