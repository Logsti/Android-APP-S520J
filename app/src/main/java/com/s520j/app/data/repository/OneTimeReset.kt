package com.s520j.app.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 纯协调逻辑：完成标记只在全部清理成功后写入，进程内并发与后续重启均不重复清理。 */
internal class OneTimeReset(
    private val isCompleted: suspend () -> Boolean,
    private val clearLocalData: suspend () -> Unit,
    private val markCompleted: suspend () -> Unit
) {
    private val mutex = Mutex()

    suspend fun ensureCompleted() {
        if (isCompleted()) return
        mutex.withLock {
            if (isCompleted()) return
            clearLocalData()
            markCompleted()
        }
    }
}
