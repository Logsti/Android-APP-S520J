package com.s520j.app.ui.screens.us

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.data.local.BindState
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.DetailRow
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

@Composable
fun UsScreen(
    vm: AppViewModel,
    onOpenSpace: () -> Unit,
    onOpenAccountSecurity: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenPreferenceSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRecycle: () -> Unit,
    onLogout: () -> Unit
) {
    val session by vm.session.collectAsState()
    val space by vm.space.collectAsState()
    val unread by vm.unreadCount.collectAsState()

    val bindState = space?.bindState?.let { raw -> BindState.entries.firstOrNull { it.name == raw } } ?: BindState.UNBOUND

    PageContainer {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "我们",
                    style = MaterialTheme.typography.displayLarge,
                    color = S520JColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "两个人，两段独立的人生，一个共同的空间。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
            }
            NotificationBell(unread = unread, onClick = onOpenNotifications)
        }

        Spacer(Modifier.height(16.dp))

        SoftCard(containerColor = S520JColors.Sand) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(session.nickname.take(1).ifBlank { "你" })
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = S520JColors.Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Avatar(session.partnerNickname.take(1).ifBlank { "TA" })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${session.nickname} & ${session.partnerNickname}",
                        style = MaterialTheme.typography.titleMedium,
                        color = S520JColors.TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagPill(bindState.label)
                        TagPill("独立账号")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DetailRow("我的账号 ID", session.accountId.ifBlank { "未登录" })
            DetailRow("共同空间", space?.name ?: "尚未创建")
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "设置", subtitle = "空间、隐私与数据")
        Spacer(Modifier.height(10.dp))

        SoftCard {
            SettingRow("共同空间", "绑定、解绑与冻结归档", onOpenSpace)
            SettingDivider()
            SettingRow("账号与安全", "账号信息与密码管理", onOpenAccountSecurity)
            SettingDivider()
            SettingRow("隐私与权限", "仅自己可见 / 共同可见", onOpenPrivacy)
            SettingDivider()
            SettingRow("数据管理", "回收站、清理缓存、导出与备份", onOpenDataManagement)
            SettingDivider()
            SettingRow("提醒设置", "双方各自设置提醒时间", onOpenReminderSettings)
            SettingDivider()
            SettingRow("应用设置", "字体大小、主题与语言", onOpenAppSettings)
            SettingDivider()
            SettingRow("个性化偏好", "首页问候语与个人偏好", onOpenPreferenceSettings)
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "关于 S520J")
        Spacer(Modifier.height(10.dp))
        SoftCard(onClick = onOpenAbout) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = S520JColors.Primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("关于 S520J", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                    Text(
                        "版本、更新日期、作者与帮助反馈",
                        style = MaterialTheme.typography.bodyMedium,
                        color = S520JColors.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("退出登录", color = S520JColors.TextSecondary)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun NotificationBell(unread: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(S520JColors.Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = "通知",
            tint = S520JColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(if (unread > 9) 18.dp else 18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE55757)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 9) "9+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Avatar(letter: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(S520JColors.Surface),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleLarge,
            color = S520JColors.Primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = S520JColors.TextSecondary)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = S520JColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(S520JColors.Divider.copy(alpha = 0.6f))
    )
}
