package com.s520j.app.ui.screens.us

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.data.local.BindState
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

@Composable
fun SpaceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val space by vm.space.collectAsState()
    val session by vm.session.collectAsState()

    var nameInput by rememberSaveable { mutableStateOf("") }
    var introInput by rememberSaveable { mutableStateOf("") }
    var bindCodeInput by rememberSaveable { mutableStateOf("") }
    var showUnbindDialog by remember { mutableStateOf(false) }

    val current = space
    val bindState = current?.bindState?.let { raw -> BindState.entries.firstOrNull { it.name == raw } }

    PageContainer {
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

        Spacer(Modifier.height(Dimens.SectionGap))

        if (current == null) {
            SectionHeader(title = "创建共同空间", subtitle = "创建后会生成 24 小时有效的绑定码")
            Spacer(Modifier.height(10.dp))
            SoftCard {
                SoftTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
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
                    text = "创建共同空间",
                    onClick = {
                        vm.createSpace(nameInput.trim(), introInput.trim())
                        nameInput = ""
                        introInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameInput.isNotBlank()
                )
            }
        } else {
            SectionHeader(title = "空间信息", subtitle = "账号不合并，内容按可见范围共享")
            Spacer(Modifier.height(10.dp))
            SoftCard {
                DetailRow("空间名称", current.name)
                DetailRow("空间简介", current.intro.ifBlank { "未填写" })
                DetailRow("绑定状态", bindState?.label ?: current.bindState)
                DetailRow("创建者", creatorLabel(current.createdBy, session.userId))
                DetailRow("创建时间", current.createdAt.toShortDate())
            }

            val bindCode = current.bindCode
            if (bindCode != null && bindState != BindState.FROZEN) {
                Spacer(Modifier.height(Dimens.CardGap))
                BindCodeCard(
                    code = bindCode,
                    expiresAt = current.bindCodeExpiresAt,
                    onRefresh = { vm.refreshBindCode() }
                )
            }

            when (bindState) {
                BindState.BOUND -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    BoundCard(
                        partnerName = session.partnerNickname.ifBlank { "TA" },
                        onRequestUnbind = { showUnbindDialog = true }
                    )
                }

                BindState.FROZEN -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    FrozenCard(onRebind = { vm.rebind() })
                }

                BindState.REQUEST_PENDING -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    PendingCard(
                        onConfirmBind = { vm.confirmBind() },
                        onConfirmUnbind = { vm.confirmUnbind() }
                    )
                }

                else -> {
                    Spacer(Modifier.height(Dimens.CardGap))
                    BindCodeInputCard(
                        value = bindCodeInput,
                        onValueChange = { bindCodeInput = it },
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
            text = { Text("解绑后空间进入冻结归档：共同内容只读保留，双方都可导出；重新绑定后可解冻恢复。") },
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
    val expired = expiresAt != null && nowMillis() > expiresAt
    SoftCard(containerColor = S520JColors.PrimarySoft) {
        Text(
            text = "我的绑定码",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
        Text(
            text = code,
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

@Composable
private fun BindCodeInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
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
            placeholder = "例如：A3F9K2"
        )
        Spacer(Modifier.height(10.dp))
        PrimaryPillButton(
            text = "提交绑定申请",
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = value.isNotBlank()
        )
    }
}

@Composable
private fun PendingCard(onConfirmBind: () -> Unit, onConfirmUnbind: () -> Unit) {
    SoftCard {
        Text(
            text = "等待确认",
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.Primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "绑定需要双方各确认一次。如果这次是对方发起的解绑申请，则确认后空间进入冻结归档。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        PrimaryPillButton(
            text = "确认绑定",
            onClick = onConfirmBind,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = onConfirmUnbind, modifier = Modifier.fillMaxWidth()) {
            Text("确认解绑", color = MaterialTheme.colorScheme.error)
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
private fun FrozenCard(onRebind: () -> Unit) {
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
    }
}

private fun creatorLabel(createdBy: String, userId: String): String = when {
    userId.isBlank() -> "未知"
    createdBy == userId -> "我"
    else -> "对方"
}
