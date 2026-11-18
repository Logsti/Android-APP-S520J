package com.s520j.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.s520j.app.ui.UiMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 操作提示的展示时长集中定义，避免各处散落魔法数。
 * 普通提示 2 秒；带「去看看」等动作的提示留 4 秒给用户点击。
 */
object SnackbarTiming {
    const val NORMAL_MS = 2_000L
    const val WITH_ACTION_MS = 4_000L

    /** 碰一碰 / 挑战达成应用内横幅。 */
    const val BANNER_MS = 2_000L

    fun forMessage(hasAction: Boolean): Long = if (hasAction) WITH_ACTION_MS else NORMAL_MS
}

/**
 * 定时自动消失的提示。
 *
 * Material3 只提供 Short / Long / Indefinite 三档，无法表达 2 秒与 4 秒，
 * 这里统一用 Indefinite 配合定时 dismiss，时长精确可控且消失后不残留遮挡。
 * 调用方按顺序 collect 消息流，因此连续提示会排队逐条展示，不会互相覆盖。
 */
suspend fun SnackbarHostState.showTimedMessage(message: UiMessage): SnackbarResult =
    coroutineScope {
        val dismissJob = launch {
            delay(SnackbarTiming.forMessage(message.actionLabel != null))
            currentSnackbarData?.dismiss()
        }
        try {
            showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite
            )
        } finally {
            dismissJob.cancel()
        }
    }
