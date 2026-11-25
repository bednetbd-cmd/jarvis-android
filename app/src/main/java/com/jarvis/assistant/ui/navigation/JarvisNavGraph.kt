package com.jarvis.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.AppContainer
import com.jarvis.assistant.ui.screens.MainScreen
import com.jarvis.assistant.ui.screens.MainViewModel
import com.jarvis.assistant.ui.screens.SettingsScreen

object JarvisRoute {
    const val MAIN = "main"
    const val SETTINGS = "settings"
}

@Composable
fun JarvisNavGraph(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))

    NavHost(
        navController = navController,
        startDestination = JarvisRoute.MAIN,
        modifier = modifier
    ) {
        composable(route = JarvisRoute.MAIN) {
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = {
                    navController.navigate(JarvisRoute.SETTINGS)
                }
            )
        }
        composable(route = JarvisRoute.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
