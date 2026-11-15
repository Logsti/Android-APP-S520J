package com.s520j.app.ui.screens.us

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.s520j.app.data.local.BindState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.s520j.app.data.sync.SyncPhase
import com.s520j.app.data.sync.SyncState
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.PageContainer
import com.s520j.app.ui.components.SoftCard
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

@Composable
fun UsScreen(
    vm: AppViewModel,
    onOpenSpace: () -> Unit,
    onOpenAccountSecurity: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenPreferenceSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRecycle: () -> Unit,
    onLogout: () -> Unit
) {
    val session by vm.session.collectAsState()
    val space by vm.space.collectAsState()
    val unread by vm.unreadCount.collectAsState()
    val defaultVisibility by vm.defaultVisibility.collectAsState()
    val reminder by vm.reminder.collectAsState()
    val myAvatar by vm.myAvatar.collectAsState()
    val partnerAvatar by vm.partnerAvatar.collectAsState()
    val isAvatarUploading by vm.isAvatarUploading.collectAsState()
    val syncState by vm.syncState.collectAsState()
    val pendingSyncCount by vm.pendingSyncCount.collectAsState()
    val syncing = syncState.phase == SyncPhase.SYNCING

    // 进入页面时补一次同步（内部受 20 秒节流保护）。
    LaunchedEffect(Unit) { vm.syncOnEnter() }

    val bindState = space?.bindState?.let { raw -> BindState.entries.firstOrNull { it.name == raw } } ?: BindState.UNBOUND
    val spaceName = space?.name?.trim()?.takeIf { it.isNotBlank() }

    val versionName = LocalContext.current.packageManager
        .getPackageInfo(LocalContext.current.packageName, 0)
        .versionName ?: "未知"

    val context = LocalContext.current
    val myPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        vm.setMyAvatar(uri.toString())
    }

    PageContainer(
        onRefresh = vm::syncNow,
        refreshing = syncState.phase == SyncPhase.SYNCING
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "我们",
                    style = MaterialTheme.typography.displayLarge,
                    color = S520JColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "愿每一个平凡的日子，都有彼此相伴的温柔。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
            }
            NotificationBell(unread = unread, onClick = onOpenNotifications)
        }

        Spacer(Modifier.height(16.dp))

        // 情侣资料卡：渐变背景 + 两头像中间夹心 + 昵称各自在头像正下方
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(
                    Brush.linearGradient(
                        listOf(S520JColors.PrimarySoft, S520JColors.Sand)
                    )
                )
                .padding(vertical = 24.dp, horizontal = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileColumn(
                        letter = session.nickname.take(1).ifBlank { "你" },
                        uri = myAvatar,
                        nickname = session.nickname.ifBlank { "你" },
                        enabled = !isAvatarUploading,
                        onClick = {
                            myPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(22.dp)
                    )
                    ProfileColumn(
                        letter = session.partnerNickname.take(1).ifBlank { "TA" },
                        uri = partnerAvatar,
                        nickname = session.partnerNickname.ifBlank { "TA" },
                        editable = false,
                        onClick = {}
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = when {
                        !spaceName.isNullOrBlank() && (bindState == BindState.BOUND || bindState == BindState.FROZEN) ->
                            "共同空间 · $spaceName"
                        bindState == BindState.BOUND || bindState == BindState.FROZEN -> "共同空间"
                        else -> "未绑定共同空间"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "点击你的头像可上传照片",
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(Modifier.height(Dimens.SectionGap))

        SoftCard {
            SettingRow(
                icon = Icons.Filled.Home,
                title = "共同空间",
                subtitle = space?.name?.ifBlank { null } ?: "未创建",
                onClick = onOpenSpace
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Filled.Lock,
                title = "账号与安全",
                subtitle = null,
                onClick = onOpenAccountSecurity
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Outlined.Visibility,
                title = "隐私与权限",
                subtitle = "默认：${defaultVisibility.shortLabel}",
                onClick = onOpenPrivacy
            )
            SettingDivider()
            // 云同步：状态并入设置行，行尾为与其它行箭头等宽的 24dp 图标。
            SettingRow(
                icon = Icons.Outlined.Cloud,
                title = "云同步",
                subtitle = syncSubtitle(syncState, pendingSyncCount),
                onClick = { if (!syncing) vm.syncNow() },
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !syncing) { vm.syncNow() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                color = S520JColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "立即同步",
                                tint = S520JColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Filled.List,
                title = "数据管理",
                subtitle = "备份 · 导出",
                onClick = onOpenDataManagement
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Outlined.Notifications,
                title = "提醒设置",
                subtitle = if (reminder.enabledCount > 0) "${reminder.enabledCount} 项开启" else "未开启",
                onClick = onOpenReminderSettings
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Filled.Settings,
                title = "应用设置",
                subtitle = null,
                onClick = onOpenAppSettings
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Filled.Favorite,
                title = "个性化偏好",
                subtitle = null,
                onClick = onOpenPreferenceSettings
            )
            SettingDivider()
            SettingRow(
                icon = Icons.Outlined.Info,
                title = "关于 S520J",
                subtitle = null,
                badge = versionName,
                onClick = onOpenAbout
            )
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("退出登录", color = S520JColors.TextSecondary)
        }

        Spacer(Modifier.height(40.dp))
    }
}

/** 云同步行副标题：同步中 → 进行态文案；否则「上次同步 X · 待上传 N 条」；无记录 → 尚未同步。 */
private fun syncSubtitle(syncState: SyncState, pending: Int): String = when {
    syncState.phase == SyncPhase.SYNCING -> "同步中…"
    syncState.lastSyncAt <= 0L -> "尚未同步"
    else -> "上次同步 ${relativeSyncTime(syncState.lastSyncAt)} · 待上传 $pending 条"
}

/** 相对时间：<1 分钟「刚刚」、<1 小时「X 分钟前」、其余「X 小时前」。 */
private fun relativeSyncTime(lastSyncAt: Long): String {
    if (lastSyncAt <= 0L) return "尚未同步"
    val diff = System.currentTimeMillis() - lastSyncAt
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        else -> "${diff / 3_600_000L} 小时前"
    }
}

@Composable
private fun NotificationBell(unread: Int, onClick: () -> Unit) {
    // 外层不裁剪，保证右上角红点能完整露出
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(S520JColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = "通知",
                tint = S520JColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE55757)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 9) "9+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 头像 + 正下方昵称，editable=true 时可点击上传照片。 */
@Composable
private fun ProfileColumn(
    letter: String,
    uri: String?,
    nickname: String,
    enabled: Boolean = true,
    editable: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .then(if (editable && enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(S520JColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                AvatarImage(model = uri, letter = letter)
            }
            if (editable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (enabled) S520JColors.Primary else S520JColors.TextSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = "上传头像",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = nickname,
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 头像显示：云端同步过来的是 `data:image/jpeg;base64,...`，Coil 对 data URL 支持不稳定，
 * 这里自行解码成 Bitmap；其余（本地 content:// / 文件路径）仍交给 Coil。
 */
@Composable
private fun AvatarImage(model: String?, letter: String) {
    if (model.isNullOrBlank()) {
        AvatarLetter(letter)
        return
    }
    if (model.startsWith("data:image")) {
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, model) {
            value = withContext(Dispatchers.Default) { decodeDataUrl(model) }
        }
        val bmp = bitmap
        if (bmp == null) {
            AvatarLetter(letter)
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

@Composable
private fun AvatarLetter(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.headlineSmall,
        color = S520JColors.Primary,
        fontWeight = FontWeight.Bold
    )
}

private fun decodeDataUrl(dataUrl: String): android.graphics.Bitmap? = runCatching {
    val comma = dataUrl.indexOf(',')
    if (comma < 0) return@runCatching null
    val bytes = android.util.Base64.decode(dataUrl.substring(comma + 1), android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    badge: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    // 标题在左，附加信息右对齐紧贴箭头，与标题同一行居中；trailing 非空时替代右侧箭头。
    val hasTrailing = trailing != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = S520JColors.Primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = S520JColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (hasTrailing) Modifier else Modifier.weight(1f)
        )
        if (!badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(S520JColors.Sand)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
        } else if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.TextSecondary,
                maxLines = if (hasTrailing) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (hasTrailing) TextAlign.End else TextAlign.Start,
                // 有 trailing 时副标题占据剩余空间并可折行，避免挤压标题。
                modifier = if (hasTrailing) {
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                } else {
                    Modifier
                }
            )
            if (!hasTrailing) Spacer(Modifier.width(4.dp))
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = S520JColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(S520JColors.Divider.copy(alpha = 0.6f))
    )
}
