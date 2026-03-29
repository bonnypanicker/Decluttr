package com.tool.decluttr.presentation.screens.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel

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

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var reviewData by remember { mutableStateOf<DashboardViewModel.ReviewData?>(null) }

    LaunchedEffect(Unit) {
        viewModel.reviewEvent.collect { data ->
            reviewData = data
        }
    }

    // Show Native Bulk Review Dialog after archive+uninstall
    val context = LocalContext.current
    val activityContext = context.findActivity()
    val currentReviewData = reviewData
    
    LaunchedEffect(currentReviewData, activityContext) {
        if (currentReviewData != null) {
            android.util.Log.d("DECLUTTR_DEBUG", "ReviewData received! Apps count: ${currentReviewData.archivedApps.size}")
            android.util.Log.d("DECLUTTR_DEBUG", "ActivityContext valid? ${activityContext != null}")
        }
    }

    if (currentReviewData != null && activityContext != null && currentReviewData.archivedApps.isNotEmpty()) {
        DisposableEffect(currentReviewData) {
            android.util.Log.d("DECLUTTR_DEBUG", "Attempting to show NativeBulkReviewDialog...")
            val dialog = try {
                NativeBulkReviewDialog(
                    context = activityContext,
                    archivedApps = currentReviewData.archivedApps,
                    onComplete = { notesMap ->
                        android.util.Log.d("DECLUTTR_DEBUG", "Dialog onComplete called with ${notesMap.size} notes")
                        viewModel.saveReviewNotes(notesMap, currentReviewData.celebration)
                        reviewData = null
                        selectedTabIndex = 1
                    },
                    onCancel = {
                        android.util.Log.d("DECLUTTR_DEBUG", "Dialog onCancel called")
                        viewModel.saveReviewNotes(emptyMap(), currentReviewData.celebration)
                        reviewData = null
                        selectedTabIndex = 1
                    }
                ).also { 
                    it.show() 
                    android.util.Log.d("DECLUTTR_DEBUG", "Dialog show() succeeded")
                }
            } catch (e: Exception) {
                android.util.Log.e("DECLUTTR_DEBUG", "Failed to show review dialog", e)
                android.util.Log.e("DECLUTTR_CRASH", "Dialog exception: ${e.message}", e)
                reviewData = null
                null
            }
            onDispose {
                try { dialog?.dismiss() } catch (_: Exception) {}
            }
        }
    }

    val tabs = listOf("Discover", "My Archive")
    val tabIcons = listOf(Icons.Default.Search, Icons.Default.List)

    var selectedAppId by remember { mutableStateOf<String?>(null) }

    if (selectedAppId != null && activityContext != null) {
        val appToShow = archivedApps.find { it.packageId == selectedAppId }
        if (appToShow != null) {
            DisposableEffect(appToShow, activityContext) {
                val dialog = NativeAppDetailsDialog(
                    context = activityContext,
                    app = appToShow,
                    onNotesUpdated = { newNotes ->
                        viewModel.updateArchivedApp(appToShow.copy(notes = newNotes))
                    },
                    onDelete = {
                        viewModel.deleteArchivedApp(appToShow)
                        selectedAppId = null
                    },
                    onDismissRequest = {
                        selectedAppId = null
                    }
                ).apply {
                    show()
                }
                onDispose {
                    try { dialog.dismiss() } catch (e: Exception) {}
                }
            }
        } else {
            selectedAppId = null
        }
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
