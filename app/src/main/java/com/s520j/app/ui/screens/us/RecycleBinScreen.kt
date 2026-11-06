package com.s520j.app.ui.screens.us

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.components.VisibilityChip
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.toDayTitle

@Composable
fun RecycleBinScreen(vm: AppViewModel, onBack: () -> Unit) {
    val flow = remember { vm.recycleBin() }
    val records by flow.collectAsState(initial = emptyList())
    val isReadOnly by vm.isReadOnly.collectAsState()
    var pendingPurge by remember { mutableStateOf<ContentRecord?>(null) }

    PageContainer {
        BackBar(title = "回收站", onBack = onBack)
        Spacer(Modifier.height(6.dp))

        SoftCard(containerColor = S520JColors.Sand) {
            Text(
                text = "关于回收站",
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            RecycleNote("共同内容删除后进入回收站，保留 30 天。")
            RecycleNote("回收站里的任意一条，任何一方都可以恢复。")
            RecycleNote("彻底删除需双方确认；本阶段为本地 Mock，直接执行并给出提示。")
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        if (records.isEmpty()) {
            SoftCard {
                EmptyState(text = "回收站是空的")
            }
        } else {
            records.forEach { record ->
                SoftCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = record.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = S520JColors.TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "模块：${record.module.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = S520JColors.TextSecondary
                            )
                            Text(
                                text = "删除于 ${record.deletedAt?.toDayTitle() ?: "未知时间"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = S520JColors.TextSecondary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        VisibilityChip(record.visibility)
                    }
                    if (record.ownerName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagPill("由 ${record.ownerName} 记录")
                        }
                    }
                    if (!isReadOnly) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { vm.restoreRecord(record.id) }) {
                                Text("恢复", color = S520JColors.Primary)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { pendingPurge = record }) {
                                Text("彻底删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.CardGap))
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    pendingPurge?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingPurge = null },
            containerColor = S520JColors.Surface,
            title = { Text("彻底删除这条记录？") },
            text = {
                Text("彻底删除后无法恢复，也不再占用 30 天保留期。本阶段为本地 Mock，将直接执行该操作。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.purgeRecord(record.id)
                    pendingPurge = null
                }) {
                    Text("彻底删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurge = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RecycleNote(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.Primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
    }
}
