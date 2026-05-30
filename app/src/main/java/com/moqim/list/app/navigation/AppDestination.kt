package com.moqim.list.app.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
) {
    data object Today : AppDestination("today", "今日")
    data object Plans : AppDestination("plans", "计划")
    data object Surface : AppDestination("surface", "展示")
    data object Settings : AppDestination("settings", "设置")
}

val bottomBarDestinations = listOf(
    AppDestination.Today,
    AppDestination.Plans,
    AppDestination.Surface,
    AppDestination.Settings,
)
