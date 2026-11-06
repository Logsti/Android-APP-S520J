package com.s520j.app.ui.screens.us

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.S520JColors

@Composable
fun DataManagementScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenRecycle: () -> Unit
) {
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            vm.notify(if (written) "已导出 JSON 数据" else "导出失败，请重试")
        }
    }
    val onExport: () -> Unit = {
        vm.exportJson { json ->
            pendingExport = json
            exportLauncher.launch("s520j-export-${System.currentTimeMillis()}.json")
        }
    }

    PageContainer {
        BackBar(title = "数据管理", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        SectionHeader(title = "数据管理", subtitle = "本地与云端数据维护")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenRecycle() }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f)) {
                    Text(text = "回收站", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                }
                Text(text = "30 天保留", style = MaterialTheme.typography.bodyMedium, color = S520JColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = S520JColors.Primary)
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth().clickable { vm.notify("本地缓存无需清理") }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "清理缓存", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = S520JColors.Primary)
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExport() }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f)) {
                    Text(text = "导出数据", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                    Text(
                        text = "JSON 文本，不含图片文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = S520JColors.TextSecondary
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = S520JColors.Primary)
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth().clickable { vm.notify("云端备份功能开发中") }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f)) {
                    Text(text = "云端备份", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                    Text(
                        text = "开发中",
                        style = MaterialTheme.typography.bodyMedium,
                        color = S520JColors.TextSecondary
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = S520JColors.Primary)
            }
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
