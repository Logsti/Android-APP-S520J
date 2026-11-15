package com.s520j.app.ui.screens.us

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.DetailRow
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.S520JColors
import java.util.Locale

@Composable
fun AppSettingsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenConflicts: () -> Unit) {
    val fontScale by vm.fontScale.collectAsState()
    val syncState by vm.syncState.collectAsState()
    val pendingCount by vm.pendingSyncCount.collectAsState()
    val conflicts by vm.syncConflicts.collectAsState()
    var editingScale by remember { mutableStateOf(false) }
    var localScale by remember { mutableStateOf(fontScale) }

    LaunchedEffect(fontScale) { if (!editingScale) localScale = fontScale }

    PageContainer {
        BackBar(title = "应用设置", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        SectionHeader(title = "应用设置", subtitle = "显示与外观")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "字体大小", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary, modifier = Modifier.weight(1f))
                Text(text = String.format(Locale.US, "%.2f×", localScale), style = MaterialTheme.typography.bodyMedium, color = S520JColors.Primary, fontWeight = FontWeight.Medium)
            }
            Slider(
                value = localScale,
                onValueChange = { editingScale = true; localScale = it; vm.setFontScale(it) },
                onValueChangeFinished = { editingScale = false },
                valueRange = 0.85f..1.3f,
                steps = 8,
                colors = SliderDefaults.colors(thumbColor = S520JColors.Primary, activeTrackColor = S520JColors.Primary, inactiveTrackColor = S520JColors.Divider)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "0.85× 更小", style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(text = "1.30× 更大", style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(10.dp))
            DetailRow("主题", "浅色")
            DetailRow("语言", "简体中文")
            Spacer(Modifier.height(4.dp))
            Text(text = "V1 仅提供浅色主题与简体中文，切换能力暂不开放。", style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader(title = "云端同步", subtitle = "本地优先，联网后自动补齐")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            val statusText = when (syncState.phase) {
                com.s520j.app.data.sync.SyncPhase.SYNCING -> "正在同步…"
                com.s520j.app.data.sync.SyncPhase.OFFLINE -> "未连接云端"
                com.s520j.app.data.sync.SyncPhase.FAILED -> "同步失败"
                com.s520j.app.data.sync.SyncPhase.IDLE ->
                    if (syncState.lastSyncAt > 0) "已同步" else "待同步"
            }
            DetailRow("同步状态", statusText)
            DetailRow("待上传", if (pendingCount > 0) "$pendingCount 条" else "无")
            DetailRow("待处理冲突", if (conflicts.isNotEmpty()) "${conflicts.size} 条" else "无")
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onOpenConflicts) {
                    Text(text = "去处理冲突", color = S520JColors.Primary)
                }
            }
            if (syncState.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = syncState.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.syncNow() },
                enabled = syncState.phase != com.s520j.app.data.sync.SyncPhase.SYNCING,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = S520JColors.Primary)
            ) {
                Text(text = "立即同步", color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "后台每 15 分钟自动同步一次；删除的内容保留 30 天，过期后自动清理。",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
        }

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
