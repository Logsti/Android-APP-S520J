package com.s520j.app.ui.screens.tacit

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.components.VisibilityChip
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.toShortDate

private data class TacitEntry(val module: ContentModule, val icon: ImageVector, val short: String)

private val tacitEntries = listOf(
    TacitEntry(ContentModule.IMPORTANT_DAY, Icons.Filled.Cake, "重要日子"),
    TacitEntry(ContentModule.PROMISE, Icons.Filled.Handshake, "彼此的承诺"),
    TacitEntry(ContentModule.DEED, Icons.Filled.CardGiftcard, "为彼此做过"),
    TacitEntry(ContentModule.RELATIONSHIP_RULE, Icons.Filled.Balance, "相处之道"),
    TacitEntry(ContentModule.LIKE, Icons.Filled.ThumbUp, "TA 的喜欢"),
    TacitEntry(ContentModule.DISLIKE, Icons.Filled.ThumbDown, "TA 不喜欢"),
    TacitEntry(ContentModule.NOTE, Icons.Filled.Edit, "我们的笔记"),
    TacitEntry(ContentModule.ROMANTIC_MOMENT, Icons.Filled.Favorite, "浪漫时刻")
)

@Composable
fun TacitScreen(
    vm: AppViewModel,
    onOpenModule: (ContentModule) -> Unit,
    onOpenRecord: (ContentModule, String) -> Unit
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val entry = tacitEntries[selectedIndex]

    val previewFlow = remember(entry.module) { vm.contents(entry.module) }
    val previews by previewFlow.collectAsState(initial = emptyList())

    PageContainer {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "把没说出口的，都记下来。",
            style = MaterialTheme.typography.headlineMedium,
            color = S520JColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "八个角落，装下你们的默契。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(tacitEntries) { index, item ->
                Column(
                    modifier = Modifier.width(66.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (selected) S520JColors.Primary else S520JColors.Surface)
                            .clickable { selectedIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.short,
                            tint = if (selected) androidx.compose.ui.graphics.Color.White else S520JColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.short,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) S520JColors.TextPrimary else S520JColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            title = entry.short,
            subtitle = "最近记录",
            actionLabel = "查看全部",
            onAction = { onOpenModule(entry.module) }
        )
        Spacer(Modifier.height(10.dp))

        if (previews.isEmpty()) {
            SoftCard {
                EmptyState("还没有记录", actionLabel = "去添加") { onOpenModule(entry.module) }
            }
        } else {
            previews.take(3).forEach { record ->
                TacitPreviewCard(record) { onOpenModule(entry.module) }
                Spacer(Modifier.height(Dimens.CardGap))
            }
        }

        Spacer(Modifier.height(20.dp))
        SoftCard {
            Text(
                text = "相处之道需要双方都确认，才算共同认可。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TacitPreviewCard(record: ContentRecord, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.title, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                if (record.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = record.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = S520JColors.TextSecondary,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagPill((record.eventDate ?: record.createdAt).toShortDate())
                    record.extra["状态"]?.let { TagPill(it) }
                    record.extra["确认"]?.let { TagPill(it) }
                }
            }
            Spacer(Modifier.width(8.dp))
            VisibilityChip(record.visibility)
        }
    }
}
