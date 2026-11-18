package com.s520j.app.ui.screens.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.s520j.app.R
import com.s520j.app.domain.food.DishCandidate
import com.s520j.app.domain.food.DishGroup
import com.s520j.app.domain.food.DishPicker
import com.s520j.app.domain.food.DishPickerState
import com.s520j.app.domain.food.DishTaxonomy
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.EmptyState
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.showTimedMessage
import com.s520j.app.ui.theme.CardCorner
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import java.io.File

/**
 * 今天吃啥：头图 + 荤/素/汤/主食四组合并卡 + 随机结果 + 确认 + 点过的菜。
 * 只使用点菜专用接口，不写入「一起做过的菜」、照片墙或内容同步。
 */
@Composable
fun DishPickerScreen(
    onBack: () -> Unit,
    vm: DishPickerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedGroup by remember { mutableStateOf<DishGroup?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 操作提示复用全局时长规则（普通 2 秒，自动消失）。
    LaunchedEffect(Unit) {
        vm.messages.collect { message -> snackbarHostState.showTimedMessage(message) }
    }

    val headerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.setHeader(uri)
    }
    val openHeaderPicker: () -> Unit = {
        headerPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val picker = state.picker
    val busy = state.loading || state.swappingName != null

    Box(Modifier.fillMaxSize()) {
        PageContainer {
            BackBar(title = "今天吃啥", onBack = onBack)

            HeaderImage(
                path = state.headerPath,
                onClick = openHeaderPicker,
                onRestore = vm::resetHeader
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "今天吃什么，交给一点点运气。",
                fontSize = 11.5.sp,
                letterSpacing = 0.4.sp,
                color = S520JColors.TextSecondary
            )
            if (state.offline) {
                Spacer(Modifier.height(8.dp))
                OfflineNotice()
            }
            Spacer(Modifier.height(12.dp))

            TableSetupCard(
                picker = picker,
                categories = state.categories,
                expandedGroup = expandedGroup,
                enabled = !state.loading,
                onToggleExpand = { group ->
                    expandedGroup = if (expandedGroup == group) null else group
                },
                onSubCategory = { group, sub ->
                    expandedGroup = null
                    vm.setSubCategory(group, sub)
                },
                onCount = { group, count -> vm.setCount(group, count) },
                onPreferNew = vm::togglePreferNew
            )

            Spacer(Modifier.height(12.dp))
            RandomTableButton(
                enabled = DishPicker.canPick(picker) && !busy,
                onClick = {
                    expandedGroup = null
                    if (picker.hasResult) vm.newTable() else vm.pick()
                }
            )
            if (!DishPicker.canPick(picker)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "至少选择 1 道菜",
                    fontSize = 10.5.sp,
                    color = S520JColors.TextSecondary
                )
            }

            Spacer(Modifier.height(Dimens.SectionGap))

            TableSection(
                picker = picker,
                loading = state.loading,
                swappingName = state.swappingName,
                error = state.error,
                confirmState = state.confirmState,
                onSwap = vm::swap,
                onRetry = vm::retry,
                onConfirm = vm::confirmTable
            )

            Spacer(Modifier.height(Dimens.SectionGap))

            HistorySection(
                count = state.history.picked.size,
                names = state.history.picked.take(HISTORY_CHIP_LIMIT).map { it.name },
                onClear = { showClearConfirm = true }
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = "菜谱实时抓取后存入云端池（上限 10 万道），本机不保存菜谱数据；" +
                    "云端不可用时使用内置 90 道兜底。",
                fontSize = 10.sp,
                lineHeight = 17.sp,
                letterSpacing = 0.2.sp,
                color = S520JColors.TextSecondary.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(96.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Dimens.PageHorizontalPadding, vertical = 24.dp)
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = S520JColors.Surface,
            title = { Text("清空点过的菜？") },
            text = { Text("会清空双方共享的点菜记录，全部菜品重新可抽；不影响任何内容记录。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    vm.clearHistory()
                }) {
                    Text("清空", color = S520JColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = S520JColors.TextSecondary)
                }
            }
        )
    }
}

/** 「点过的菜」最多展示的胶囊数量，超出的折成「+N」。 */
private const val HISTORY_CHIP_LIMIT = 12

private val CardShape = RoundedCornerShape(20.dp)

/** 无内边距的卡片：行式内容自己控制左右 14dp 与分隔线，保证通栏分隔线。 */
@Composable
private fun FlatCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardCorner,
        color = S520JColors.Surface,
        border = BorderStroke(1.dp, S520JColors.Divider.copy(alpha = 0.6f)),
        shadowElevation = 1.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = S520JColors.Divider.copy(alpha = 0.55f)
    )
}

// ---------------------------------------------------------------- 头图

@Composable
private fun HeaderImage(path: String?, onClick: () -> Unit, onRestore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(CardShape)
            .background(S520JColors.Sand)
            .clickable(onClick = onClick)
    ) {
        if (path.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.dish_header_default),
                contentDescription = "今天吃啥头图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = File(path),
                contentDescription = "今天吃啥头图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            Text(
                text = "今天吃啥",
                fontSize = 27.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = S520JColors.PureWhite
            )
            Text(
                text = "荤菜 · 素菜 · 汤 · 主食　随机配一桌",
                fontSize = 11.5.sp,
                letterSpacing = 1.sp,
                color = S520JColors.PureWhite.copy(alpha = 0.9f)
            )
        }
        GlassPill(
            text = "点我换图",
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
        if (!path.isNullOrBlank()) {
            GlassPill(
                text = "恢复默认",
                onClick = onRestore,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
    }
}

/** 头图上的胶囊：白 24% 底 + 1dp 白 45% 描边，外框保证触控 ≥44dp。 */
@Composable
private fun GlassPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(S520JColors.PureWhite.copy(alpha = 0.24f))
                .border(
                    width = 1.dp,
                    color = S520JColors.PureWhite.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                maxLines = 1,
                color = S520JColors.PureWhite
            )
        }
    }
}

@Composable
private fun OfflineNotice() {
    Surface(shape = RoundedCornerShape(999.dp), color = S520JColors.Sand.copy(alpha = 0.6f)) {
        Text(
            text = "离线模式，菜谱范围缩小",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 11.sp,
            color = S520JColors.TextSecondary
        )
    }
}

// ---------------------------------------------------------------- 四组份数

@Composable
private fun TableSetupCard(
    picker: DishPickerState,
    categories: Map<DishGroup, List<String>>,
    expandedGroup: DishGroup?,
    enabled: Boolean,
    onToggleExpand: (DishGroup) -> Unit,
    onSubCategory: (DishGroup, String) -> Unit,
    onCount: (DishGroup, Int) -> Unit,
    onPreferNew: () -> Unit
) {
    FlatCard {
        DishGroup.order.forEachIndexed { index, group ->
            if (index > 0) RowDivider()
            GroupRow(
                group = group,
                count = picker.countOf(group),
                subCategory = picker.subOf(group),
                options = DishTaxonomy.optionsFor(group, categories),
                expanded = expandedGroup == group,
                enabled = enabled,
                onToggleExpand = { onToggleExpand(group) },
                onSubCategory = { sub -> onSubCategory(group, sub) },
                onCount = { count -> onCount(group, count) }
            )
        }
        RowDivider()
        SummaryRow(picker = picker, onPreferNew = onPreferNew)
    }
}

@Composable
private fun GroupRow(
    group: DishGroup,
    count: Int,
    subCategory: String,
    options: List<String>,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpand: () -> Unit,
    onSubCategory: (String) -> Unit,
    onCount: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.label,
            modifier = Modifier.widthIn(min = 44.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = S520JColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            FilterChip(
                text = subCategory,
                enabled = enabled,
                onClick = onToggleExpand
            )
            DropdownMenu(expanded = expanded, onDismissRequest = onToggleExpand) {
                options.forEach { option ->
                    val selected = option == subCategory
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 44.dp),
                        text = {
                            Text(
                                text = option,
                                fontSize = 13.sp,
                                color = if (selected) S520JColors.Primary else S520JColors.TextPrimary
                            )
                        },
                        onClick = { onSubCategory(option) }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        CountStepper(count = count, enabled = enabled, onCount = onCount)
    }
}

/** 二级分类筛选胶囊：Sand 底 + 右侧小三角，点击弹出同一份筛选菜单。 */
@Composable
private fun FilterChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    S520JColors.Sand.copy(alpha = if (enabled) 0.55f else 0.3f)
                )
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                color = S520JColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "▾",
                fontSize = 9.sp,
                color = S520JColors.TextSecondary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CountStepper(count: Int, enabled: Boolean, onCount: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(
            label = "−",
            plus = false,
            enabled = enabled && count > 0,
            onClick = { onCount(count - 1) }
        )
        Text(
            text = count.toString(),
            modifier = Modifier.width(22.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = S520JColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        StepperButton(
            label = "+",
            plus = true,
            enabled = enabled && count < DishPicker.MAX_PER_GROUP,
            onClick = { onCount(count + 1) }
        )
    }
}

/** 触控区 44dp，内部圆 30dp：减号 Sand 底，加号 Primary 底白字。 */
@Composable
private fun StepperButton(
    label: String,
    plus: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        plus && enabled -> S520JColors.Primary
                        plus -> S520JColors.Primary.copy(alpha = 0.4f)
                        enabled -> S520JColors.Sand.copy(alpha = 0.55f)
                        else -> S520JColors.Sand.copy(alpha = 0.3f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = when {
                    plus && enabled -> S520JColors.PureWhite
                    enabled -> S520JColors.TextSecondary
                    else -> S520JColors.TextSecondary.copy(alpha = 0.5f)
                }
            )
        }
    }
}

@Composable
private fun SummaryRow(picker: DishPickerState, onPreferNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(S520JColors.Sand.copy(alpha = 0.32f))
            .heightIn(min = 54.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "这一桌 ${picker.totalCount} 道",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = S520JColors.TextPrimary,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = DishGroup.order.joinToString(" · ") { group ->
                    "${group.label} ${picker.countOf(group)}"
                },
                fontSize = 10.5.sp,
                letterSpacing = 0.3.sp,
                color = S520JColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "新菜优先",
            fontSize = 11.5.sp,
            color = S520JColors.TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.width(7.dp))
        Switch(
            checked = picker.preferNew,
            onCheckedChange = { onPreferNew() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = S520JColors.PureWhite,
                checkedTrackColor = S520JColors.Primary
            )
        )
    }
}

@Composable
private fun RandomTableButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(47.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = S520JColors.Primary,
            contentColor = S520JColors.PureWhite,
            disabledContainerColor = S520JColors.Sand,
            disabledContentColor = S520JColors.TextSecondary
        )
    ) {
        Text(
            text = "换一桌",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}

// ---------------------------------------------------------------- 这一桌

@Composable
private fun TableSection(
    picker: DishPickerState,
    loading: Boolean,
    swappingName: String?,
    error: String?,
    confirmState: ConfirmState,
    onSwap: (DishCandidate) -> Unit,
    onRetry: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "这一桌",
            modifier = Modifier.weight(1f),
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = S520JColors.TextPrimary,
            maxLines = 1
        )
        if (picker.table.isNotEmpty()) {
            ConfirmStatePill(confirmState)
        }
    }
    Spacer(Modifier.height(9.dp))

    when {
        loading -> SoftCard { EmptyState("正在配菜，稍等一下") }
        picker.table.isEmpty() && error != null -> SoftCard {
            EmptyState(error, actionLabel = "重试", onAction = onRetry)
        }
        picker.table.isEmpty() -> SoftCard {
            EmptyState("还没有配菜，选好份数点「换一桌」")
        }
        else -> {
            FlatCard {
                picker.table.forEachIndexed { index, dish ->
                    if (index > 0) RowDivider()
                    DishRow(
                        dish = dish,
                        busy = swappingName != null,
                        swapping = swappingName == dish.name,
                        onSwap = { onSwap(dish) }
                    )
                }
            }
            picker.note?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    fontSize = 11.sp,
                    color = S520JColors.TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
            ConfirmTableButton(confirmState = confirmState, onConfirm = onConfirm)
            Spacer(Modifier.height(7.dp))
            Text(
                text = "确认后才会计入「点过的菜」",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 10.5.sp,
                letterSpacing = 0.3.sp,
                color = S520JColors.TextSecondary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConfirmStatePill(confirmState: ConfirmState) {
    val label: String
    val background: Color
    val foreground: Color
    when (confirmState) {
        ConfirmState.PENDING -> {
            label = "待确认"
            background = S520JColors.Sand
            foreground = S520JColors.TextSecondary
        }
        ConfirmState.SUBMITTING -> {
            label = "提交中"
            background = S520JColors.Sand.copy(alpha = 0.6f)
            foreground = S520JColors.TextSecondary
        }
        ConfirmState.CONFIRMED -> {
            label = "已确认"
            background = S520JColors.PrimarySoft
            foreground = S520JColors.DeepGreen
        }
        ConfirmState.FAILED -> {
            label = "未记上"
            background = S520JColors.Sand
            foreground = MaterialTheme.colorScheme.error
        }
    }
    Surface(shape = RoundedCornerShape(999.dp), color = background) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 10.5.sp,
            color = foreground,
            maxLines = 1
        )
    }
}

@Composable
private fun DishRow(
    dish: DishCandidate,
    busy: Boolean,
    swapping: Boolean,
    onSwap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = dish.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = S520JColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = dish.tag,
                fontSize = 10.5.sp,
                letterSpacing = 0.3.sp,
                color = S520JColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .clickable(enabled = !busy, onClick = onSwap),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(S520JColors.PrimarySoft.copy(alpha = 0.3f))
                    .border(
                        width = 1.dp,
                        color = S520JColors.PrimarySoft,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (swapping) "换菜中" else "换一道",
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    color = if (busy) S520JColors.TextSecondary else S520JColors.Primary
                )
            }
        }
    }
}

@Composable
private fun ConfirmTableButton(confirmState: ConfirmState, onConfirm: () -> Unit) {
    val confirmed = confirmState == ConfirmState.CONFIRMED
    val submitting = confirmState == ConfirmState.SUBMITTING
    val label = when {
        confirmed -> "已记入点过的菜"
        submitting -> "提交中…"
        confirmState == ConfirmState.FAILED -> "重试确认"
        else -> "就吃这一桌"
    }
    Button(
        onClick = onConfirm,
        enabled = !confirmed && !submitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (confirmed || submitting) S520JColors.Divider else S520JColors.Primary
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = S520JColors.Surface,
            contentColor = S520JColors.Primary,
            disabledContainerColor = S520JColors.Background,
            disabledContentColor = S520JColors.TextSecondary
        )
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------- 点过的菜

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySection(
    count: Int,
    names: List<String>,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "点过的菜",
            modifier = Modifier.weight(1f),
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = S520JColors.TextPrimary,
            maxLines = 1
        )
        Text(
            text = "共 $count 道",
            fontSize = 11.5.sp,
            color = S520JColors.TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "·",
            fontSize = 11.5.sp,
            color = S520JColors.TextSecondary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .clickable(enabled = count > 0, onClick = onClear),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "清空",
                fontSize = 11.5.sp,
                maxLines = 1,
                color = if (count > 0) S520JColors.Primary
                else S520JColors.TextSecondary.copy(alpha = 0.5f)
            )
        }
    }
    Spacer(Modifier.height(9.dp))
    if (names.isEmpty()) {
        Text(
            text = "还没有点过的菜，随机一桌再确认就开始记了。",
            fontSize = 11.5.sp,
            color = S520JColors.TextSecondary
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            names.forEach { name -> HistoryChip(name) }
            val extra = count - names.size
            if (extra > 0) HistoryChip("+$extra", highlighted = true)
        }
    }
}

@Composable
private fun HistoryChip(text: String, highlighted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) S520JColors.Sand.copy(alpha = 0.55f) else S520JColors.Surface,
        border = BorderStroke(1.dp, S520JColors.Divider.copy(alpha = 0.8f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            color = S520JColors.TextSecondary,
            maxLines = 1
        )
    }
}
