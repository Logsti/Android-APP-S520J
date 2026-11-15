package com.s520j.app.ui.screens.us

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.s520j.app.data.local.SyncConflictEntity
import com.s520j.app.data.sync.ConflictResolver
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PrimaryPillButton
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.toShortDate

/** 8B.2：冲突快照选择页，同一条记录在两端同时改动后由用户决定保留哪一份。 */
@Composable
fun ConflictResolutionScreen(vm: AppViewModel, onBack: () -> Unit) {
    val conflicts by vm.syncConflicts.collectAsState()

    PageContainer {
        BackBar(title = "冲突处理", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        SectionHeader(
            title = "待处理冲突",
            subtitle = "同一条记录在两端同时改动，请选择保留哪一份"
        )
        Spacer(Modifier.height(10.dp))

        if (conflicts.isEmpty()) {
            EmptyState(text = "暂无待处理的冲突")
            return@PageContainer
        }

        conflicts.forEach { conflict ->
            ConflictCard(conflict = conflict, vm = vm)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConflictCard(conflict: SyncConflictEntity, vm: AppViewModel) {
    val local = ConflictResolver.snapshot(conflict.localJson)
    val server = ConflictResolver.snapshot(conflict.serverJson)
    val title = server?.title?.takeIf { it.isNotBlank() }
        ?: local?.title?.takeIf { it.isNotBlank() }
        ?: "未命名内容"

    SoftCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${conflict.createdAt.toShortDate()} 产生冲突 · 快照保留 30 天",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )

        Spacer(Modifier.height(10.dp))
        VersionBlock(
            label = "本机版本",
            content = local?.content.orEmpty(),
            updatedAt = local?.updatedAt ?: 0L
        )
        Spacer(Modifier.height(8.dp))
        VersionBlock(
            label = "云端版本",
            content = server?.content.orEmpty(),
            updatedAt = server?.updatedAt ?: 0L
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryPillButton(
                text = "保留本机",
                onClick = { vm.resolveConflict(conflict.id, keepLocal = true) },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { vm.resolveConflict(conflict.id, keepLocal = false) },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "保留云端", color = S520JColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun VersionBlock(label: String, content: String, updatedAt: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = S520JColors.Primary
            )
            if (updatedAt > 0) {
                Text(
                    text = updatedAt.toShortDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = content.ifBlank { "（无正文）" },
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextPrimary
        )
    }
}
