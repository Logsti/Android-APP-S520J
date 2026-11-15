package com.s520j.app.ui.screens.tacit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.domain.tacit.LetterBoard
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PhotoPagerDialog
import com.s520j.app.ui.components.RecordPhotoGrid
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.toDayTitle

private data class ViewingLetterPhotos(val images: List<String>, val initialIndex: Int)

@Composable
fun LetterListScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit
) {
    val flow = remember { vm.contents(ContentModule.LETTER) }
    val records by flow.collectAsState(initial = emptyList())
    val session by vm.session.collectAsState()
    val isReadOnly by vm.isReadOnly.collectAsState()
    var filter by remember { mutableStateOf(LetterBoard.ALL) }
    var pendingDelete by remember { mutableStateOf<ContentRecord?>(null) }
    var viewing by remember { mutableStateOf<ViewingLetterPhotos?>(null) }
    val visible = remember(records, session.userId, filter) {
        LetterBoard.letters(records, session.userId, filter)
    }

    PageContainer(scrollable = false) {
        BackBar(
            title = "一封情书",
            onBack = onBack,
            action = if (isReadOnly) null else {
                {
                    IconButton(onClick = { onEdit(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = "写一封情书", tint = S520JColors.Primary)
                    }
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LetterBoard.filters.forEach { option ->
                val selected = filter == option
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) S520JColors.Primary else S520JColors.Surface)
                        .clickable { filter = option }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = LetterBoard.filterLabel(option, session.partnerNickname),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else S520JColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (visible.isEmpty()) {
            SoftCard {
                EmptyState(
                    text = if (filter == LetterBoard.ALL) "还没有情书" else "这个分类还没有情书",
                    actionLabel = if (isReadOnly) null else "写一封"
                ) { onEdit(null) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                items(visible, key = { it.id }) { record ->
                    LetterCard(
                        record = record,
                        viewerId = session.userId,
                        partnerNickname = session.partnerNickname,
                        isReadOnly = isReadOnly,
                        onEdit = { onEdit(record.id) },
                        onDelete = { pendingDelete = record },
                        onOpenPhotos = { index ->
                            viewing = ViewingLetterPhotos(record.mediaIds, index)
                        }
                    )
                }
            }
        }
    }

    viewing?.let { photos ->
        PhotoPagerDialog(
            images = photos.images,
            initialIndex = photos.initialIndex,
            onDismiss = { viewing = null }
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = S520JColors.Surface,
            title = { Text("删除这封情书？") },
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

@Composable
private fun LetterCard(
    record: ContentRecord,
    viewerId: String,
    partnerNickname: String,
    isReadOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPhotos: (Int) -> Unit
) {
    SoftCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (record.eventDate ?: record.createdAt).toDayTitle(),
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            TagPill(LetterBoard.directionLabel(record, viewerId, partnerNickname))
            if (!isReadOnly) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "删除情书",
                        tint = S520JColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(record.title, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
        if (record.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(record.content, style = MaterialTheme.typography.bodyMedium, color = S520JColors.TextSecondary)
        }
        if (record.mediaIds.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            record.mediaIds.chunked(4).forEachIndexed { chunkIndex, images ->
                if (chunkIndex > 0) Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(S520JColors.AccentPink)
                ) {
                    RecordPhotoGrid(
                        images = images,
                        title = record.title,
                        modifier = Modifier.fillMaxSize(),
                        onImageClick = { index -> onOpenPhotos(chunkIndex * 4 + index) }
                    )
                }
            }
        }
    }
}
