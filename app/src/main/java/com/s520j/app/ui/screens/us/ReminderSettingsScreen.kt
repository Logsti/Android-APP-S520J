package com.s520j.app.ui.screens.us

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.domain.us.ReminderSetting
import com.s520j.app.domain.us.ReminderSettingCodec
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.S520JColors

@Composable
fun ReminderSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val setting by vm.reminder.collectAsState()

    PageContainer {
        BackBar(title = "提醒设置", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        SoftCard {
            ToggleRow(
                title = "重要日期",
                subtitle = "纪念日、生日等重要日子提醒",
                checked = setting.importantDay,
                onCheckedChange = { vm.setReminder(setting.copy(importantDay = it)) }
            )
            Divider()
            ToggleRow(
                title = "日历事项",
                subtitle = "共同计划与日程开始前提醒",
                checked = setting.calendar,
                onCheckedChange = { vm.setReminder(setting.copy(calendar = it)) }
            )
            Divider()
            ToggleRow(
                title = "任务提醒",
                subtitle = "心愿、承诺等待办事项提醒",
                checked = setting.task,
                onCheckedChange = { vm.setReminder(setting.copy(task = it)) }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "提醒时间",
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Text(
                text = "每天在这个时间汇总一次当日提醒。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReminderSettingCodec.TIME_OPTIONS.forEach { time ->
                    TimeChip(text = time, selected = setting.time == time) {
                        vm.setReminder(setting.copy(time = time))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "当前提醒时间：${setting.time}",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.Primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "提醒只在本机生效，无免打扰档位。",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(S520JColors.Divider.copy(alpha = 0.6f))
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = S520JColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = S520JColors.Primary,
                uncheckedThumbColor = S520JColors.Surface,
                uncheckedTrackColor = S520JColors.Divider,
                uncheckedBorderColor = S520JColors.Divider
            )
        )
    }
}

@Composable
private fun TimeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) S520JColors.Primary else S520JColors.Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else S520JColors.TextSecondary
        )
    }
}
