package com.tool.decluttr.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tool.decluttr.presentation.screens.auth.AuthScreen
import com.tool.decluttr.presentation.screens.dashboard.DashboardScreen
import com.tool.decluttr.presentation.screens.settings.SettingsViewModel

object NavRoutes {
    const val AUTH = "auth"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}

@Composable
fun DecluttrNavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.AUTH
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.AUTH) {
            val viewModel: com.tool.decluttr.presentation.screens.auth.AuthViewModel = hiltViewModel()
            val isLoginMode by viewModel.isLoginMode.collectAsState()
            
            // Wait until we have user logged in from firebase auth state listener
            val authRepository = (navController.context as? android.app.Activity)?.applicationContext?.let {
                // Actually we can observe auth state directly if needed, or we just rely on navigation.
                // In AuthScreen, once signed in, we need to navigate.
            }
            
            // To simplify, we'll observe auth state in a LaunchedEffect
            val authViewModel: com.tool.decluttr.presentation.screens.settings.SettingsViewModel = hiltViewModel()
            val isLoggedIn by authViewModel.isLoggedIn.collectAsState(initial = false)
            
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            AuthScreen(
                viewModel = viewModel,
                onSkip = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.DASHBOARD) {
            val settingsViewModel: com.tool.decluttr.presentation.screens.settings.SettingsViewModel = hiltViewModel()
            val isLoggedIn by settingsViewModel.isLoggedIn.collectAsState(initial = false)

            DashboardScreen(
                onNavigateToSettings = { 
                    navController.navigate(NavRoutes.SETTINGS) 
                }
            )
        }
        
        composable(NavRoutes.SETTINGS) {
            val settingsViewModel: com.tool.decluttr.presentation.screens.settings.SettingsViewModel = hiltViewModel()
            val isLoggedIn by settingsViewModel.isLoggedIn.collectAsState(initial = false)

            com.tool.decluttr.presentation.screens.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = settingsViewModel
            )
        }
    }
}
