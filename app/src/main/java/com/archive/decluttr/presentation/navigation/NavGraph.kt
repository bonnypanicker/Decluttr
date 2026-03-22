package com.archive.decluttr.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.archive.decluttr.presentation.screens.dashboard.DashboardScreen

object NavRoutes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}

@Composable
fun DecluttrNavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.DASHBOARD
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.DASHBOARD) {
            DashboardScreen(
                onNavigateToSettings = { 
                    navController.navigate(NavRoutes.SETTINGS) 
                }
            )
        }
        
        composable(NavRoutes.SETTINGS) {
            com.archive.decluttr.presentation.screens.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
