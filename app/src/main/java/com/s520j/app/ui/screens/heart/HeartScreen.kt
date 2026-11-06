package com.s520j.app.ui.screens.heart

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.theme.S520JNumberStyle
import com.s520j.app.ui.util.daysBetween
import com.s520j.app.ui.util.toDayTitle
import com.s520j.app.ui.util.toLocalDate
import com.s520j.app.ui.util.today
import com.s520j.app.ui.util.weekdayName
import java.time.LocalDate
import java.time.YearMonth

private val challengePrompts = listOf(
    "今天认真夸对方一次",
    "一起拍一张今天的照片",
    "给彼此留一句晚安",
    "一起做一件很小的事",
    "说一句谢谢，并说清原因"
)

private val defaultPokes = listOf("拍拍你", "抱抱", "亲亲", "想你")

@Composable
fun HeartScreen(
    vm: AppViewModel,
    onOpenModule: (ContentModule, String?) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val session by vm.session.collectAsState()
    val greeting by vm.greeting.collectAsState()

    val wishesFlow = remember { vm.contents(ContentModule.WISH) }
    val wishes by wishesFlow.collectAsState(initial = emptyList())
    val branchesFlow = remember { vm.contents(ContentModule.CALENDAR_EVENT) }
    val events by branchesFlow.collectAsState(initial = emptyList())
    val momentsFlow = remember { vm.contents(ContentModule.RECENT_MOMENT) }
    val moments by momentsFlow.collectAsState(initial = emptyList())

    var challengeIndex by rememberSaveable { mutableIntStateOf(0) }
    var iDone by rememberSaveable { mutableStateOf(false) }
    var partnerDone by rememberSaveable { mutableStateOf(false) }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(today()) }

    var showGreetingDialog by remember { mutableStateOf(false) }
    var greetingDraft by remember { mutableStateOf(greeting) }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(28.dp))

        // 顶部问候卡片
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${today().toDayTitle()}  ${today().weekdayName()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = S520JColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${session.nickname} 与 ${session.partnerNickname}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
            }
            SoftCard(
                modifier = Modifier.width(96.dp),
                onClick = { showGreetingDialog = true }
            ) {
                Text(
                    text = "相伴",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
                Text(
                    text = "${daysBetween(session.coupleStartAt)}",
                    style = S520JNumberStyle,
                    color = S520JColors.Primary
                )
                Text(
                    text = "天",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        // 今日心动挑战
        SectionHeader(title = "今日心动挑战", subtitle = "双方确认后即为完成")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Text(
                text = challengePrompts[challengeIndex % challengePrompts.size],
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfirmPill("我已完成", iDone) {
                    iDone = !iDone
                    if (iDone && partnerDone) vm.notify("今日挑战已完成")
                }
                ConfirmPill("TA 已完成", partnerDone) {
                    partnerDone = !partnerDone
                    if (iDone && partnerDone) vm.notify("今日挑战已完成")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { challengeIndex += 1 }) {
                    Text("换一换", color = S520JColors.Primary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        // 快速记录
        SectionHeader(title = "快速记录", subtitle = "把刚发生的事记下来")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("照片", "做饭", "探店", "文字", "浪漫").forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(S520JColors.Surface)
                        .clickable { onOpenModule(ContentModule.QUICK_RECORD, label) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = S520JColors.TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        // 心愿
        SectionHeader(
            title = "心愿待实现",
            subtitle = "一起攒下想做的事",
            actionLabel = "查看全部",
            onAction = { onOpenModule(ContentModule.WISH, null) }
        )
        Spacer(Modifier.height(10.dp))
        if (wishes.isEmpty()) {
            SoftCard { EmptyState("还没有心愿，先写下一个吧") }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(wishes, key = { it.id }) { wish ->
                    SoftCard(modifier = Modifier.width(180.dp), onClick = { onOpenModule(ContentModule.WISH, null) }) {
                        Text(
                            text = wish.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = S520JColors.TextPrimary,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = wish.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = S520JColors.TextSecondary,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagPill(wish.extra["状态"] ?: "待回应")
                            TagPill(wish.extra["提出人"] ?: "我")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        // 碰碰你
        SectionHeader(title = "碰碰你", subtitle = "随手给对方一个信号")
        Spacer(Modifier.height(10.dp))
        PokeSection(vm)

        Spacer(Modifier.height(Dimens.SectionGap))

        // 共同日历
        SectionHeader(title = "共同日历", subtitle = "待办、重要时间、纪念日与共同计划")
        Spacer(Modifier.height(6.dp))
        SoftCard {
            MonthCalendar(
                month = month,
                selectedDate = selectedDate,
                eventCounts = events.associate { (it.eventDate?.toLocalDate() ?: today()) to 1 },
                onSelectDate = { selectedDate = it },
                onPrevMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) },
                onToday = {
                    month = YearMonth.now()
                    selectedDate = today()
                }
            )
            Spacer(Modifier.height(10.dp))
            val dayEvents = events.filter { it.eventDate?.toLocalDate() == selectedDate }
            if (dayEvents.isEmpty()) {
                EmptyState(
                    text = "${selectedDate.toDayTitle()} 还没有安排",
                    actionLabel = "添加事项",
                    onAction = { onOpenModule(ContentModule.CALENDAR_EVENT, null) }
                )
            } else {
                dayEvents.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenModule(ContentModule.CALENDAR_EVENT, null) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(S520JColors.Primary, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                            if (event.content.isNotBlank()) {
                                Text(event.content, style = MaterialTheme.typography.bodyMedium, color = S520JColors.TextSecondary)
                            }
                        }
                        TagPill(event.extra["type"] ?: "计划")
                    }
                }
                TextButton(onClick = { onOpenModule(ContentModule.CALENDAR_EVENT, null) }) {
                    Text("管理日历事项", color = S520JColors.Primary)
                }
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        // 最近的美好
        SectionHeader(
            title = "最近的美好",
            subtitle = "你们最近记下的小事",
            actionLabel = "查看全部",
            onAction = { onOpenModule(ContentModule.RECENT_MOMENT, null) }
        )
        Spacer(Modifier.height(10.dp))
        if (moments.isEmpty()) {
            SoftCard {
                EmptyState("还没有记录", actionLabel = "记一条") { onOpenModule(ContentModule.RECENT_MOMENT, null) }
            }
        } else {
            moments.take(3).forEach { moment ->
                MomentCard(moment) { onOpenModule(ContentModule.RECENT_MOMENT, null) }
                Spacer(Modifier.height(Dimens.CardGap))
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showGreetingDialog) {
        AlertDialog(
            onDismissRequest = { showGreetingDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setGreeting(greetingDraft)
                    showGreetingDialog = false
                }) { Text("保存", color = S520JColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showGreetingDialog = false }) { Text("取消") }
            },
            containerColor = S520JColors.Surface,
            title = { Text("编辑寄语") },
            text = {
                com.s520j.app.ui.components.SoftTextField(
                    value = greetingDraft,
                    onValueChange = { greetingDraft = it },
                    label = "寄语",
                    singleLine = false,
                    minLines = 2
                )
            }
        )
    }
}

@Composable
private fun ConfirmPill(text: String, done: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (done) S520JColors.PrimarySoft else S520JColors.Background)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (done) "✓ $text" else text,
            style = MaterialTheme.typography.labelSmall,
            color = if (done) S520JColors.TextPrimary else S520JColors.TextSecondary
        )
    }
}

@Composable
private fun PokeSection(vm: AppViewModel) {
    val customPokes = remember { mutableStateListOf<String>() }
    var showAdd by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var lastSent by remember { mutableStateOf<String?>(null) }

    Column {
        val all = defaultPokes + customPokes
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            all.take(4).forEach { poke ->
                PokeChip(poke, onClick = {
                    lastSent = poke
                    vm.notify("已发送「$poke」")
                })
            }
        }
        if (all.size > 4) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                all.drop(4).forEach { poke ->
                    PokeChip(poke, onClick = {
                        lastSent = poke
                        vm.notify("已发送「$poke」")
                    })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showAdd = true }) {
                Text("＋ 自定义", color = S520JColors.Primary, style = MaterialTheme.typography.labelSmall)
            }
            if (customPokes.isNotEmpty()) {
                TextButton(onClick = { customPokes.clear() }) {
                    Text("清空自定义", color = S520JColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.weight(1f))
            lastSent?.let {
                Text(
                    text = "已发送「$it」",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.Primary
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.isNotBlank()) customPokes.add(draft.trim())
                    draft = ""
                    showAdd = false
                }) { Text("添加", color = S520JColors.Primary) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
            containerColor = S520JColors.Surface,
            title = { Text("自定义碰碰你") },
            text = {
                com.s520j.app.ui.components.SoftTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "动作名称"
                )
            }
        )
    }
}

@Composable
private fun PokeChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(S520JColors.PrimarySoft)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = S520JColors.TextPrimary)
    }
}

@Composable
private fun MomentCard(moment: ContentRecord, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(S520JColors.AccentPink)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = S520JColors.Primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(moment.title, style = MaterialTheme.typography.titleMedium, color = S520JColors.TextPrimary)
                Text(
                    text = moment.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = (moment.eventDate ?: moment.createdAt).toDayTitle(),
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
            com.s520j.app.ui.components.VisibilityChip(moment.visibility)
        }
    }
}
