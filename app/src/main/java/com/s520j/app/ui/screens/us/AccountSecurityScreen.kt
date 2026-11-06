package com.s520j.app.ui.screens.us

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.s520j.app.data.repository.RecoveryCodeRule
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.DetailRow
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.S520JColors

@Composable
fun AccountSecurityScreen(vm: AppViewModel, onBack: () -> Unit) {
    val session by vm.session.collectAsState()
    var showChangePwdDialog by rememberSaveable { mutableStateOf(false) }

    PageContainer {
        BackBar(title = "账号与安全", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        SectionHeader(title = "账号与安全", subtitle = "账号信息与密码管理")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            DetailRow("当前账号 ID", session.accountId.ifBlank { "未登录" })
            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(session.loggedIn) { showChangePwdDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "修改密码",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (session.loggedIn) S520JColors.TextPrimary else S520JColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (session.loggedIn) "›" else "未登录",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (session.loggedIn) S520JColors.Primary else S520JColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showChangePwdDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePwdDialog = false },
            onConfirm = { old, new ->
                vm.changeRecoveryCode(old, new)
                showChangePwdDialog = false
            }
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(S520JColors.Divider.copy(alpha = 0.6f))
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit
) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun validateAndSubmit() {
        when {
            oldPwd.isEmpty() -> errorMsg = "请输入旧密码"
            newPwd.isEmpty() -> errorMsg = "请输入新密码"
            newPwd == oldPwd -> errorMsg = "新旧密码不能相同"
            confirmPwd != newPwd -> errorMsg = "两次新密码不一致"
            else -> RecoveryCodeRule.validate(newPwd)?.let { errorMsg = it }
                ?: run { onConfirm(oldPwd, newPwd) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                PasswordField(value = oldPwd, onValueChange = { oldPwd = it; errorMsg = null }, label = "旧密码")
                Spacer(Modifier.height(10.dp))
                PasswordField(value = newPwd, onValueChange = { newPwd = it; errorMsg = null }, label = "新密码")
                Spacer(Modifier.height(10.dp))
                PasswordField(value = confirmPwd, onValueChange = { confirmPwd = it; errorMsg = null }, label = "确认新密码")
                errorMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = S520JColors.Primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(text = "8-32 位字母和数字。", style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validateAndSubmit() },
                enabled = oldPwd.isNotBlank() && newPwd.isNotBlank() && confirmPwd.isNotBlank()
            ) { Text("确认", color = S520JColors.Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = S520JColors.TextSecondary) } },
        containerColor = S520JColors.Surface
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = S520JColors.Primary,
            unfocusedBorderColor = S520JColors.Divider,
            focusedLabelColor = S520JColors.Primary,
            unfocusedLabelColor = S520JColors.TextSecondary,
            focusedContainerColor = S520JColors.Surface,
            unfocusedContainerColor = S520JColors.Surface,
            cursorColor = S520JColors.Primary
        )
    )
}
