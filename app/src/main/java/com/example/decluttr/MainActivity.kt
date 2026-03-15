package com.example.decluttr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.example.decluttr.presentation.screens.dashboard.DashboardViewModel
import com.example.decluttr.presentation.navigation.DecluttrNavGraph
import com.example.decluttr.ui.theme.DecluttrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !dashboardViewModel.isStartupReady.value
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DecluttrTheme {
                val navController = rememberNavController()
                DecluttrNavGraph(navController = navController)
            }
        }
    }
}

