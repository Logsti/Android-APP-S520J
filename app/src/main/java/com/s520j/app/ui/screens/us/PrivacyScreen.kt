package com.s520j.app.ui.screens.us

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.Visibility
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

@Composable
fun PrivacyScreen(vm: AppViewModel, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Visibility.PRIVATE.name) }
    val selectedVisibility = Visibility.from(selected)

    PageContainer {
        BackBar(title = "隐私与权限", onBack = onBack)
        Spacer(Modifier.height(6.dp))

        SoftCard(containerColor = S520JColors.Sand) {
            Text(
                text = "可见范围只有两档",
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "S520J 不提供「指定授权」或「部分好友可见」，每条内容只可能是下面两种状态之一。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "默认可见范围", subtitle = "当前阶段仅作展示")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Visibility.entries.forEach { visibility ->
                VisibilityOption(
                    visibility = visibility,
                    selected = selected == visibility.name,
                    onSelect = { selected = visibility.name }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagPill("当前默认：${selectedVisibility.label}")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "新内容默认仅自己可见，可以在每条内容上单独改为共同可见；此处选择仅在本机记忆展示，不改动已有内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "照片与附件")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            PrivacyNote("照片和附件自动继承所属记录的可见范围，不单独设置权限。")
            PrivacyNote("记录改为共同可见时，其照片和附件一并变为共同可见。")
            PrivacyNote("解绑冻结期间，内容按原可见范围只读保留。")
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "权限校验")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            PrivacyNote("正式版的可见范围由服务端校验，前端只展示状态，且与服务端保持一致。")
            PrivacyNote("本阶段为本地 Mock：可见范围仅保存在本机，不会上传或共享。")
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun VisibilityOption(
    visibility: Visibility,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = S520JColors.Primary,
                unselectedColor = S520JColors.TextSecondary
            )
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = visibility.label,
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.TextPrimary
            )
            Text(
                text = visibilityDescription(visibility),
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }
    }
}

@Composable
private fun PrivacyNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
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

private fun visibilityDescription(visibility: Visibility): String = when (visibility) {
    Visibility.PRIVATE -> "只有你自己能看到：对方在列表、详情与导出中都看不到这条内容。"
    Visibility.COUPLE -> "绑定后双方都能看到、共同维护；解绑冻结期间按原范围只读保留。"
}
