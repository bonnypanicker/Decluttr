package com.tool.decluttr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.tool.decluttr.presentation.screens.dashboard.DashboardViewModel
import com.tool.decluttr.presentation.navigation.DecluttrNavGraph
import com.tool.decluttr.presentation.navigation.NavRoutes
import com.tool.decluttr.ui.theme.DecluttrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()

    @Inject
    lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("DECLUTTR_CRASH", "FATAL EXCEPTION in thread ${thread.name}", throwable)
            // Let the default handler do its job (crash the app)
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !dashboardViewModel.isStartupReady.value
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DecluttrTheme {
                val navController = rememberNavController()
                val startDestination = if (auth.currentUser != null) {
                    NavRoutes.DASHBOARD
                } else {
                    NavRoutes.AUTH
                }
                DecluttrNavGraph(navController = navController, startDestination = startDestination)
            }
        }
    }
}

