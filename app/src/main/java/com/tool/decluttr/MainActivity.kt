package com.tool.decluttr

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.tool.decluttr.presentation.screens.dashboard.DashboardViewModel
import com.tool.decluttr.presentation.util.AppReviewManager
import com.tool.decluttr.presentation.util.ThemePreferences
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        DecluttrApp.appendStartupLog(this, "MainActivity onCreate start")
        try {
            val currentUser = if (FirebaseApp.getApps(this).isNotEmpty()) {
                runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            } else {
                null
            }
            val isNewUser = currentUser == null

            val splashScreen = installSplashScreen()
            DecluttrApp.appendStartupLog(this, "Splash screen installed")
            
            if (!isNewUser) {
                splashScreen.setKeepOnScreenCondition {
                    !dashboardViewModel.isStartupReady.value
                }
            }

            super.onCreate(savedInstanceState)
            DecluttrApp.appendStartupLog(this, "super.onCreate complete")
            enableEdgeToEdge()
            setContentView(R.layout.activity_main)
            DecluttrApp.appendStartupLog(this, "Content view set")

            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            
            val startDestination = if (currentUser != null) {
                R.id.dashboardFragment
            } else {
                R.id.authFragment
            }
            graph.setStartDestination(startDestination)
            navController.graph = graph
            
            // Check app launch count and potentially show Play Store rating panel
            AppReviewManager.checkAndShowReview(this)
            
            DecluttrApp.appendStartupLog(this, "Navigation graph ready with startDestination=$startDestination")
        } catch (throwable: Throwable) {
            DecluttrApp.appendStartupLog(this, "MainActivity onCreate failed", throwable)
            DecluttrApp.recordExceptionIfAvailable(this, throwable)
            throw throwable
        }
    }
}
