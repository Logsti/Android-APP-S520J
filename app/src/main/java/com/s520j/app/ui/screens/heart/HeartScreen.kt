package com.s520j.app.ui.screens.heart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.sync.SyncPhase
import com.s520j.app.domain.heart.CalendarSupport
import com.s520j.app.domain.heart.PokeCatalog
import com.s520j.app.domain.heart.QuickRecordTypes
import com.s520j.app.domain.heart.TimeGreeting
import com.s520j.app.domain.heart.WishBoard
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.components.VisibilityChip
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.daysBetween
import com.s520j.app.ui.util.heroDateText
import com.s520j.app.ui.util.toDayTitle
import com.s520j.app.ui.util.toMonthTitle
import com.s520j.app.ui.util.today
import java.text.NumberFormat
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

private data class ChallengePrompt(val text: String, val tags: List<String>)

private val challengePrompts = listOf(
    ChallengePrompt("给对方发一条语音，说出今天最想感谢 TA 的一件小事。", listOf("表达", "轻松")),
    ChallengePrompt("一起拍一张今天的照片，不用好看，只要真实。", listOf("陪伴", "轻松")),
    ChallengePrompt("今天认真夸对方一次，说出具体的一件事。", listOf("表达", "真诚")),
    ChallengePrompt("给彼此留一句晚安，哪怕只有「早点睡」。", listOf("表达", "温柔")),
    ChallengePrompt("一起做一件很小的事，比如一起倒一杯水。", listOf("陪伴", "轻松"))
)

private val sectionGap = 26.dp

@Composable
fun HeartScreen(
    vm: AppViewModel,
    onOpenModule: (ContentModule, String?) -> Unit,
    onOpenEditor: (ContentModule) -> Unit,
    onOpenRecord: (ContentModule, String) -> Unit
) {
    val session by vm.session.collectAsState()
    val greeting by vm.greeting.collectAsState()
    val challenge by vm.challenge.collectAsState()

    val wishesFlow = remember { vm.contents(ContentModule.WISH) }
    val wishes by wishesFlow.collectAsState(initial = emptyList())
    val eventsFlow = remember { vm.contents(ContentModule.CALENDAR_EVENT) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val momentsFlow = remember { vm.contents(ContentModule.ROMANTIC_MOMENT) }
    val moments by momentsFlow.collectAsState(initial = emptyList())

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(today()) }
    val syncState by vm.syncState.collectAsState()

    // 进入页面时补一次同步（内部受 20 秒节流保护）。
    LaunchedEffect(Unit) { vm.syncOnEnter() }

    var showGreetingDialog by remember { mutableStateOf(false) }
    var greetingDraft by remember { mutableStateOf(greeting) }

    val eventCounts = remember(events) { CalendarSupport.eventCounts(events) }
    val dayEvents = remember(events, selectedDate) { CalendarSupport.eventsOn(events, selectedDate) }

    PageContainer(
        onRefresh = vm::syncNow,
        refreshing = syncState.phase == SyncPhase.SYNCING
    ) {
        Spacer(Modifier.height(26.dp))

        GreetingHero(
            nickname = session.nickname.ifBlank { "你" },
            partner = session.partnerNickname.ifBlank { "TA" },
            days = daysBetween(session.coupleStartAt),
            greeting = greeting,
            onEditGreeting = {
                greetingDraft = greeting
                showGreetingDialog = true
            }
        )

        Spacer(Modifier.height(sectionGap))

        ChallengeSection(
            challenge = challenge,
            onRotate = { vm.rotateChallenge() },
            onToggleSelf = { vm.toggleChallengeSelf() }
        )

        Spacer(Modifier.height(sectionGap))

        QuickRecordSection(onOpenEditor)

        Spacer(Modifier.height(sectionGap))

        WishSection(
            wishes = wishes,
            selfId = session.userId,
            partner = session.partnerNickname.ifBlank { "TA" },
            onOpenWish = { onOpenRecord(ContentModule.WISH, it.id) },
            onAccept = { vm.acceptWish(it) },
            onReject = { vm.rejectWish(it) },
            onRealize = { vm.setWishStatus(it, WishBoard.REALIZED) },
            onSeeAll = { onOpenModule(ContentModule.WISH, null) }
        )

        Spacer(Modifier.height(sectionGap))

        HeartSectionHeader(title = "碰碰你")
        Spacer(Modifier.height(12.dp))
        PokeSection(vm)

        Spacer(Modifier.height(sectionGap))

        HeartSectionHeader(title = "共同日历")
        Spacer(Modifier.height(12.dp))
        SoftCard {
            MonthCalendar(
                month = month,
                selectedDate = selectedDate,
                eventCounts = eventCounts,
                onSelectDate = { selectedDate = it },
                onPrevMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) },
                onToday = {
                    month = YearMonth.now()
                    selectedDate = today()
                }
            )
            Spacer(Modifier.height(10.dp))
            if (dayEvents.isEmpty()) {
                EmptyState(text = "${selectedDate.toDayTitle()} 还没有安排")
                Spacer(Modifier.height(4.dp))
            } else {
                dayEvents.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRecord(ContentModule.CALENDAR_EVENT, event.id) }
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
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpenModule(ContentModule.CALENDAR_EVENT, selectedDate.toString()) }) {
                    Text("＋ 在这天添加", color = S520JColors.Primary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onOpenModule(ContentModule.CALENDAR_EVENT, null) }) {
                    Text("管理日历事项", color = S520JColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(sectionGap))

        HeartSectionHeader(
            title = "最近的美好",
            actionLabel = "查看全部",
            onAction = { onOpenModule(ContentModule.ROMANTIC_MOMENT, null) }
        )
        Spacer(Modifier.height(12.dp))
        if (moments.isEmpty()) {
            SoftCard {
                EmptyState("还没有记录", actionLabel = "记一条") {
                    onOpenEditor(ContentModule.ROMANTIC_MOMENT)
                }
            }
        } else {
            moments.take(3).forEach { moment ->
                MomentCard(moment) { onOpenRecord(ContentModule.ROMANTIC_MOMENT, moment.id) }
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
                SoftTextField(
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

/** 参考图 1：鼠尾草绿到暖沙色的渐变问候卡。 */
@Composable
private fun GreetingHero(
    nickname: String,
    partner: String,
    days: Long,
    greeting: String,
    onEditGreeting: () -> Unit
) {
    BoxWithConstraints {
        val compact = maxWidth < 340.dp
        val line = remember(nickname, partner) {
            TimeGreeting.line(LocalTime.now().hour, nickname.ifBlank { "你" }, partner.ifBlank { "TA" })
        }
        val formattedDays = remember(days) {
            NumberFormat.getIntegerInstance(Locale.CHINA).format(days)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(S520JColors.HeroStart, S520JColors.HeroEnd)
                    )
                )
                .clickable(onClick = onEditGreeting)
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Column {
                Text(
                    text = today().heroDateText(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.4.sp,
                    color = S520JColors.PureWhite.copy(alpha = 0.92f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = line,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 18.sp else 20.sp,
                    lineHeight = if (compact) 24.sp else 26.sp,
                    color = S520JColors.PureWhite,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "我们已经在一起",
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = S520JColors.PureWhite.copy(alpha = 0.88f)
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formattedDays,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 46.sp else 54.sp,
                        lineHeight = if (compact) 54.sp else 64.sp,
                        color = S520JColors.PureWhite
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "天",
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        color = S520JColors.PureWhite.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = if (compact) 7.dp else 9.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = greeting,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = S520JColors.PureWhite.copy(alpha = 0.92f)
                )
            }
        }
    }
}

/** 参考图 2：白色圆角挑战卡与整宽绿色按钮。 */
@Composable
private fun ChallengeSection(
    challenge: com.s520j.app.domain.heart.ChallengeState,
    onRotate: () -> Unit,
    onToggleSelf: () -> Unit
) {
    HeartSectionHeader(
        title = "今日心动挑战",
        action = {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onRotate)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "换一换",
                    tint = S520JColors.DeepGreen,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "换一换",
                    fontSize = 13.sp,
                    color = S520JColors.DeepGreen
                )
            }
        }
    )
    Spacer(Modifier.height(12.dp))

    val prompt = challengePrompts[challenge.promptIndex(challengePrompts.size)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = S520JColors.PureWhite,
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                prompt.tags.forEachIndexed { index, tag ->
                    SoftTag(
                        text = tag,
                        background = if (index == 0) S520JColors.PrimarySoft else S520JColors.ChipPeach,
                        foreground = if (index == 0) S520JColors.DeepGreen else S520JColors.WarmBrown
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = prompt.text,
                fontSize = 17.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onToggleSelf,
                enabled = !challenge.selfDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = S520JColors.Primary,
                    contentColor = S520JColors.PureWhite
                )
            ) {
                Text(
                    text = when {
                        challenge.bothDone -> "已完成"
                        challenge.selfDone -> "已标记完成 · 等 TA"
                        else -> "标记完成"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        challenge.bothDone -> "今天的心动挑战已经完成"
                        challenge.partnerDone -> "TA 已经标记完成，等你啦～"
                        challenge.selfDone -> "已标记完成，等 TA 一起完成"
                        else -> "各自标记，两个人都完成就算达成～"
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    color = if (challenge.bothDone) S520JColors.DeepGreen else S520JColors.TextSecondary
                )
            }
        }
    }
}

/** 参考图 3：五个视角胶囊（横向可滚动）+ 竖向全量卡片；每个心愿独立一张卡。 */
@Composable
private fun WishSection(
    wishes: List<ContentRecord>,
    selfId: String,
    partner: String,
    onOpenWish: (ContentRecord) -> Unit,
    onAccept: (ContentRecord) -> Unit,
    onReject: (ContentRecord) -> Unit,
    onRealize: (ContentRecord) -> Unit,
    onSeeAll: () -> Unit
) {
    val groups = remember(wishes, selfId) { WishBoard.groupByView(wishes, selfId) }
    // 用户手动选择后保持不动；未选择时按「待我回应 → 待对方回应 → 已答应」兜底。
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val view = picked ?: WishBoard.defaultView(groups)

    HeartSectionHeader(
        title = "心愿待实现",
        action = {
            Text(
                text = "全部",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                fontSize = 13.sp,
                color = S520JColors.TextSecondary
            )
        }
    )
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WishBoard.views.forEach { item ->
            WishStatusTab(
                text = "$item ${groups[item].orEmpty().size}",
                selected = view == item,
                onClick = { picked = item }
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    val list = groups[view].orEmpty()
    if (list.isEmpty()) {
        WishEmptyCard(view = view, onSeeAll = onSeeAll)
    } else {
        BoxWithConstraints {
            val cardWidth = maxWidth * 0.62f
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.id }) { wish ->
                    WishCard(
                        wish = wish,
                        selfId = selfId,
                        partner = partner,
                        onOpen = { onOpenWish(wish) },
                        onAccept = { onAccept(wish) },
                        onReject = { onReject(wish) },
                        onRealize = { onRealize(wish) },
                        modifier = Modifier
                            .width(cardWidth)
                            .height(244.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WishStatusTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) S520JColors.PrimarySoft else S520JColors.PureWhite)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (selected) S520JColors.DeepGreen else S520JColors.TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun WishCard(
    wish: ContentRecord,
    selfId: String,
    partner: String,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRealize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = WishBoard.statusOf(wish)
    val view = WishBoard.viewOf(wish, selfId)
    val dateLabel = (wish.eventDate ?: wish.createdAt).toMonthTitle()
    val proposer = WishBoard.proposerLabel(wish, selfId, partner)
    val accepter = WishBoard.accepterLabel(wish, selfId, partner)
    val subtitle = when (view) {
        WishBoard.WAITING_ME -> "$partner 提出 · 等你回应"
        WishBoard.WAITING_PARTNER -> "等待 $partner 回应"
        WishBoard.ACCEPTED -> "$proposer 提出 · $accepter 答应"
        WishBoard.REALIZED -> "$proposer 提出 · 已经实现"
        else -> "$proposer 提出 · 已失效"
    }
    var showRejectConfirm by remember(wish.id) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = S520JColors.PureWhite,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(18.dp)) {
            Icon(
                imageVector = Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = S520JColors.WarmBrown,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = wish.title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                lineHeight = 26.sp,
                color = S520JColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = S520JColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoftTag(
                    text = dateLabel,
                    background = S520JColors.ChipPeach,
                    foreground = S520JColors.WarmBrown
                )
                Spacer(Modifier.weight(1f))
                when (status) {
                    WishBoard.ACCEPTED -> SoftTag(
                        text = status,
                        background = S520JColors.PrimarySoft,
                        foreground = S520JColors.DeepGreen
                    )
                    WishBoard.REALIZED -> SoftTag(
                        text = status,
                        background = S520JColors.Primary,
                        foreground = S520JColors.PureWhite
                    )
                    WishBoard.REJECTED -> SoftTag(
                        text = WishBoard.INVALID,
                        background = S520JColors.Sand,
                        foreground = S520JColors.TextSecondary
                    )
                    else -> SoftTag(
                        text = status,
                        background = S520JColors.Sand,
                        foreground = S520JColors.TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            when (view) {
                // 只有「待我回应」才允许答应/拒绝。
                WishBoard.WAITING_ME -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WishActionButton(
                        text = "答应",
                        filled = true,
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )
                    WishActionButton(
                        text = "拒绝",
                        filled = false,
                        onClick = { showRejectConfirm = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 我提出的心愿只能等待对方回应，不给任何操作按钮。
                WishBoard.WAITING_PARTNER -> WishActionButton(
                    text = "等待 $partner 回应",
                    filled = false,
                    onClick = null,
                    modifier = Modifier.fillMaxWidth()
                )
                WishBoard.ACCEPTED -> WishActionButton(
                    text = "标记实现",
                    filled = true,
                    onClick = onRealize,
                    modifier = Modifier.fillMaxWidth()
                )
                else -> WishActionButton(
                    text = if (status == WishBoard.REALIZED) "已实现" else "已失效",
                    filled = false,
                    onClick = null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showRejectConfirm) {
        AlertDialog(
            onDismissRequest = { showRejectConfirm = false },
            containerColor = S520JColors.Surface,
            title = { Text("拒绝这个心愿？") },
            text = { Text("拒绝后心愿会归入「失效」，双方都不能再标记实现。") },
            confirmButton = {
                TextButton(onClick = {
                    showRejectConfirm = false
                    onReject()
                }) {
                    Text("拒绝", color = Color(0xFFB3261E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectConfirm = false }) { Text("再想想") }
            }
        )
    }
}

/** 心愿卡底部动作按钮；[onClick] 为 null 时只读展示。 */
@Composable
private fun WishActionButton(
    text: String,
    filled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) S520JColors.Primary else S520JColors.Sand)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (filled) S520JColors.PureWhite else S520JColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun WishEmptyCard(view: String, onSeeAll: () -> Unit) {
    val text = when (view) {
        WishBoard.WAITING_ME -> "还没有需要你回应的心愿"
        WishBoard.WAITING_PARTNER -> "还没有等待对方回应的心愿"
        WishBoard.INVALID -> "还没有失效的心愿"
        else -> "还没有「$view」的心愿"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(22.dp),
        color = S520JColors.PureWhite,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                color = S520JColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSeeAll) {
                Text("去许一个心愿", color = S520JColors.Primary, fontSize = 13.sp)
            }
        }
    }
}

/** 参考图 4：四个等宽白色小卡。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PokeSection(vm: AppViewModel) {
    val custom by vm.customPokes.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PokeCatalog.defaults.forEach { poke ->
                PokeCard(
                    label = poke,
                    icon = pokeIcon(poke),
                    modifier = Modifier.weight(1f),
                    onClick = { vm.sendPoke(poke) }
                )
            }
        }
        if (custom.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "自定义",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                custom.forEach { poke ->
                    Box {
                        PokeCard(
                            label = poke,
                            icon = Icons.Outlined.AutoAwesome,
                            modifier = Modifier.width(84.dp),
                            onClick = { vm.sendPoke(poke) }
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .clickable { vm.removeCustomPoke(poke) }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "×",
                                fontSize = 12.sp,
                                color = S520JColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showAdd = true }) {
                Text("＋ 自定义", color = S520JColors.Primary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "发送后进入通知中心未读",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.addCustomPoke(draft)
                    draft = ""
                    showAdd = false
                }) { Text("添加", color = S520JColors.Primary) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
            containerColor = S520JColors.Surface,
            title = { Text("自定义碰碰你") },
            text = {
                SoftTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "动作名称",
                    placeholder = "最多 ${PokeCatalog.MAX_NAME_LENGTH} 个字"
                )
            }
        )
    }
}

@Composable
private fun PokeCard(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = S520JColors.PureWhite,
        border = BorderStroke(1.dp, S520JColors.Divider.copy(alpha = 0.55f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = S520JColors.WarmBrown,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = S520JColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun pokeIcon(name: String): ImageVector = when (name) {
    "拍一拍" -> Icons.Filled.WavingHand
    "抱抱" -> Icons.Outlined.SentimentSatisfied
    "亲亲" -> Icons.Outlined.FavoriteBorder
    "想你" -> Icons.Outlined.ChatBubbleOutline
    else -> Icons.Outlined.AutoAwesome
}

/** 参考图 5：五个圆形图标入口单行排列；随手记走标题右侧角标。 */
@Composable
private fun QuickRecordSection(onOpenEditor: (ContentModule) -> Unit) {
    HeartSectionHeader(
        title = "快速记录",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onOpenEditor(ContentModule.JOT) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = "随手记",
                        tint = S520JColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onOpenEditor(ContentModule.LETTER) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MailOutline,
                        contentDescription = "一封情书",
                        tint = S520JColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
    Spacer(Modifier.height(14.dp))
    BoxWithConstraints {
        val itemWidth = maxWidth / QuickRecordTypes.primary.size.toFloat()
        val circle = minOf(54.dp, itemWidth)
        Row {
            QuickRecordTypes.primary.forEach { type ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(circle)
                            .clip(CircleShape)
                            .background(S520JColors.PrimarySoft)
                            .clickable {
                                QuickRecordTypes.targetModuleOf(type)?.let(onOpenEditor)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = quickIcon(type),
                            contentDescription = type,
                            tint = S520JColors.DeepGreen,
                            modifier = Modifier.size(circle * 0.44f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = type,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = S520JColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun quickIcon(type: String): ImageVector = when (type) {
    QuickRecordTypes.PHOTO -> Icons.Outlined.PhotoCamera
    QuickRecordTypes.COOKING -> Icons.Outlined.Restaurant
    QuickRecordTypes.SHOP -> Icons.Outlined.Storefront
    QuickRecordTypes.TRAVEL -> Icons.Outlined.Luggage
    QuickRecordTypes.JOT -> Icons.Outlined.EditNote
    else -> Icons.Outlined.AutoAwesome
}

@Composable
private fun HeartSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            color = S520JColors.TextPrimary
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                fontSize = 13.sp,
                color = S520JColors.TextSecondary
            )
        }
        action?.invoke()
    }
}

@Composable
private fun SoftTag(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = foreground,
            maxLines = 1
        )
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
            VisibilityChip(moment.visibility)
        }
    }
}
