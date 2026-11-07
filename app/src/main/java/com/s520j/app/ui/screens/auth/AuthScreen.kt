package com.s520j.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.PrimaryPillButton
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

private enum class AuthTab(val label: String) { REGISTER("注册"), LOGIN("登录"), RESTORE("换机恢复") }

@Composable
fun AuthScreen(vm: AppViewModel) {
    var tab by remember { mutableStateOf(AuthTab.REGISTER) }
    var nickname by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { message -> snackbarHostState.showSnackbar(message.text) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(S520JColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.PageMaxWidth)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.PageHorizontalPadding)
        ) {
            Spacer(Modifier.height(48.dp))
            Text("S520J", style = MaterialTheme.typography.displayLarge, color = S520JColors.Primary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "两个独立的人，一段共同的日子。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthTab.entries.forEach { item ->
                    val selected = tab == item
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) S520JColors.Primary else S520JColors.Surface)
                            .clickable { tab = item }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else S520JColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SoftCard {
                when (tab) {
                    AuthTab.REGISTER -> {
                        Text(
                            text = "一键注册",
                            style = MaterialTheme.typography.titleLarge,
                            color = S520JColors.TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "账号 ID 与密码都由你自己设置。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = S520JColors.TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        SoftTextField(value = nickname, onValueChange = { nickname = it }, label = "昵称")
                        Spacer(Modifier.height(10.dp))
                        SoftTextField(
                            value = accountId,
                            onValueChange = { accountId = it },
                            label = "账号 ID（8–20 位字母数字）",
                            placeholder = "自己设置，方便记住"
                        )
                        Spacer(Modifier.height(10.dp))
                        SoftTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "设置密码（8–32 位字母数字）",
                            placeholder = "区分大小写"
                        )
                        Spacer(Modifier.height(10.dp))
                        SoftTextField(
                            value = passwordConfirm,
                            onValueChange = { passwordConfirm = it },
                            label = "确认密码"
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryPillButton(
                            text = "注册并进入",
                            onClick = {
                                if (password != passwordConfirm) {
                                    vm.notify("两次输入的密码不一致")
                                } else {
                                    vm.register(accountId, nickname, password)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "请自行记牢密码：APP 内不提供查看明文入口，也无法自助找回。",
                            style = MaterialTheme.typography.labelSmall,
                            color = S520JColors.TextSecondary
                        )
                    }

                    AuthTab.LOGIN -> {
                        Text(
                            text = "账号 ID + 密码登录",
                            style = MaterialTheme.typography.titleLarge,
                            color = S520JColors.TextPrimary
                        )
                        Spacer(Modifier.height(12.dp))
                        SoftTextField(value = accountId, onValueChange = { accountId = it }, label = "账号 ID")
                        Spacer(Modifier.height(10.dp))
                        SoftTextField(value = password, onValueChange = { password = it }, label = "密码")
                        Spacer(Modifier.height(14.dp))
                        PrimaryPillButton(
                            text = "登录",
                            onClick = { vm.login(accountId, password) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AuthTab.RESTORE -> {
                        Text(
                            text = "换机恢复",
                            style = MaterialTheme.typography.titleLarge,
                            color = S520JColors.TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "在新设备输入账号 ID 与密码，即可恢复到这台设备。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = S520JColors.TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        SoftTextField(value = accountId, onValueChange = { accountId = it }, label = "账号 ID")
                        Spacer(Modifier.height(10.dp))
                        SoftTextField(value = password, onValueChange = { password = it }, label = "密码")
                        Spacer(Modifier.height(14.dp))
                        PrimaryPillButton(
                            text = "开始恢复",
                            onClick = { vm.login(accountId, password) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "忘记密码且没有已登录设备时，无法自助找回。",
                            style = MaterialTheme.typography.labelSmall,
                            color = S520JColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "本阶段使用本地 Mock 数据：账号、空间与内容先落本地库，阶段 8 起接入云端同步。",
                style = MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
            Spacer(Modifier.height(40.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Dimens.PageHorizontalPadding)
        )
    }
}
