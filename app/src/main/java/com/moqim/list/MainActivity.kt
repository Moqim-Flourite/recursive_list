package com.moqim.list

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moqim.list.app.navigation.AppDestination
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import com.moqim.list.app.navigation.AppNavigation
import com.moqim.list.app.navigation.bottomBarDestinations
import com.moqim.list.ui.theme.MyApplicationTheme
import com.moqim.list.widget.CurrentSegmentWidgetProvider
import com.moqim.list.widget.MorningWidgetProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MorningWidgetProvider.refreshAll(this)
        CurrentSegmentWidgetProvider.refreshAll(this)
        WallpaperRefreshNotifier.notifyRefresh(this)
        enableEdgeToEdge()
        setContent {
            val settingsRepository = SettingsPreferencesRepository(applicationContext)
            val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = com.moqim.list.data.preferences.SettingsPreferences()
            )
            MyApplicationTheme(themeMode = settings.appTheme) {
                MoqimListApp()
            }
        }
    }
}

@Composable
fun MoqimListApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        )

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .blur(0.5.dp),
                    shape = RoundedCornerShape(30.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                    ) {
                        bottomBarDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    if (currentRoute != destination.route) {
                                        navController.navigate(destination.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                ),
                                icon = {
                                    Icon(
                                        imageVector = destination.toIcon(),
                                        contentDescription = destination.label,
                                    )
                                },
                                label = {
                                    Text(text = destination.label)
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            AppNavigation(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun AppDestination.toIcon() = when (this) {
    AppDestination.Today -> Icons.Outlined.Home
    AppDestination.Plans -> Icons.Outlined.DateRange
    AppDestination.Surface -> Icons.Outlined.Widgets
    AppDestination.Settings -> Icons.Outlined.Settings
}
