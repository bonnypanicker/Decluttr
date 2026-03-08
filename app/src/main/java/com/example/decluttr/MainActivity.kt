package com.example.decluttr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.decluttr.presentation.navigation.DecluttrNavGraph
import com.example.decluttr.ui.theme.DecluttrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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

