package com.s520j.app.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import com.s520j.app.data.sync.SyncPhase
import com.s520j.app.domain.gallery.StarBoard
import com.s520j.app.domain.heart.GalleryPhotoLayout
import com.s520j.app.domain.heart.PhotoFeed
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PhotoPagerDialog
import com.s520j.app.ui.components.RecordPhotoGrid
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
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
    onOpenRecord: (ContentModule, String) -> Unit,
    onCreateRecord: (ContentModule) -> Unit,
    onOpenStarBoard: () -> Unit,
    onOpenSharedPhotos: () -> Unit
) {
    val allFlow = remember { vm.allContents() }
    val records by allFlow.collectAsState(initial = emptyList())
    val isReadOnly by vm.isReadOnly.collectAsState()
    val syncState by vm.syncState.collectAsState()

    // 进入页面时补一次同步（内部受 20 秒节流保护）。
    LaunchedEffect(Unit) { vm.syncOnEnter() }

    var category by remember { mutableStateOf(PhotoFeed.ALL) }
    var query by remember { mutableStateOf("") }
    var showAddEntries by remember { mutableStateOf(false) }
    // 非空时展示全屏多图浏览；点击照片区写入，关闭时清空。
    var viewingImages by remember { mutableStateOf<List<String>?>(null) }

    // 照片墙 = 五个带图来源，分类完全由 module 派生。
    val feed = remember(records) { PhotoFeed.feed(records) }
    val filtered = remember(feed, category) { PhotoFeed.filter(feed, category) }
    val visible = remember(filtered, query) { PhotoFeed.search(filtered, query) }
    val dishCount = remember(records) { records.count { it.module == ContentModule.DISH } }
    val shopCount = remember(records) { records.count { it.module == ContentModule.SHOP } }
    val travelCount = remember(records) { records.count { it.module == ContentModule.TRAVEL } }
    val allStars = remember(records) { StarBoard.list(records, StarBoard.ALL) }

    Box(Modifier.fillMaxWidth()) {
        PageContainer(
            onRefresh = vm::syncNow,
            refreshing = syncState.phase == SyncPhase.SYNCING
        ) {
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

            // 四张统计卡：共同照片进入独立子页；其余三张进入独立管理页。
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                StatCard("共同照片", feed.size, Modifier.weight(1f), onClick = onOpenSharedPhotos)
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
            // 小屏（320dp）下六个分类会超宽，改为可横滑。
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
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
            SoftTextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索照片",
                placeholder = "按标题或内容搜索"
            )
            Spacer(Modifier.height(12.dp))

            if (visible.isEmpty()) {
                SoftCard {
                    if (query.isBlank()) {
                        EmptyState("这个分类还没有照片", actionLabel = "去添加") {
                            onOpenModule(PhotoFeed.moduleOf(category) ?: ContentModule.PHOTO)
                        }
                    } else {
                        EmptyState("没有匹配的照片")
                    }
                }
            } else {
                visible.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                        pair.forEach { photo ->
                            PhotoTile(
                                photo = photo,
                                modifier = Modifier.weight(1f),
                                onOpenPhotos = {
                                    viewingImages = photo.mediaIds.take(GalleryPhotoLayout.MAX)
                                },
                                onEdit = { onOpenRecord(photo.module, photo.id) }
                            )
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
                title = "五星榜",
                items = allStars,
                onClick = onOpenStarBoard
            )

            Spacer(Modifier.height(96.dp))
        }

        // 仅拾光页有悬浮新增按钮；冻结归档时隐藏。点击先选类型，再按类型决定是否显示照片上传。
        if (!isReadOnly) {
            FloatingActionButton(
                onClick = { showAddEntries = true },
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

    if (showAddEntries) {
        AddEntryDialog(
            onDismiss = { showAddEntries = false },
            onPick = { module ->
                showAddEntries = false
                onCreateRecord(module)
            }
        )
    }

    viewingImages?.let { images ->
        PhotoPagerDialog(
            images = images,
            initialIndex = 0,
            onDismiss = { viewingImages = null }
        )
    }
}

private data class AddEntry(val label: String, val hint: String, val module: ContentModule)

private val addEntries = listOf(
    AddEntry("共同照片", "相册里的日常", ContentModule.PHOTO),
    AddEntry("一起做过的菜", "给这顿饭标星", ContentModule.DISH),
    AddEntry("一起去过的店", "记下店名和味道", ContentModule.SHOP),
    AddEntry("一起去过的城市", "旅行中的一张照片", ContentModule.TRAVEL),
    AddEntry("收藏的瞬间", "值得留住的小片段", ContentModule.COLLECTED_MOMENT),
    AddEntry("浪漫时刻", "两个人的心动时刻", ContentModule.ROMANTIC_MOMENT),
    AddEntry("文字笔记", "只写文字，不附照片", ContentModule.NOTE)
)

@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onPick: (ContentModule) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = S520JColors.Surface,
        title = { Text("新增内容", color = S520JColors.TextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                addEntries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(entry.module) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = S520JColors.TextPrimary
                            )
                            Text(
                                entry.hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = S520JColors.TextSecondary
                            )
                        }
                        TagPill(if (entry.module.needsPhoto) "带照片" else "纯文字")
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = S520JColors.TextSecondary) }
        }
    )
}

@Composable
private fun StatCard(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = "只记录自己做的饭菜，探店不计入；不评价人和关系。",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
    }
}

@Composable
private fun PhotoTile(
    photo: ContentRecord,
    modifier: Modifier = Modifier,
    onOpenPhotos: () -> Unit,
    onEdit: () -> Unit
) {
    val images = photo.mediaIds.take(GalleryPhotoLayout.MAX)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
                .clip(RoundedCornerShape(22.dp))
                .background(S520JColors.AccentPink)
                .clickable(enabled = images.isNotEmpty()) { onOpenPhotos() },
            contentAlignment = Alignment.Center
        ) {
            if (images.isEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Photo,
                    contentDescription = null,
                    tint = S520JColors.PrimarySoft,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                RecordPhotoGrid(
                    images = images,
                    title = photo.title,
                    modifier = Modifier.fillMaxSize()
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
            // 编辑入口：右下角 28dp 半透明按钮，点击不穿透到照片区。
            if (photo.id.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(S520JColors.Surface.copy(alpha = 0.86f))
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = S520JColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
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
