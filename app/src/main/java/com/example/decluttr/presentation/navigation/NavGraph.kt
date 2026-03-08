package com.example.decluttr.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.decluttr.presentation.screens.dashboard.DashboardScreen

object NavRoutes {
    const val DASHBOARD = "dashboard"
    const val APP_DETAILS = "app_details"
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
                },
                onNavigateToAppDetails = { packageId -> 
                    navController.navigate("${NavRoutes.APP_DETAILS}/$packageId") 
                }
            )
        }
        
        composable(
            route = "${NavRoutes.APP_DETAILS}/{packageId}",
            arguments = listOf(androidx.navigation.navArgument("packageId") { 
                type = androidx.navigation.NavType.StringType 
            })
        ) {
            com.example.decluttr.presentation.screens.appdetails.AppDetailsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(NavRoutes.SETTINGS) {
            com.example.decluttr.presentation.screens.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
