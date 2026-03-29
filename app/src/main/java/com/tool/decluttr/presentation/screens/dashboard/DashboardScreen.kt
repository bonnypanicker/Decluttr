package com.tool.decluttr.presentation.screens.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.tool.decluttr.presentation.screens.appdetails.AppDetailsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val archivedApps by viewModel.archivedApps.collectAsState()
    val unusedApps by viewModel.unusedApps.collectAsState()
    val largeApps by viewModel.largeApps.collectAsState()
    val allInstalledApps by viewModel.allInstalledApps.collectAsState()
    val isLoadingDiscovery by viewModel.isLoadingDiscovery.collectAsState()
    val isPreparingAllApps by viewModel.isPreparingAllApps.collectAsState()

    var celebrationData by remember { mutableStateOf<DashboardViewModel.CelebrationData?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var reviewData by remember { mutableStateOf<DashboardViewModel.ReviewData?>(null) }

    LaunchedEffect(Unit) {
        viewModel.celebrationEvent.collect { data ->
            celebrationData = data
        }
    }

    LaunchedEffect(Unit) {
        viewModel.reviewEvent.collect { data ->
            reviewData = data
        }
    }

    // Show Native Bulk Review Dialog after archive+uninstall
    val context = LocalContext.current
    val activityContext = context.findActivity()
    val currentReviewData = reviewData
    if (currentReviewData != null && activityContext != null) {
        val archivedApps = archivedApps.filter { it.packageId in currentReviewData.archivedPackageIds }
        if (archivedApps.isNotEmpty()) {
            DisposableEffect(currentReviewData) {
                val dialog = try {
                    NativeBulkReviewDialog(
                        context = activityContext,
                        archivedApps = archivedApps,
                        onComplete = { notesMap ->
                            viewModel.saveReviewNotes(notesMap, currentReviewData.celebration)
                            reviewData = null
                        },
                        onCancel = {
                            // Skip review, still show celebration
                            viewModel.saveReviewNotes(emptyMap(), currentReviewData.celebration)
                            reviewData = null
                        }
                    ).also { it.show() }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardScreen", "Failed to show review dialog", e)
                    reviewData = null
                    null
                }
                onDispose {
                    try { dialog?.dismiss() } catch (_: Exception) {}
                }
            }
        } else {
            // Apps not yet in DB, show celebration directly
            LaunchedEffect(currentReviewData) {
                viewModel.saveReviewNotes(emptyMap(), currentReviewData.celebration)
                reviewData = null
            }
        }
    }

    val tabs = listOf("Discover", "My Archive")
    val tabIcons = listOf(Icons.Default.Search, Icons.Default.List)

    if (celebrationData != null) {
        Dialog(onDismissRequest = { celebrationData = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Great job!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val mbSaved = celebrationData!!.savedBytes / (1024 * 1024)
                    Text(
                        text = "${celebrationData!!.count} apps archived\n$mbSaved MB freed\nBackground processes stopped",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            celebrationData = null
                            selectedTabIndex = 1 // Switch to My Archive
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("See them in My Archive")
                    }
                }
            }
        }
    }

    var selectedAppId by remember { mutableStateOf<String?>(null) }

    if (selectedAppId != null) {
        val appToDelete = archivedApps.find { it.packageId == selectedAppId }
        AppDetailsDialog(
            packageId = selectedAppId!!,
            onDismissRequest = { selectedAppId = null },
            onDeleteClick = {
                appToDelete?.let { viewModel.deleteArchivedApp(it) }
                selectedAppId = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Decluttr", 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = { Icon(tabIcons[index], contentDescription = title) },
                        label = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    // Discovery Dashboard
                    val uninstallProgress by viewModel.uninstallProgress.collectAsState()
                    DiscoveryScreen(
                        unusedApps = unusedApps,
                        largeApps = largeApps,
                        allApps = allInstalledApps,
                        isLoading = isLoadingDiscovery,
                        isPreparingAllApps = isPreparingAllApps,
                        uninstallProgress = uninstallProgress,
                        onRefresh = { viewModel.loadDiscoveryDataIfStale() },
                        onPrepareAllApps = { viewModel.prepareAllAppsForDisplay() },
                        onPrefetchPackages = { packageIds -> viewModel.prefetchIcons(packageIds) },
                        onBatchUninstall = { packageIds ->
                            viewModel.archiveAndUninstallSelected(packageIds)
                        },
                        onBatchUninstallOnly = { packageIds ->
                            viewModel.uninstallSelectedOnly(packageIds)
                        },
                        hasUsagePermission = viewModel.hasUsagePermission.collectAsState().value,
                        onRequestPermission = { viewModel.checkUsagePermission() }
                    )
                }
                1 -> {
                    // Archive List
                    ArchivedAppsList(
                        apps = archivedApps,
                        onAppClick = { selectedAppId = it },
                        onDeleteClick = { viewModel.deleteArchivedApp(it) },
                        onAppUpdate = { viewModel.updateArchivedApp(it) },
                        onNavigateToDiscover = { selectedTabIndex = 0 }
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
