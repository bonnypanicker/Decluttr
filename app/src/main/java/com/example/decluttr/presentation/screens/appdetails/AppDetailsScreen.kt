package com.example.decluttr.presentation.screens.appdetails

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppDetailsViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsState()
    val context = LocalContext.current

    var categoryText by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    LaunchedEffect(appState) {
        val app = appState
        if (app != null) {
            categoryText = app.category ?: ""
            tagsText = app.tags.joinToString(", ")
            notesText = app.notes ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appState?.name ?: "App Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val app = appState
        if (app == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (app.iconBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(96.dp)
                    )
                }
            } else {
                Box(modifier = Modifier.size(96.dp))
            }
            
            Spacer(modifier = Modifier.size(16.dp))
            Text(app.packageId, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.size(24.dp))
            
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageId}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageId}"))
                        context.startActivity(webIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Re-install from Play Store")
            }

            Spacer(modifier = Modifier.size(32.dp))

            OutlinedTextField(
                value = categoryText,
                onValueChange = { 
                    categoryText = it
                    viewModel.updateCategory(it)
                },
                label = { Text("Category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.size(16.dp))
            
            OutlinedTextField(
                value = tagsText,
                onValueChange = { 
                    tagsText = it
                    viewModel.updateTags(it)
                },
                label = { Text("Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.size(16.dp))
            
            OutlinedTextField(
                value = notesText,
                onValueChange = { 
                    notesText = it
                    viewModel.updateNotes(it)
                },
                label = { Text("Personal Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}
