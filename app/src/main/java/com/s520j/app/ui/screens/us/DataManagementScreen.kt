package com.s520j.app.ui.screens.us

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.s520j.app.data.backup.BackupService
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.S520JColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataManagementScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenRecycle: () -> Unit
) {
    val context = LocalContext.current
    val backups by vm.backups.collectAsState()
    var showBackups by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshBackups() }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { vm.exportZip(it) } }

    PageContainer {
        BackBar(title = "数据管理", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        SectionHeader(title = "数据管理", subtitle = "本地与云端数据维护")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            SettingLine(
                title = "回收站",
                subtitle = "30 天保留",
                onClick = onOpenRecycle
            )
            Divider()
            SettingLine(
                title = "导出数据",
                subtitle = "内容 JSON + 图片 ZIP，未加密，仅用于离线留存",
                onClick = {
                    zipLauncher.launch("s520j-export-${System.currentTimeMillis()}.zip")
                }
            )
            Divider()
            SettingLine(
                title = "手动备份",
                subtitle = "仅保存内容记录 JSON，可在本机恢复",
                trailing = "立即备份",
                onClick = { vm.createBackup() }
            )
            Divider()
            SettingLine(
                title = "恢复备份",
                subtitle = if (backups.isEmpty()) "暂无备份" else "共 ${backups.size} 个备份",
                onClick = { if (backups.isNotEmpty()) showBackups = true }
            )
            Divider()
            SettingLine(
                title = "清理本机数据",
                subtitle = "只清本机，云端与备份文件保留",
                onClick = { confirmClear = true }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "换机或重装主要依靠普通登录后的云端同步，自动恢复已同步的昵称、头像、共同空间、内容记录和已上传图片。\n\n本机手动备份只包含内容记录 JSON，不包含图片文件、账号、密码、共同空间或设置；卸载 APP 后本机备份会删除。恢复只覆盖同 ID 的记录，不影响其他内容。\n\nZIP 导出包含内容 JSON 和图片，仅用于离线留存，不支持导入恢复；备份与导出都不会上传到云端。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(40.dp))
    }

    if (showBackups) {
        AlertDialog(
            onDismissRequest = { showBackups = false },
            title = { Text("选择备份") },
            text = {
                Column {
                    backups.forEach { backup ->
                        BackupRow(
                            backup = backup,
                            onRestore = {
                                vm.restoreBackup(backup.name)
                                showBackups = false
                            },
                            onDelete = { vm.deleteBackup(backup.name) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackups = false }) { Text("关闭") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清理本机数据") },
            text = { Text("将清空本机的全部内容记录（云端不受影响）。建议先创建备份，之后可随时恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearLocalData()
                }) { Text("确认清理") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BackupRow(backup: BackupService.BackupInfo, onRestore: () -> Unit, onDelete: () -> Unit) {
    val time = remember(backup.createdAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(backup.createdAt))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(time, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
            Text(
                "${backup.bytes / 1024} KB",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }
        TextButton(onClick = onRestore) { Text("恢复") }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

@Composable
private fun SettingLine(
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.Primary
            )
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = S520JColors.Primary)
        }
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
