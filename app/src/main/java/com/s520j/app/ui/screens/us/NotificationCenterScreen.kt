package com.s520j.app.ui.screens.us

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.toShortDate

@Composable
fun NotificationCenterScreen(vm: AppViewModel, onBack: () -> Unit) {
    val notifications by vm.notifications.collectAsState()
    val unread by vm.unreadCount.collectAsState()
    val reminders by vm.todayReminders.collectAsState()

    PageContainer {
        BackBar(
            title = "通知中心",
            onBack = onBack,
            action = {
                TextButton(
                    onClick = { vm.markAllRead() },
                    enabled = unread > 0
                ) {
                    Text(
                        text = "全部已读",
                        color = if (unread > 0) S520JColors.Primary else S520JColors.TextSecondary
                    )
                }
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (unread > 0) "有 $unread 条未读通知" else "全部通知都已读",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(16.dp))

        SoftCard(containerColor = S520JColors.Sand) {
            Text(
                text = "今日待提醒",
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            if (reminders.total == 0) {
                Text(
                    text = "今天没有到期的提醒项。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
            } else {
                Text(
                    text = "共 ${reminders.total} 项 · 重要日子 ${reminders.importantDay} · 日历事项 ${reminders.calendar} · 待兑现承诺 ${reminders.task}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextPrimary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "按「提醒设置」中我的开关与当天日期汇总。",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(16.dp))

        if (notifications.isEmpty()) {
            SoftCard {
                EmptyState(text = "暂时没有通知")
            }
        } else {
            notifications.forEach { item ->
                SoftCard(onClick = { vm.markRead(item.id) }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (item.read) S520JColors.TextSecondary else S520JColors.TextPrimary,
                                fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = S520JColors.TextSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.createdAt.toShortDate(),
                                style = MaterialTheme.typography.labelSmall,
                                color = S520JColors.TextSecondary
                            )
                        }
                        if (!item.read) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(8.dp)
                                    .background(S520JColors.Primary, CircleShape)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.CardGap))
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SoftCard(containerColor = S520JColors.Sand) {
            Text(
                text = "关于通知",
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "本应用不使用系统推送与本地通知。打开 APP、切回前台或手动同步时，会拉取最新的共同动态。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
