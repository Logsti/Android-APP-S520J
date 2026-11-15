package com.s520j.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.s520j.app.R
import com.s520j.app.data.couple.CoupleEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 双人互动系统通知：碰一碰 / 挑战达成。
 * 无通知权限时静默跳过（应用内横幅与未读记录仍然保留）。
 */
@Singleton
class CoupleNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        ensureChannel()
        ensureUpdateChannel()
    }

    fun notifyEvent(event: CoupleEvent) {
        val (title, body) = contentOf(event)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                context,
                event.id.hashCode(),
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
        }
    }

    /** 心愿、空间与本机异常通用提醒；无权限时仅跳过系统通知。 */
    fun notifyUpdate(id: String, title: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                context,
                id.hashCode(),
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, id.hashCode(), notification)
        }
    }

    private fun contentOf(event: CoupleEvent): Pair<String, String> = when (event.type) {
        CoupleEvent.TYPE_CHALLENGE_DONE -> "今日心动挑战达成" to "你们俩今天都完成了，真好。"
        else -> {
            val who = event.fromName.ifBlank { "TA" }
            "$who 碰了碰你" to event.body.ifBlank { "打开看看 TA 想说什么" }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "双人互动", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "碰一碰与今日挑战达成提醒"
            }
        )
    }

    private fun ensureUpdateChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(UPDATE_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(UPDATE_CHANNEL_ID, "共同动态与同步提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "心愿、共同空间与需要处理的账号或同步异常"
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "s520j_couple"
        private const val UPDATE_CHANNEL_ID = "s520j_updates"
    }
}
