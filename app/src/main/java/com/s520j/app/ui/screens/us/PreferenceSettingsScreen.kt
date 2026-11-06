package com.s520j.app.ui.screens.us

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.PrimaryPillButton
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.SoftTextField
import com.s520j.app.ui.theme.S520JColors

@Composable
fun PreferenceSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val greeting by vm.greeting.collectAsState()
    var greetingInput by rememberSaveable(greeting) { mutableStateOf(greeting) }

    PageContainer {
        BackBar(title = "个性化偏好", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        SectionHeader(title = "个性化偏好", subtitle = "首页问候语")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            SoftTextField(
                value = greetingInput,
                onValueChange = { greetingInput = it },
                label = "问候语",
                placeholder = "今天也要好好喜欢彼此。",
                singleLine = false,
                minLines = 2
            )
            Spacer(Modifier.height(12.dp))
            PrimaryPillButton(
                text = "保存问候语",
                onClick = { vm.setGreeting(greetingInput.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = greetingInput.isNotBlank() && greetingInput.trim() != greeting
            )
            Spacer(Modifier.height(6.dp))
            Text(text = "保存后立即生效。", style = MaterialTheme.typography.labelSmall, color = S520JColors.TextSecondary)
        }

        Spacer(Modifier.height(40.dp))
    }
}
