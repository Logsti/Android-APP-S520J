package com.s520j.app.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
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

private val photoCategories = listOf("全部", "做饭", "探店", "旅行", "日常", "纪念日")

@Composable
fun GalleryScreen(
    vm: AppViewModel,
    onOpenModule: (ContentModule) -> Unit
) {
    val photosFlow = remember { vm.contents(ContentModule.PHOTO) }
    val photos by photosFlow.collectAsState(initial = emptyList())
    val isReadOnly by vm.isReadOnly.collectAsState()
    val dishesFlow = remember { vm.contents(ContentModule.DISH) }
    val dishes by dishesFlow.collectAsState(initial = emptyList())
    val shopsFlow = remember { vm.contents(ContentModule.SHOP) }
    val shops by shopsFlow.collectAsState(initial = emptyList())
    val momentsFlow = remember { vm.contents(ContentModule.COLLECTED_MOMENT) }
    val moments by momentsFlow.collectAsState(initial = emptyList())

    var category by remember { mutableStateOf("全部") }

    val filtered = if (category == "全部") photos else photos.filter { (it.extra["category"] ?: "日常") == category }

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

            // 四个统计入口：均可点击进入独立管理页
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                StatCard("共同照片", photos.size, Modifier.weight(1f)) { onOpenModule(ContentModule.PHOTO) }
                StatCard("一起做过的菜", dishes.size, Modifier.weight(1f)) { onOpenModule(ContentModule.DISH) }
            }
            Spacer(Modifier.height(Dimens.CardGap))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                StatCard("一起去过的店", shops.size, Modifier.weight(1f)) { onOpenModule(ContentModule.SHOP) }
                StatCard("收藏的瞬间", moments.size, Modifier.weight(1f)) { onOpenModule(ContentModule.COLLECTED_MOMENT) }
            }

            Spacer(Modifier.height(Dimens.SectionGap))

            SectionHeader(
                title = "照片墙",
                subtitle = "两张一组的日常",
                actionLabel = "全部照片",
                onAction = { onOpenModule(ContentModule.PHOTO) }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photoCategories.forEach { item ->
                    val selected = item == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) S520JColors.Primary else S520JColors.Surface)
                            .clickable { category = item }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else S520JColors.TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                SoftCard { EmptyState("这个分类还没有照片", actionLabel = "去添加") { onOpenModule(ContentModule.PHOTO) } }
            } else {
                filtered.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                        pair.forEach { photo ->
                            PhotoTile(photo, Modifier.weight(1f)) { onOpenModule(ContentModule.PHOTO) }
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(Dimens.CardGap))
                }
            }

            Spacer(Modifier.height(Dimens.SectionGap))

            SoftCard(onClick = { onOpenModule(ContentModule.DISH) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "五星榜",
                        style = MaterialTheme.typography.titleLarge,
                        color = S520JColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TagPill("小锦鲤 · 许愿树")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "只记录菜品、餐厅与体验，不评价人和关系。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
            }

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
private fun PhotoTile(photo: ContentRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            Icon(
                imageVector = Icons.Outlined.Photo,
                contentDescription = null,
                tint = S520JColors.PrimarySoft,
                modifier = Modifier.size(34.dp)
            )
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
                    text = photo.extra["category"] ?: "日常",
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
