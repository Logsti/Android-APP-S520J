package com.s520j.app.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import com.s520j.app.data.couple.CoupleEvent
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.sync.SyncPolicy
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.components.SnackbarTiming
import com.s520j.app.ui.components.showTimedMessage
import com.s520j.app.ui.screens.gallery.DishPickerScreen
import com.s520j.app.ui.screens.gallery.GalleryScreen
import com.s520j.app.ui.screens.gallery.SharedPhotosScreen
import com.s520j.app.ui.screens.gallery.StarBoardScreen
import com.s520j.app.ui.screens.heart.HeartScreen
import com.s520j.app.ui.screens.module.ModuleEditScreen
import com.s520j.app.ui.screens.module.ModuleListScreen
import com.s520j.app.ui.screens.tacit.LetterListScreen
import com.s520j.app.ui.screens.tacit.TacitScreen
import com.s520j.app.ui.screens.us.AboutScreen
import com.s520j.app.ui.screens.us.AccountSecurityScreen
import com.s520j.app.ui.screens.us.AppSettingsScreen
import com.s520j.app.ui.screens.us.ConflictResolutionScreen
import com.s520j.app.ui.screens.us.DataManagementScreen
import com.s520j.app.ui.screens.us.NotificationCenterScreen
import com.s520j.app.ui.screens.us.PreferenceSettingsScreen
import com.s520j.app.ui.screens.us.PrivacyScreen
import com.s520j.app.ui.screens.us.RecycleBinScreen
import com.s520j.app.ui.screens.us.ReminderSettingsScreen
import com.s520j.app.ui.screens.us.SpaceScreen
import com.s520j.app.ui.screens.us.UsScreen
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors
import kotlinx.coroutines.flow.collect

private const val RouteHeart = "heart"
private const val RouteModule = "module/{module}"
private const val RouteModuleEdit = "module/{module}/edit?recordId={recordId}&preset={preset}"

@Composable
fun MainShell(vm: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val mainRoutes = remember { MainTabs.map { it.route }.toSet() }
    val isReadOnly by vm.isReadOnly.collectAsState()
    val syncState by vm.syncState.collectAsState()

    // 前后台自动同步（9.3）：前台每 20 秒一次，退到后台立即停止。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onAppForeground()
                Lifecycle.Event.ON_STOP -> vm.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val result = snackbarHostState.showTimedMessage(message)
            if (result == SnackbarResult.ActionPerformed) {
                message.targetModule?.let { module ->
                    navController.navigate("module/${module.name}")
                }
            }
        }
    }

    // 碰一碰 / 挑战达成横幅：展示 2 秒后自动消失，点击进入通知中心。
    val pokeBanner by vm.pokeBanner.collectAsState()
    LaunchedEffect(pokeBanner?.id) {
        if (pokeBanner != null) {
            kotlinx.coroutines.delay(SnackbarTiming.BANNER_MS)
            vm.dismissPokeBanner()
        }
    }

    // Android 13+ 通知权限：只询问一次，拒绝后仍有应用内横幅与未读记录。
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.markNotifyPermissionAsked() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            vm.shouldAskNotifyPermission()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        containerColor = S520JColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 固定高度的底栏占位：主页面与子页面之间切换时内容区高度不变，避免整体位移。
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Dimens.BottomBarHeight + 1.dp)
                ) {
                    if (currentRoute in mainRoutes) {
                        S520JBottomBar(currentRoute = currentRoute) { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            if (isReadOnly) {
                ReadOnlyNotice()
            }
            // 离线/失败时常驻一条可点击的状态条，避免同步异常被藏进设置页。
            SyncPolicy.statusBarText(syncState.phase)?.let { text ->
                SyncStatusBar(text = text, onClick = vm::syncNow)
            }
            pokeBanner?.let { event ->
                CoupleBanner(
                    event = event,
                    onDismiss = vm::dismissPokeBanner,
                    onClick = {
                        vm.dismissPokeBanner()
                        navController.navigate("notifications")
                    }
                )
            }
            NavHost(
                navController = navController,
                startDestination = RouteHeart,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(RouteHeart) {
                    HeartScreen(
                        vm = vm,
                        onOpenModule = { module, preset ->
                            if (preset != null) {
                                navController.navigate("module/${module.name}/edit?preset=${Uri.encode(preset)}")
                            } else {
                                navController.navigate("module/${module.name}")
                            }
                        },
                        onOpenEditor = { module ->
                            navController.navigate("module/${module.name}/edit")
                        },
                        onOpenRecord = { module, id ->
                            navController.navigate("module/${module.name}/edit?recordId=${Uri.encode(id)}")
                        }
                    )
                }

                composable("gallery") {
                    GalleryScreen(
                        vm = vm,
                        onOpenModule = { module -> navController.navigate("module/${module.name}") },
                        onOpenRecord = { module, id ->
                            navController.navigate("module/${module.name}/edit?recordId=${Uri.encode(id)}")
                        },
                        onCreateRecord = { module ->
                            navController.navigate("module/${module.name}/edit")
                        },
                        onOpenStarBoard = { navController.navigate("starboard") },
                        onOpenSharedPhotos = { navController.navigate("gallery/photos") },
                        onOpenDishPicker = { navController.navigate("gallery/food") }
                    )
                }

                composable("gallery/photos") {
                    SharedPhotosScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("gallery/food") {
                    DishPickerScreen(onBack = { navController.popBackStack() })
                }

                composable("starboard") {
                    StarBoardScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onOpenRecord = { module, id ->
                            navController.navigate("module/${module.name}/edit?recordId=${Uri.encode(id)}")
                        },
                        onCreateDish = { navController.navigate("module/${ContentModule.DISH.name}/edit") }
                    )
                }

                composable("tacit") {
                    TacitScreen(
                        vm = vm,
                        onOpenModule = { module -> navController.navigate("module/${module.name}") },
                        onOpenRecord = { module, id ->
                            navController.navigate("module/${module.name}/edit?recordId=${Uri.encode(id)}")
                        }
                    )
                }

                composable("us") {
                    UsScreen(
                        vm = vm,
                        onOpenSpace = { navController.navigate("space") },
                        onOpenAccountSecurity = { navController.navigate("settings/account") },
                        onOpenPrivacy = { navController.navigate("privacy") },
                        onOpenDataManagement = { navController.navigate("settings/data") },
                        onOpenReminderSettings = { navController.navigate("settings/reminder") },
                        onOpenAppSettings = { navController.navigate("settings/appearance") },
                        onOpenPreferenceSettings = { navController.navigate("settings/preference") },
                        onOpenNotifications = { navController.navigate("notifications") },
                        onOpenAbout = { navController.navigate("about") },
                        onOpenRecycle = { navController.navigate("recycle") },
                        onLogout = { vm.logout() }
                    )
                }

                composable(
                    route = RouteModule,
                    arguments = listOf(navArgument("module") { type = NavType.StringType })
                ) { backStackEntry ->
                    val module = ContentModule.from(backStackEntry.arguments?.getString("module").orEmpty())
                    if (module == ContentModule.LETTER) {
                        LetterListScreen(
                            vm = vm,
                            onBack = { navController.popBackStack() },
                            onEdit = { id ->
                                navController.navigate("module/${module.name}/edit?recordId=${Uri.encode(id ?: "")}")
                            }
                        )
                    } else {
                        ModuleListScreen(
                            vm = vm,
                            module = module,
                            onBack = { navController.popBackStack() },
                            onEdit = { id ->
                                navController.navigate("module/${module.name}/edit?recordId=${Uri.encode(id ?: "")}")
                            }
                        )
                    }
                }

                composable(
                    route = RouteModuleEdit,
                    arguments = listOf(
                        navArgument("module") { type = NavType.StringType },
                        navArgument("recordId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("preset") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val module = ContentModule.from(backStackEntry.arguments?.getString("module").orEmpty())
                    val recordId = backStackEntry.arguments
                        ?.getString("recordId")
                        ?.takeIf { it.isNotBlank() }
                    val preset = backStackEntry.arguments
                        ?.getString("preset")
                        ?.takeIf { it.isNotBlank() }
                    ModuleEditScreen(
                        vm = vm,
                        module = module,
                        recordId = recordId,
                        preset = preset,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("space") {
                    SpaceScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable("settings/account") {
                    AccountSecurityScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable("settings/reminder") {
                    ReminderSettingsScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable("settings/appearance") {
                    AppSettingsScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onOpenConflicts = { navController.navigate("sync/conflicts") }
                    )
                }

                composable("sync/conflicts") {
                    ConflictResolutionScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable("settings/preference") {
                    PreferenceSettingsScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable("settings/data") {
                    DataManagementScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onOpenRecycle = { navController.navigate("recycle") }
                    )
                }

                composable("privacy") {
                    PrivacyScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable("notifications") {
                    NotificationCenterScreen(vm = vm, onBack = { navController.popBackStack() })
                }

                composable("about") {
                    AboutScreen(onBack = { navController.popBackStack() })
                }

                composable("recycle") {
                    RecycleBinScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/** 碰一碰 / 挑战达成横幅：置顶醒目展示，可点击进入通知中心或手动关闭。 */
@Composable
private fun CoupleBanner(event: CoupleEvent, onDismiss: () -> Unit, onClick: () -> Unit) {
    val challengeDone = event.type == CoupleEvent.TYPE_CHALLENGE_DONE
    val title = if (challengeDone) "今日挑战达成" else "${event.fromName.ifBlank { "TA" }} 碰了碰你"
    val body = if (challengeDone) "你们俩今天都完成了，真好。"
    else event.body.ifBlank { "打开看看 TA 想说什么" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PageHorizontalPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(S520JColors.Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = S520JColors.PureWhite,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = S520JColors.PureWhite.copy(alpha = 0.92f)
            )
        }
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "关闭",
            tint = S520JColors.PureWhite.copy(alpha = 0.85f),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onDismiss)
                .padding(4.dp)
        )
    }
}

/** 同步状态条：离线/失败时常驻在内容区顶部，整行可点击立即重试。 */@Composable
private fun SyncStatusBar(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(S520JColors.Sand)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.PageHorizontalPadding, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
    }
}

@Composable
private fun ReadOnlyNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(S520JColors.Sand)
            .padding(horizontal = Dimens.PageHorizontalPadding, vertical = 8.dp)
    ) {
        Text(
            text = "空间已冻结归档 · 共同内容只读，可在「我们-数据管理」或共同空间页导出",
            style = MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
    }
}
