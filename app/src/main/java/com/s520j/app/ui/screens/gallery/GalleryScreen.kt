package com.s520j.app.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import com.s520j.app.domain.gallery.StarBoard
import com.s520j.app.domain.heart.PhotoFeed
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.toDayTitle
import com.s520j.app.ui.util.today
import com.s520j.app.ui.util.weekdayName

@Composable
fun GalleryScreen(
    vm: AppViewModel,
    onOpenModule: (ContentModule) -> Unit,
    onOpenRecord: (ContentModule, String) -> Unit
) {
    val allFlow = remember { vm.allContents() }
    val records by allFlow.collectAsState(initial = emptyList())
    val isReadOnly by vm.isReadOnly.collectAsState()

    var category by remember { mutableStateOf(PhotoFeed.ALL) }

    // 照片墙 = 五个带图来源，分类完全由 module 派生。
    val feed = remember(records) { PhotoFeed.feed(records) }
    val filtered = remember(feed, category) { PhotoFeed.filter(feed, category) }
    val dishCount = remember(records) { records.count { it.module == ContentModule.DISH } }
    val shopCount = remember(records) { records.count { it.module == ContentModule.SHOP } }
    val travelCount = remember(records) { records.count { it.module == ContentModule.TRAVEL } }
    val songStars = remember(records) { StarBoard.list(records, StarBoard.SONG) }
    val jinStars = remember(records) { StarBoard.list(records, StarBoard.JIN) }

    Box(Modifier.fillMaxWidth()) {
        PageContainer {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "拾起生活里的微光。",
                style = MaterialTheme.typography.displayLarge,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "把一起走过的日子，慢慢收好。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            SoftCard(containerColor = S520JColors.Sand) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = today().toDayTitle(),
                            style = MaterialTheme.typography.titleLarge,
                            color = S520JColors.TextPrimary
                        )
                        Text(
                            text = today().weekdayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = S520JColors.TextSecondary
                        )
                    }
                    Text("今日", style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(Dimens.SectionGap))

            // 四个统计入口：共同照片切回「全部」tab，其余进入对应模块
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                StatCard("共同照片", feed.size, Modifier.weight(1f)) { category = PhotoFeed.ALL }
                StatCard("一起做过的菜", dishCount, Modifier.weight(1f)) { onOpenModule(ContentModule.DISH) }
            }
            Spacer(Modifier.height(Dimens.CardGap))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                StatCard("一起去过的店", shopCount, Modifier.weight(1f)) { onOpenModule(ContentModule.SHOP) }
                StatCard("一起去过的城市", travelCount, Modifier.weight(1f)) { onOpenModule(ContentModule.TRAVEL) }
            }

            Spacer(Modifier.height(Dimens.SectionGap))

            SectionHeader(
                title = "照片墙",
                subtitle = "两张一组的日常",
                actionLabel = "查看全部",
                onAction = { onOpenModule(PhotoFeed.moduleOf(category) ?: ContentModule.PHOTO) }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoFeed.categories.forEach { item ->
                    val selected = item == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) S520JColors.Primary else S520JColors.Surface)
                            .clickable { category = item }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else S520JColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                SoftCard {
                    EmptyState("这个分类还没有照片", actionLabel = "去添加") {
                        onOpenModule(PhotoFeed.moduleOf(category) ?: ContentModule.PHOTO)
                    }
                }
            } else {
                filtered.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                        pair.forEach { photo ->
                            PhotoTile(photo, Modifier.weight(1f)) {
                                onOpenRecord(photo.module, photo.id)
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(Dimens.CardGap))
                }
            }

            Spacer(Modifier.height(Dimens.SectionGap))

            StarBoardCard(
                title = "松记五星榜",
                items = songStars,
                onClick = { onOpenModule(ContentModule.DISH) }
            )
            Spacer(Modifier.height(Dimens.CardGap))
            StarBoardCard(
                title = "锦记五星榜",
                items = jinStars,
                onClick = { onOpenModule(ContentModule.DISH) }
            )

            Spacer(Modifier.height(96.dp))
        }

        // 仅拾光页有悬浮新增按钮；冻结归档时隐藏
        if (!isReadOnly) {
            FloatingActionButton(
                onClick = { onOpenModule(ContentModule.PHOTO) },
                containerColor = S520JColors.Primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 20.dp)
                    .size(Dimens.FabSize),
                shape = RoundedCornerShape(999.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
            }
        }
    }
}

@Composable
private fun StatCard(title: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SoftCard(modifier = modifier, onClick = onClick) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = S520JColors.Primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text("条记录", style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
    }
}

@Composable
private fun StarBoardCard(
    title: String,
    items: List<ContentRecord>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SoftCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = S520JColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            TagPill("5 星 · 自做饭菜")
        }
        Spacer(Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text(
                text = "还没有 5 星菜品",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        } else {
            items.take(3).forEach { dish ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dish.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = S520JColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "★★★★★",
                        style = MaterialTheme.typography.labelSmall,
                        color = S520JColors.Primary
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: ContentRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cover = photo.mediaIds.firstOrNull()
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
                .clip(RoundedCornerShape(22.dp))
                .background(S520JColors.AccentPink)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = photo.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Photo,
                    contentDescription = null,
                    tint = S520JColors.PrimarySoft,
                    modifier = Modifier.size(34.dp)
                )
            }
            if (photo.visibility == Visibility.PRIVATE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(S520JColors.Surface.copy(alpha = 0.9f))
                        .padding(5.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "私密", tint = S520JColors.TextSecondary, modifier = Modifier.size(12.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(S520JColors.Surface.copy(alpha = 0.86f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = PhotoFeed.categoryOf(photo).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = photo.title,
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextPrimary,
            maxLines = 1
        )
        Text(
            text = (photo.eventDate ?: photo.createdAt).toDayTitle(),
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
    }
}
