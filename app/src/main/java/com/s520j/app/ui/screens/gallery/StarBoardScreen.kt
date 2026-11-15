package com.s520j.app.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.domain.gallery.StarBoard
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PrimaryPillButton
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

/**
 * 五星榜：一个入口下分「全部 / 松记五星榜 / 锦记五星榜」三个分类。
 * 归属仍由记录的「榜单」字段决定，历史数据无需迁移。
 */
@Composable
fun StarBoardScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenRecord: (ContentModule, String) -> Unit,
    onCreateDish: () -> Unit
) {
    val records by remember { vm.contents(ContentModule.DISH) }.collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(StarBoard.ALL) }
    val items = remember(records, tab) { StarBoard.list(records, tab) }

    PageContainer {
        BackBar(title = "五星榜", onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "只记录自己做的饭菜，探店不计入。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarBoard.tabs.forEach { name ->
                TabChip(
                    text = if (name == StarBoard.ALL) name else "${name}五星榜",
                    selected = name == tab,
                    onClick = { tab = name }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            SoftCard {
                EmptyState(text = if (tab == StarBoard.ALL) "还没有 5 星菜品" else "「$tab」还没有 5 星菜品")
                Spacer(Modifier.height(12.dp))
                PrimaryPillButton(
                    text = "去记录",
                    onClick = onCreateDish,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items.forEach { dish ->
                DishRow(dish = dish, onClick = { onOpenRecord(ContentModule.DISH, dish.id) })
                Spacer(Modifier.height(Dimens.CardGap))
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) S520JColors.Primary else S520JColors.Sand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) S520JColors.PureWhite else S520JColors.TextSecondary
        )
    }
}

@Composable
private fun DishRow(dish: ContentRecord, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = dish.title.ifBlank { "未命名菜品" },
                    style = MaterialTheme.typography.titleMedium,
                    color = S520JColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${StarBoard.boardOf(dish)} · ${dish.eventDate?.let { "已记录" } ?: "未设置日期"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
            Text(
                text = "★★★★★",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.Primary
            )
        }
    }
}
