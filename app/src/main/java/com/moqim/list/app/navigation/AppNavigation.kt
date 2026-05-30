package com.moqim.list.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.moqim.list.feature.home.TodayScreen
import com.moqim.list.feature.plans.PlansScreen
import com.moqim.list.feature.settings.SettingsScreen
import com.moqim.list.feature.settings.SettingsViewModel
import com.moqim.list.feature.surface.SurfaceScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context),
    )
    val settingsState by settingsViewModel.uiState.collectAsState()

    val startDestination = when (settingsState.defaultHomeTabRoute) {
        AppDestination.Plans.route -> AppDestination.Plans.route
        AppDestination.Surface.route -> AppDestination.Surface.route
        AppDestination.Settings.route -> AppDestination.Settings.route
        else -> AppDestination.Today.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        route = "root_${settingsState.defaultHomeTabRoute}",
    ) {
        composable(AppDestination.Today.route) {
            TodayScreen()
        }
        composable(AppDestination.Plans.route) {
            PlansScreen()
        }
        composable(AppDestination.Surface.route) {
            SurfaceScreen()
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen()
        }
    }
}
