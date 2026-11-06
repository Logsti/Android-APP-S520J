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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.DetailRow
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PrimaryPillButton
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val fontScale by vm.fontScale.collectAsState()
    val greeting by vm.greeting.collectAsState()

    var importantDayOn by rememberSaveable { mutableStateOf(true) }
    var calendarOn by rememberSaveable { mutableStateOf(true) }
    var taskOn by rememberSaveable { mutableStateOf(false) }
    var reminderTime by rememberSaveable { mutableStateOf("20:00") }

    var editingScale by remember { mutableStateOf(false) }
    var localScale by remember { mutableStateOf(fontScale) }
    LaunchedEffect(fontScale) {
        if (!editingScale) localScale = fontScale
    }

    var greetingInput by rememberSaveable(greeting) { mutableStateOf(greeting) }

    PageContainer {
        BackBar(title = "设置", onBack = onBack)
        Spacer(Modifier.height(6.dp))

        SectionHeader(title = "提醒设置", subtitle = "双方各自设置，互不影响")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            ToggleRow(
                title = "重要日期",
                subtitle = "纪念日、生日等重要日子提醒",
                checked = importantDayOn,
                onCheckedChange = { importantDayOn = it }
            )
            SettingsDivider()
            ToggleRow(
                title = "日历事项",
                subtitle = "共同计划与日程开始前提醒",
                checked = calendarOn,
                onCheckedChange = { calendarOn = it }
            )
            SettingsDivider()
            ToggleRow(
                title = "任务提醒",
                subtitle = "心愿、承诺等待办事项提醒",
                checked = taskOn,
                onCheckedChange = { taskOn = it }
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
                listOf("08:00", "12:00", "20:00").forEach { time ->
                    TimeChip(
                        text = time,
                        selected = reminderTime == time,
                        onClick = { reminderTime = time }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "当前提醒时间：$reminderTime",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.Primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "本阶段仅本地保存，接入云端后同步。",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "应用设置", subtitle = "显示、语言与个性化")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "字体大小",
                    style = MaterialTheme.typography.titleMedium,
                    color = S520JColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format(Locale.US, "%.2f×", localScale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.Primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Slider(
                value = localScale,
                onValueChange = { value ->
                    editingScale = true
                    localScale = value
                    vm.setFontScale(value)
                },
                onValueChangeFinished = { editingScale = false },
                valueRange = 0.85f..1.3f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = S520JColors.Primary,
                    activeTrackColor = S520JColors.Primary,
                    inactiveTrackColor = S520JColors.Divider
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "0.85× 更小",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "1.30× 更大",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingsDivider()
            Spacer(Modifier.height(10.dp))
            DetailRow("主题", "浅色")
            DetailRow("语言", "简体中文")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "V1 仅提供浅色主题与简体中文，切换能力暂不开放。",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "个性化偏好", subtitle = "首页问候语")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            SoftTextField(
                value = greetingInput,
                onValueChange = { greetingInput = it },
                label = "问候语",
                placeholder = "今天也要好好喜欢彼此。",
                singleLine = false,
                minLines = 2
            )
            Spacer(Modifier.height(12.dp))
            PrimaryPillButton(
                text = "保存问候语",
                onClick = { vm.setGreeting(greetingInput.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = greetingInput.isNotBlank() && greetingInput.trim() != greeting
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "保存后立即生效。",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
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

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(S520JColors.Divider.copy(alpha = 0.6f))
    )
}
