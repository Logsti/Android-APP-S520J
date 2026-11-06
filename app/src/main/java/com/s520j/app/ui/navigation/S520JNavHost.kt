package com.s520j.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.s520j.app.data.model.ContentModule
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.screens.gallery.GalleryScreen
import com.s520j.app.ui.screens.heart.HeartScreen
import com.s520j.app.ui.screens.module.ModuleEditScreen
import com.s520j.app.ui.screens.module.ModuleListScreen
import com.s520j.app.ui.screens.tacit.TacitScreen
import com.s520j.app.ui.screens.us.AboutScreen
import com.s520j.app.ui.screens.us.AccountSecurityScreen
import com.s520j.app.ui.screens.us.AppSettingsScreen
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
private const val RouteModuleEdit = "module/{module}/edit?recordId={recordId}"

@Composable
fun MainShell(vm: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val mainRoutes = remember { MainTabs.map { it.route }.toSet() }
    val isReadOnly by vm.isReadOnly.collectAsState()

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        containerColor = S520JColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
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
                                navController.navigate("module/${module.name}/edit")
                            } else {
                                navController.navigate("module/${module.name}")
                            }
                        },
                        onOpenNotifications = { navController.navigate("notifications") }
                    )
                }

                composable("gallery") {
                    GalleryScreen(
                        vm = vm,
                        onOpenModule = { module -> navController.navigate("module/${module.name}") }
                    )
                }

                composable("tacit") {
                    TacitScreen(
                        vm = vm,
                        onOpenModule = { module -> navController.navigate("module/${module.name}") }
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
                    ModuleListScreen(
                        vm = vm,
                        module = module,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate("module/${module.name}/edit?recordId=${id ?: ""}") }
                    )
                }

                composable(
                    route = RouteModuleEdit,
                    arguments = listOf(
                        navArgument("module") { type = NavType.StringType },
                        navArgument("recordId") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    val module = ContentModule.from(backStackEntry.arguments?.getString("module").orEmpty())
                    val recordId = backStackEntry.arguments
                        ?.getString("recordId")
                        ?.takeIf { it.isNotBlank() }
                    ModuleEditScreen(
                        vm = vm,
                        module = module,
                        recordId = recordId,
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
                    ReminderSettingsScreen(onBack = { navController.popBackStack() })
                }

                composable("settings/appearance") {
                    AppSettingsScreen(vm = vm, onBack = { navController.popBackStack() })
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
