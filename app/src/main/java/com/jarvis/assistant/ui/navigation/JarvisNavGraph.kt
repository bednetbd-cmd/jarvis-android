package com.jarvis.assistant.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.AppContainer
import com.jarvis.assistant.core.vision.VisionScanMode
import com.jarvis.assistant.ui.screens.BiometricLockOverlay
import com.jarvis.assistant.ui.screens.MainScreen
import com.jarvis.assistant.ui.screens.MainViewModel
import com.jarvis.assistant.ui.screens.MemoryScreen
import com.jarvis.assistant.ui.screens.SettingsScreen
import com.jarvis.assistant.ui.screens.ToolsScreen
import com.jarvis.assistant.ui.screens.VisionScreen
import com.jarvis.assistant.ui.screens.WorkbenchScreen
import com.jarvis.assistant.ui.screens.WorkbenchTab
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

object JarvisRoute {
    const val MAIN = "main"
    const val TOOLS = "tools"
    const val MEMORY = "memory"
    const val SETTINGS = "settings"
    const val VISION = "vision"
    const val WORKBENCH = "workbench"
}

private data class HudNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val hudDestinations = listOf(
    HudNavDestination(JarvisRoute.MAIN, "JARVIS", Icons.Rounded.ChatBubble),
    HudNavDestination(JarvisRoute.TOOLS, "Tools", Icons.Rounded.Build),
    HudNavDestination(JarvisRoute.MEMORY, "Memory", Icons.Rounded.History),
    HudNavDestination(JarvisRoute.SETTINGS, "Settings", Icons.Rounded.Settings)
)

@Composable
fun JarvisNavGraph(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: JarvisRoute.MAIN
    val accent = MaterialTheme.colorScheme.primary
    var pendingVisionMode by remember { mutableStateOf(VisionScanMode.OCR) }
    var pendingWorkbenchTab by remember { mutableStateOf(WorkbenchTab.CALCULATOR) }

    if (uiState.isAppLocked) {
        BiometricLockOverlay(
            biometricAuth = mainViewModel.biometricAuth,
            capability = uiState.biometricCapability,
            onUnlocked = mainViewModel::unlockApp,
            modifier = modifier
        )
        return
    }

    val pendingSensitive = uiState.pendingSensitiveAction
    if (pendingSensitive != null) {
        AlertDialog(
            onDismissRequest = mainViewModel::dismissPendingSensitiveAction,
            containerColor = JarvisSurface,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = accent
                )
            },
            title = {
                Text(
                    text = pendingSensitive.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisTextPrimary
                )
            },
            text = {
                Text(
                    text = pendingSensitive.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = mainViewModel::confirmPendingSensitiveAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = JarvisBackground
                    )
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = mainViewModel::dismissPendingSensitiveAction,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openVision(mode: VisionScanMode) {
        pendingVisionMode = mode
        navController.navigate(JarvisRoute.VISION) {
            launchSingleTop = true
        }
    }

    fun openWorkbench(tab: WorkbenchTab) {
        pendingWorkbenchTab = tab
        navController.navigate(JarvisRoute.WORKBENCH) {
            launchSingleTop = true
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = JarvisBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute != JarvisRoute.VISION && currentRoute != JarvisRoute.WORKBENCH) {
                NavigationBar(
                    containerColor = JarvisSurface
                ) {
                    hudDestinations.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(item.route) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(text = item.label)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                indicatorColor = JarvisSurfaceVariant,
                                unselectedIconColor = JarvisTextMuted,
                                unselectedTextColor = JarvisTextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = JarvisRoute.MAIN,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = JarvisRoute.MAIN) {
                MainScreen(
                    viewModel = mainViewModel,
                    onNavigateToSettings = { navigateToTab(JarvisRoute.SETTINGS) },
                    onOpenVision = ::openVision,
                    onOpenWorkbench = ::openWorkbench
                )
            }
            composable(route = JarvisRoute.TOOLS) {
                ToolsScreen(
                    onRunCommand = { command ->
                        mainViewModel.sendMessage(overrideText = command)
                        navigateToTab(JarvisRoute.MAIN)
                    },
                    onOpenVision = ::openVision,
                    onOpenWorkbench = ::openWorkbench
                )
            }
            composable(route = JarvisRoute.MEMORY) {
                MemoryScreen(
                    viewModel = mainViewModel,
                    onRunCommand = { command ->
                        mainViewModel.sendMessage(overrideText = command)
                        navigateToTab(JarvisRoute.MAIN)
                    }
                )
            }
            composable(route = JarvisRoute.SETTINGS) {
                SettingsScreen(viewModel = mainViewModel)
            }
            composable(route = JarvisRoute.VISION) {
                VisionScreen(
                    viewModel = mainViewModel,
                    initialMode = pendingVisionMode,
                    onNavigateBack = { navController.popBackStack() },
                    onRunChatCommand = { command ->
                        mainViewModel.sendMessage(overrideText = command)
                        navigateToTab(JarvisRoute.MAIN)
                    }
                )
            }
            composable(route = JarvisRoute.WORKBENCH) {
                WorkbenchScreen(
                    viewModel = mainViewModel,
                    initialTab = pendingWorkbenchTab,
                    onNavigateBack = { navController.popBackStack() },
                    onRunChatCommand = { command ->
                        mainViewModel.sendMessage(overrideText = command)
                        navigateToTab(JarvisRoute.MAIN)
                    }
                )
            }
        }
    }
}
