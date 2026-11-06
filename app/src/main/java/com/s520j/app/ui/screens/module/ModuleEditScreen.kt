package com.s520j.app.ui.screens.module

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.Visibility
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PrimaryPillButton
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.epochMillis
import com.s520j.app.ui.util.toLocalDate
import java.time.LocalDate

private val photoCategories = listOf("日常生活", "做饭", "探店", "旅行", "纪念日")

@Composable
fun ModuleEditScreen(
    vm: AppViewModel,
    module: ContentModule,
    recordId: String?,
    onBack: () -> Unit
) {
    val flow = remember(module) { vm.contents(module) }
    val records by flow.collectAsState(initial = emptyList())
    val existing = remember(recordId, records) { records.firstOrNull { it.id == recordId } }

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(module.defaultVisibility) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var rating by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf("日常生活") }
    var accepted by remember { mutableStateOf(false) }
    val mediaUris = remember { mutableStateListOf<String>() }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            title = existing.title
            content = existing.content
            visibility = existing.visibility
            eventDate = existing.eventDate
            rating = existing.extra["星级"]?.toIntOrNull() ?: 0
            category = existing.extra["category"] ?: "日常生活"
            accepted = existing.extra["确认"] == "双方已确认"
            mediaUris.clear()
            mediaUris.addAll(existing.mediaIds)
            loaded = true
        }
    }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { mediaUris.add(it.toString()) }
    }

    val initialDate = (eventDate ?: System.currentTimeMillis()).toLocalDate()

    PageContainer {
        BackBar(title = if (existing == null) "新增${module.label}" else "编辑${module.label}", onBack = onBack)
        Spacer(Modifier.height(6.dp))

        SoftCard {
            SoftTextField(
                value = title,
                onValueChange = { title = it },
                label = "标题",
                placeholder = "给这件小事起个名字"
            )
            Spacer(Modifier.height(12.dp))
            SoftTextField(
                value = content,
                onValueChange = { content = it },
                label = "内容",
                placeholder = "写下当时的样子",
                singleLine = false,
                minLines = 3
            )
        }

        Spacer(Modifier.height(Dimens.CardGap))

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "日期",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                TagPill(eventDate?.toDayText() ?: "未设置")
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            eventDate = LocalDate.of(year, month + 1, day).epochMillis()
                        },
                        initialDate.year,
                        initialDate.monthValue - 1,
                        initialDate.dayOfMonth
                    ).show()
                }) { Text("选择", color = S520JColors.Primary) }
            }
        }

        Spacer(Modifier.height(Dimens.CardGap))

        // 可见范围：仅两档，默认仅自己可见
        SoftCard {
            Text("可见范围", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Visibility.entries.forEach { option ->
                    val selected = visibility == option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) S520JColors.Primary else S520JColors.Background)
                            .clickable { visibility = option }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else S520JColors.TextSecondary
                        )
                    }
                }
            }
            if (visibility == Visibility.PRIVATE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "私人内容对方不可见，也不可申请查看。",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
        }

        if (module.needsPhoto) {
            Spacer(Modifier.height(Dimens.CardGap))
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "照片",
                        style = MaterialTheme.typography.titleMedium,
                        color = S520JColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = S520JColors.Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加", color = S520JColors.Primary)
                    }
                }
                if (mediaUris.isEmpty()) {
                    Text(
                        text = "照片会继承本条记录的可见范围。",
                        style = MaterialTheme.typography.labelSmall,
                        color = S520JColors.TextSecondary
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        mediaUris.take(4).forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        TextButton(onClick = { mediaUris.clear() }) { Text("清空", color = S520JColors.TextSecondary) }
                    }
                }
            }
        }

        if (module.supportsRating) {
            Spacer(Modifier.height(Dimens.CardGap))
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "五星榜",
                        style = MaterialTheme.typography.titleMedium,
                        color = S520JColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    for (index in 1..5) {
                        Icon(
                            imageVector = if (index <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "$index 星",
                            tint = S520JColors.Primary,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { rating = if (rating == index) 0 else index }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "只用于菜品、餐厅与体验，不评价人和关系。",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
        }

        if (module == ContentModule.PHOTO) {
            Spacer(Modifier.height(Dimens.CardGap))
            SoftCard {
                Text("分类", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photoCategories.forEach { item ->
                        val selected = category == item
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) S520JColors.PrimarySoft else S520JColors.Background)
                                .clickable { category = item }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) S520JColors.TextPrimary else S520JColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (module == ContentModule.RELATIONSHIP_RULE) {
            Spacer(Modifier.height(Dimens.CardGap))
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("双方认可", style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                        Text(
                            text = if (accepted) "双方已确认" else "等待对方确认",
                            style = MaterialTheme.typography.labelSmall,
                            color = S520JColors.TextSecondary
                        )
                    }
                    TextButton(onClick = { accepted = !accepted }) {
                        Text(if (accepted) "撤销" else "确认", color = S520JColors.Primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryPillButton(
            text = if (existing == null) "保存" else "保存修改",
            onClick = {
                vm.saveRecord(
                    existing = existing,
                    module = module,
                    title = title,
                    content = content,
                    visibility = visibility,
                    eventDate = eventDate,
                    rating = rating,
                    photoType = if (module == ContentModule.PHOTO) category else "",
                    extra = buildMap {
                        if (module == ContentModule.RELATIONSHIP_RULE) {
                            put("确认", if (accepted) "双方已确认" else "等待对方确认")
                        }
                    }
                )
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(40.dp))
    }
}

private fun Long.toDayText(): String = toLocalDate().toString()
