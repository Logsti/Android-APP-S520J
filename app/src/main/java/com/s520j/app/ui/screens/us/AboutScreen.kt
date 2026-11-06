package com.s520j.app.ui.screens.us

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.components.BackBar
import com.s520j.app.ui.components.DetailRow
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SectionHeader
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.components.TagPill
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

@Suppress("DEPRECATION")
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?.ifBlank { "未知" }
            ?: "未知"
    }

    PageContainer {
        BackBar(title = "关于 S520J", onBack = onBack)
        Spacer(Modifier.height(10.dp))

        Text(
            text = "S520J",
            style = MaterialTheme.typography.displayLarge,
            color = S520JColors.Primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "两个独立的人，一段共同的日子。",
            style = MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )

        Spacer(Modifier.height(22.dp))

        SectionHeader(title = "应用信息", subtitle = "本页为只读信息")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            DetailRow("应用名称", "S520J")
            DetailRow("版本号", versionName)
            DetailRow("更新日期", "2026 年 11 月 6 日")
            DetailRow("作者", "LKS")
            Spacer(Modifier.height(10.dp))
            TagPill("阶段一 · 本地 Mock")
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "应用定位")
        Spacer(Modifier.height(10.dp))
        SoftCard(containerColor = S520JColors.Sand) {
            Text(
                text = "给两个人用的共同生活记录本：账号各自独立，共同内容一起维护，私人内容只属于自己。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SectionHeader(title = "帮助与反馈", subtitle = "只读说明")
        Spacer(Modifier.height(10.dp))
        SoftCard {
            DetailRow("反馈邮箱", "song971222@163.com")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "写信时请附上版本号、出现问题时的操作步骤与截图，方便定位问题。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "阶段一为本地 Mock 版本，账号与内容保存在本机；接入云端后会补全跨设备同步与导出能力。",
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
