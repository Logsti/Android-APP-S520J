package com.s520j.app.ui.screens.module

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.domain.tacit.ImportantDay
import com.s520j.app.domain.tacit.Promise
import com.s520j.app.domain.tacit.RelationshipRule
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.components.VisibilityChip
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.toDayTitle

@Composable
fun ModuleListScreen(
    vm: AppViewModel,
    module: ContentModule,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit
) {
    val flow = remember(module) { vm.contents(module) }
    val records by flow.collectAsState(initial = emptyList())
    val isReadOnly by vm.isReadOnly.collectAsState()
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ContentRecord?>(null) }

    val filtered = records.filter {
        query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true)
    }

    PageContainer {
        if (isReadOnly) {
            BackBar(title = module.label, onBack = onBack)
        } else {
            BackBar(
                title = module.label,
                onBack = onBack,
                action = {
                    IconButton(onClick = { onEdit(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = "新增", tint = S520JColors.Primary)
                    }
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        SoftTextField(
            value = query,
            onValueChange = { query = it },
            label = "搜索",
            placeholder = "按标题或内容搜索"
        )
        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            SoftCard {
                EmptyState(
                    text = if (query.isBlank()) "这里还没有内容" else "没有匹配的记录",
                    actionLabel = if (query.isBlank() && !isReadOnly) "新增一条" else null
                ) { onEdit(null) }
            }
        } else {
            filtered.forEach { record ->
                SoftCard(onClick = { onEdit(record.id) }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                record.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = S520JColors.TextPrimary
                            )
                            if (record.content.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    record.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = S520JColors.TextSecondary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (module == ContentModule.IMPORTANT_DAY && ImportantDay.isRepeat(record)) {
                                    TagPill("下次 ${ImportantDay.nextOccurrence(record).toDayTitle()}")
                                    TagPill("每年重复")
                                } else {
                                    TagPill((record.eventDate ?: record.createdAt).toDayTitle())
                                }
                                record.extra["星级"]?.let { TagPill("${it} 星") }
                                record.extra["状态"]?.let { TagPill(it) }
                                if (module == ContentModule.RELATIONSHIP_RULE) {
                                    TagPill(RelationshipRule.status(record))
                                }
                                if (module == ContentModule.PROMISE) {
                                    Promise.fulfilledAt(record)?.let { TagPill("已兑现 ${it.toDayTitle()}") }
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            VisibilityChip(record.visibility)
                            if (!isReadOnly) {
                                IconButton(onClick = { pendingDelete = record }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = S520JColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.CardGap))
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = S520JColors.Surface,
            title = { Text("删除这条记录？") },
            text = { Text("会移入回收站并保留 30 天，任一方都可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(record.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = Color(0xFFB3261E))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}
