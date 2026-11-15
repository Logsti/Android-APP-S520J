package com.s520j.app.ui.screens.us

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.data.local.BindState
import com.s520j.app.data.space.PendingAction
import com.s520j.app.data.space.SpaceStateMachine
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.DetailRow
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PrimaryPillButton
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.nowMillis
import com.s520j.app.ui.util.toShortDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SpaceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val space by vm.space.collectAsState()
    val session by vm.session.collectAsState()
    val bindFailure by vm.bindFailure.collectAsState()
    val readOnly by vm.isReadOnly.collectAsState()

    var nameInput by rememberSaveable { mutableStateOf("") }
    var introInput by rememberSaveable { mutableStateOf("") }
    var bindCodeInput by rememberSaveable { mutableStateOf("") }
    var showUnbindDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editIntro by rememberSaveable { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageScroll = rememberScrollState()
    val current = space
    val bindState = SpaceStateMachine.stateOf(current?.bindState)
    val unbindPending = bindState == BindState.REQUEST_PENDING &&
        PendingAction.from(current?.pendingAction) == PendingAction.UNBIND

    // 进入页面即向云端拉取一次空间状态，双方确认后无需手动刷新即可看到最新结果。
    LaunchedEffect(Unit) { vm.refreshSpaceFromCloud() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            vm.notify(if (written) "已导出共同空间归档数据" else "导出失败，请重试")
        }
    }
    val onExport: () -> Unit = {
        vm.exportJson { json ->
            pendingExport = json
            exportLauncher.launch("s520j-space-${System.currentTimeMillis()}.json")
        }
    }

    PageContainer(scrollState = pageScroll) {
        BackBar(title = "共同空间", onBack = onBack)
        Spacer(Modifier.height(6.dp))

        SoftCard(containerColor = S520JColors.Sand) {
            Text(
                text = "关于共同空间",
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            SpaceRuleLine("一个空间最多两名成员：你和 TA。")
            SpaceRuleLine("双方账号不合并，各自独立登录与恢复。")
            SpaceRuleLine("共同内容双方可见，私人内容仅本人可见。")
            SpaceRuleLine("解绑后空间冻结归档，共同内容只读，双方都可导出。")
            SpaceRuleLine("重新绑定后空间解冻，历史内容保留。")
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "当前状态",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            TagPill(if (unbindPending) "解绑申请中" else (bindState ?: BindState.UNBOUND).label)
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        if (current == null || bindState == null || bindState == BindState.UNBOUND) {
            if (bindFailure != null) {
                BindFailureCard(
                    reason = bindFailure.orEmpty(),
                    onClose = { vm.clearBindFailure() }
                )
                Spacer(Modifier.height(Dimens.CardGap))
            }

            SoftCard(containerColor = S520JColors.Sand) {
                Text(
                    text = "共同空间未绑定",
                    style = MaterialTheme.typography.titleMedium,
                    color = S520JColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "两种方式任选其一：创建共同空间后把绑定码发给 TA，或者直接输入 TA 生成的绑定码加入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
            }
            Spacer(Modifier.height(Dimens.CardGap))

            SectionHeader(title = "创建共同空间", subtitle = "创建后立即生成 24 小时有效的绑定码")
            Spacer(Modifier.height(10.dp))
            SoftCard {
                SoftTextField(
                    value = nameInput,
                    // 云端限制空间名称不超过 20 个字，这里同步截断，避免提交后才报错。
                    onValueChange = { nameInput = it.take(20) },
                    label = "空间名称",
                    placeholder = "例如：我们的小屋"
                )
                Spacer(Modifier.height(10.dp))
                SoftTextField(
                    value = introInput,
                    onValueChange = { introInput = it },
                    label = "空间简介",
                    placeholder = "一句话写下这个空间的意义",
                    singleLine = false,
                    minLines = 2
                )
                Spacer(Modifier.height(14.dp))
                PrimaryPillButton(
                    text = "创建并生成绑定码",
                    onClick = {
                        vm.createSpace(nameInput.trim(), introInput.trim())
                        nameInput = ""
                        introInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !readOnly && nameInput.isNotBlank()
                )
            }
            Spacer(Modifier.height(Dimens.CardGap))

            SectionHeader(title = "或使用对方的绑定码", subtitle = "输入 TA 生成的绑定码即可申请加入")
            Spacer(Modifier.height(10.dp))
            BindCodeInputCard(
                value = bindCodeInput,
                enabled = !readOnly,
                onValueChange = { bindCodeInput = it },
                // 绑定码输入框在页面最底部：聚焦后直接滚到底，保证键盘弹出时仍完整可见。
                onInputFocused = {
                    scope.launch {
                        delay(350)
                        runCatching { pageScroll.animateScrollTo(pageScroll.maxValue) }
                    }
                },
                onSubmit = {
                    vm.submitBindCode(bindCodeInput.trim())
                    bindCodeInput = ""
                }
            )
        } else {
            SectionHeader(
                title = "空间信息",
                subtitle = "账号不合并，内容按可见范围共享",
                actionLabel = "编辑资料",
                onAction = {
                    editName = current.name
                    editIntro = current.intro
                    showEditDialog = true
                }
            )
            Spacer(Modifier.height(10.dp))
            SoftCard {
                DetailRow("空间名称", current.name)
                DetailRow("空间简介", current.intro.ifBlank { "未填写" })
                DetailRow("绑定状态", bindState?.label ?: current.bindState)
                DetailRow("创建者", creatorLabel(current.createdBy, session.userId))
                DetailRow("创建时间", current.createdAt.toShortDate())
            }

            when (bindState) {
                BindState.INVITE_GENERATED -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    BindCodeCard(
                        code = current.bindCode.orEmpty(),
                        expiresAt = current.bindCodeExpiresAt,
                        onRefresh = { vm.refreshBindCode() }
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryPillButton(
                        text = "已分享给对方，开始等待",
                        onClick = { vm.shareInvite() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !readOnly
                    )
                }

                BindState.WAITING_PARTNER -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    BindCodeCard(
                        code = current.bindCode.orEmpty(),
                        expiresAt = current.bindCodeExpiresAt,
                        onRefresh = { vm.refreshBindCode() }
                    )
                    Spacer(Modifier.height(Dimens.CardGap))
                    SoftCard(containerColor = S520JColors.Sand) {
                        Text(
                            text = "等待对方加入",
                            style = MaterialTheme.typography.titleMedium,
                            color = S520JColors.TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "把上面的绑定码发给 TA，让 TA 在自己的手机上打开「共同空间」输入绑定码；双方各确认一次后即可完成绑定。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = S520JColors.TextSecondary
                        )
                    }
                }

                BindState.REQUEST_PENDING -> {
                    val action = PendingAction.from(current.pendingAction) ?: PendingAction.BIND
                    Spacer(Modifier.height(Dimens.CardGap))
                    if (action == PendingAction.BIND) {
                        val iAmOwner = current.createdBy == session.userId
                        val myConfirmed = if (iAmOwner) current.ownerConfirmedAt != null else current.partnerConfirmedAt != null
                        val theirConfirmed = if (iAmOwner) current.partnerConfirmedAt != null else current.ownerConfirmedAt != null
                        ConfirmCard(
                            myConfirmed = myConfirmed,
                            theirConfirmed = theirConfirmed,
                            enabled = !readOnly,
                            onConfirmSelf = { vm.confirmBindAsOwner() }
                        )
                    } else {
                        val iAmRequester = current.unbindRequestedBy == session.userId
                        if (iAmRequester) {
                            UnbindWaitingCard(onCancel = { vm.cancelUnbind() })
                        } else {
                            UnbindIncomingCard(
                                onConfirm = { vm.confirmUnbind() },
                                onReject = { vm.rejectUnbind() }
                            )
                        }
                    }
                }

                BindState.BOUND -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    BoundCard(
                        partnerName = session.partnerNickname.ifBlank { "TA" },
                        onRequestUnbind = { showUnbindDialog = true }
                    )
                }

                BindState.FROZEN -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    FrozenCard(
                        onRebind = { vm.rebind() },
                        onExport = onExport
                    )
                }

                BindState.FAILED -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    FailedCard(
                        reason = bindFailure ?: "绑定未完成，请重新生成绑定码后让对方重新输入。",
                        onRegenerate = { vm.refreshBindCode() }
                    )
                }

                else -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    BindCodeInputCard(
                        value = bindCodeInput,
                        enabled = !readOnly,
                        onValueChange = { bindCodeInput = it },
                        onInputFocused = {
                            scope.launch {
                                delay(350)
                                runCatching { pageScroll.animateScrollTo(pageScroll.maxValue) }
                            }
                        },
                        onSubmit = {
                            vm.submitBindCode(bindCodeInput.trim())
                            bindCodeInput = ""
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showUnbindDialog) {
        AlertDialog(
            onDismissRequest = { showUnbindDialog = false },
            containerColor = S520JColors.Surface,
            title = { Text("发起解绑？") },
            text = { Text("解绑申请会发送给 TA，由 TA 确认或拒绝；TA 确认后空间进入冻结归档，共同内容只读保留，双方都可导出。") },
            confirmButton = {
                TextButton(onClick = {
                    showUnbindDialog = false
                    vm.requestUnbind()
                }) {
                    Text("发起解绑", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnbindDialog = false }) { Text("取消") }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = S520JColors.Surface,
            title = { Text("编辑空间资料") },
            text = {
                Column {
                    SoftTextField(
                        value = editName,
                        // 云端限制空间名称不超过 20 个字，这里同步截断，避免提交后才报错。
                        onValueChange = { editName = it.take(20) },
                        label = "空间名称",
                        placeholder = "例如：我们的小屋"
                    )
                    Spacer(Modifier.height(10.dp))
                    SoftTextField(
                        value = editIntro,
                        onValueChange = { editIntro = it },
                        label = "空间简介",
                        placeholder = "一句话写下这个空间的意义",
                        singleLine = false,
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        vm.updateSpaceInfo(editName, editIntro)
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text("保存", color = S520JColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SpaceRuleLine(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.Primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
    }
}

@Composable
private fun BindCodeCard(code: String, expiresAt: Long?, onRefresh: () -> Unit) {
    val expired = SpaceStateMachine.isBindCodeExpired(expiresAt, nowMillis())
    SoftCard(containerColor = S520JColors.PrimarySoft) {
        Text(
            text = "我的绑定码",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
        Text(
            text = code.ifBlank { "------" },
            style = MaterialTheme.typography.displayLarge,
            color = S520JColors.Primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = when {
                expiresAt == null -> "绑定码未设置有效期"
                expired -> "绑定码已过期，请刷新后让对方重新输入。"
                else -> "有效期至 ${expiresAt.toShortDate()}（生成后 24 小时内有效）"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (expired) MaterialTheme.colorScheme.error else S520JColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        PrimaryPillButton(
            text = "刷新绑定码",
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BindCodeInputCard(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onInputFocused: () -> Unit,
    onSubmit: () -> Unit
) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    SoftCard {
        Text(
            text = "输入对方绑定码",
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "对方在「共同空间」页生成的绑定码，生成后 24 小时内有效。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        SoftTextField(
            value = value,
            onValueChange = onValueChange,
            label = "绑定码",
            placeholder = "例如：A3F9K2",
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(requester)
                .onFocusChanged { state ->
                    // 键盘弹出动画期间可视区域仍在变化，分两次滚动确保输入框完整露出。
                    if (state.isFocused) {
                        onInputFocused()
                        scope.launch {
                            delay(200)
                            runCatching { requester.bringIntoView() }
                            delay(400)
                            runCatching { requester.bringIntoView() }
                        }
                    }
                }
        )
        Spacer(Modifier.height(10.dp))
        PrimaryPillButton(
            text = "提交绑定申请",
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && value.isNotBlank()
        )
    }
}

@Composable
private fun ConfirmCard(
    myConfirmed: Boolean,
    theirConfirmed: Boolean,
    enabled: Boolean,
    onConfirmSelf: () -> Unit
) {
    SoftCard {
        Text(
            text = "绑定确认",
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.Primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "绑定需要双方各确认一次，双方都确认后才会正式绑定。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        DetailRow("我方", if (myConfirmed) "已确认" else "待确认")
        DetailRow("对方", if (theirConfirmed) "已确认" else "待确认")
        Spacer(Modifier.height(12.dp))
        PrimaryPillButton(
            text = "我方确认",
            onClick = onConfirmSelf,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !myConfirmed
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "对方确认后会自动变为已绑定，可稍后重新进入本页查看。",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
    }
}

/** 解绑发起方：等待对方处理，可撤回申请。 */
@Composable
private fun UnbindWaitingCard(onCancel: () -> Unit) {
    SoftCard {
        Text(
            text = "解绑申请中",
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.Primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "已向 TA 发起解绑，等待对方确认。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "对方确认后空间进入冻结归档，共同内容只读保留，双方都可导出。",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("撤回解绑申请", color = S520JColors.Primary)
        }
    }
}

/** 解绑被申请方：确认后空间冻结，拒绝则保持已绑定。 */
@Composable
private fun UnbindIncomingCard(onConfirm: () -> Unit, onReject: () -> Unit) {
    SoftCard(containerColor = S520JColors.AccentPink) {
        Text(
            text = "解绑申请",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "TA 想解除共同空间绑定，确认后空间将冻结归档，共同内容只读保留。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            )
        ) {
            Text("确认解绑")
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) {
            Text("拒绝解绑", color = S520JColors.Primary)
        }
    }
}

@Composable
private fun BoundCard(partnerName: String, onRequestUnbind: () -> Unit) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已绑定",
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.Primary,
                modifier = Modifier.weight(1f)
            )
            TagPill("双方共享")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "你和 $partnerName 正在共同维护这个空间：共同内容双方可见，私人内容仍只属于自己。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onRequestUnbind, modifier = Modifier.fillMaxWidth()) {
            Text("发起解绑", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FrozenCard(onRebind: () -> Unit, onExport: () -> Unit) {
    SoftCard(containerColor = S520JColors.Sand) {
        Text(
            text = "空间已冻结归档",
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "解绑后空间只读：历史内容不会被删除，双方仍可查看与导出。重新绑定后恢复读写，历史内容保留。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        PrimaryPillButton(
            text = "重新绑定",
            onClick = onRebind,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text("导出归档内容", color = S520JColors.Primary)
        }
    }
}

@Composable
private fun BindFailureCard(reason: String, onClose: () -> Unit) {
    SoftCard(containerColor = S520JColors.AccentPink) {
        Text(
            text = "操作失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "失败只影响本次操作，可以关闭提示后重试。",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        PrimaryPillButton(
            text = "知道了",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FailedCard(reason: String, onRegenerate: () -> Unit) {
    SoftCard(containerColor = S520JColors.AccentPink) {
        Text(
            text = "绑定失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        PrimaryPillButton(
            text = "重新生成绑定码",
            onClick = onRegenerate,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun creatorLabel(createdBy: String, userId: String): String = when {
    userId.isBlank() -> "未知"
    createdBy == userId -> "我"
    else -> "对方"
}
