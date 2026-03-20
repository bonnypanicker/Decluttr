package com.example.decluttr.presentation.screens.dashboard

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase
import kotlin.math.roundToInt

enum class DiscoveryViewState {
    DASHBOARD, RARELY_USED, LARGE_APPS, ALL_APPS
}

enum class SortOption(val label: String) {
    NAME("Name"), SIZE("Size"), LAST_USED("Last Used")
}

@Composable
fun DiscoveryScreen(
    unusedApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    largeApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    allApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    isLoading: Boolean,
    isPreparingAllApps: Boolean,
    uninstallProgress: DashboardViewModel.UninstallProgress,
    onRefresh: () -> Unit,
    onPrepareAllApps: () -> Unit,
    onPrefetchPackages: (List<String>) -> Unit,
    onBatchUninstall: (Set<String>) -> Unit,
    onBatchUninstallOnly: (Set<String>) -> Unit,
    hasUsagePermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var viewState by remember { mutableStateOf(DiscoveryViewState.DASHBOARD) }
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Check permission on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRequestPermission()
                onRefresh()
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // Handle back button when not in Dashboard mode
    BackHandler(enabled = viewState != DiscoveryViewState.DASHBOARD) {
        viewState = DiscoveryViewState.DASHBOARD
    }

    if (isLoading || isPreparingAllApps) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (viewState) {
                DiscoveryViewState.DASHBOARD -> {
                    DiscoveryDashboard(
                        unusedApps = unusedApps,
                        largeApps = largeApps,
                        allApps = allApps,
                        hasUsagePermission = hasUsagePermission,
                        onRequestPermission = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignored
                            }
                        },
                        onNavigateToSpecificList = { newState -> viewState = newState },
                        onBatchUninstall = onBatchUninstall,
                        onBatchUninstallOnly = onBatchUninstallOnly
                    )
                }
                DiscoveryViewState.RARELY_USED -> {
                    SpecificAppListDisplay(
                        title = "Rarely Used Apps",
                        appList = unusedApps,
                        listType = DiscoveryViewState.RARELY_USED,
                        onPrefetchPackages = onPrefetchPackages,
                        onBack = { viewState = DiscoveryViewState.DASHBOARD },
                        onBatchUninstall = { ids -> 
                            viewState = DiscoveryViewState.DASHBOARD
                            onBatchUninstall(ids) 
                        },
                        onBatchUninstallOnly = { ids -> 
                            viewState = DiscoveryViewState.DASHBOARD
                            onBatchUninstallOnly(ids) 
                        }
                    )
                }
                DiscoveryViewState.LARGE_APPS -> {
                    SpecificAppListDisplay(
                        title = "Large Apps (>100MB)",
                        appList = largeApps,
                        listType = DiscoveryViewState.LARGE_APPS,
                        onPrefetchPackages = onPrefetchPackages,
                        onBack = { viewState = DiscoveryViewState.DASHBOARD },
                        onBatchUninstall = { ids -> 
                            viewState = DiscoveryViewState.DASHBOARD
                            onBatchUninstall(ids) 
                        },
                        onBatchUninstallOnly = { ids -> 
                            viewState = DiscoveryViewState.DASHBOARD
                            onBatchUninstallOnly(ids) 
                        }
                    )
                }
                DiscoveryViewState.ALL_APPS -> {
                    SpecificAppListDisplay(
                        title = "All Installed Apps",
                        appList = allApps,
                        listType = DiscoveryViewState.ALL_APPS,
                        onPrefetchPackages = onPrefetchPackages,
                        onBack = { viewState = DiscoveryViewState.DASHBOARD },
                        onBatchUninstall = { ids -> 
                            viewState = DiscoveryViewState.DASHBOARD
                            onBatchUninstall(ids) 
                        },
                        onBatchUninstallOnly = { ids -> 
                            viewState = DiscoveryViewState.DASHBOARD
                            onBatchUninstallOnly(ids) 
                        }
                    )
                }
            }
        }
        
        if (uninstallProgress.isUninstalling) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Uninstalling app ${uninstallProgress.current} of ${uninstallProgress.total}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please wait...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryDashboard(
    unusedApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    largeApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    allApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    hasUsagePermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToSpecificList: (DiscoveryViewState) -> Unit,
    onBatchUninstall: (Set<String>) -> Unit,
    onBatchUninstallOnly: (Set<String>) -> Unit
) {
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Extract Compose theme colors for native views
    val themeColors = NativeThemeColors(
        textPrimary = MaterialTheme.colorScheme.onSurface.toArgb(),
        textSecondary = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        textTertiary = MaterialTheme.colorScheme.tertiary.toArgb(),
        selectedBackground = MaterialTheme.colorScheme.primaryContainer.toArgb(),
        normalBackground = MaterialTheme.colorScheme.surface.toArgb(),
        checkboxTint = MaterialTheme.colorScheme.primary.toArgb()
    )

    // Filter apps based on search query
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Build the list of items for RecyclerView
    val dashboardItems = remember(unusedApps, largeApps, filteredApps, hasUsagePermission, isSearchActive, searchQuery, selectedApps) {
        buildList {
            // Storage meter
            if (allApps.isNotEmpty()) {
                val totalSize = allApps.sumOf { it.apkSizeBytes }
                val wasteSize = unusedApps.sumOf { it.apkSizeBytes }
                val percentage = if (totalSize > 0) ((wasteSize.toFloat() / totalSize.toFloat()) * 100).roundToInt() else 0
                add(DashboardItem.StorageMeter(wasteSize, totalSize, percentage))
            }

            // Permission warning or rarely used card
            if (!hasUsagePermission) {
                add(DashboardItem.PermissionWarning())
            } else {
                add(DashboardItem.SmartCard(
                    icon = "📦",
                    title = "Rarely Used Apps",
                    description = "${unusedApps.size} apps • ${bytesToMB(unusedApps.sumOf { it.apkSizeBytes })} MB",
                    viewState = DiscoveryViewState.RARELY_USED
                ))
            }

            // Large apps card
            add(DashboardItem.SmartCard(
                icon = "💾",
                title = "Large Apps",
                description = "${largeApps.size} apps • ${bytesToMB(largeApps.sumOf { it.apkSizeBytes })} MB",
                viewState = DiscoveryViewState.LARGE_APPS
            ))

            // All apps header
            add(DashboardItem.AllAppsHeader(isSearchActive))

            // Search bar (if active)
            if (isSearchActive) {
                add(DashboardItem.SearchBar(searchQuery))
            }

            // App items
            filteredApps.forEach { app ->
                add(DashboardItem.AppItem(app, app.packageId in selectedApps))
            }
        }
    }

    // Handle back press when search is active
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = DiscoveryDashboardAdapter(
                        onNavigateToList = onNavigateToSpecificList,
                        onRequestPermission = onRequestPermission,
                        onToggleApp = { packageId ->
                            selectedApps = if (packageId in selectedApps) {
                                selectedApps - packageId
                            } else {
                                selectedApps + packageId
                            }
                        },
                        onSearchToggle = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                isSearchActive = true
                            }
                        },
                        onSearchQueryChange = { query ->
                            searchQuery = query
                        },
                        themeColors = themeColors
                    )
                    setPadding(
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6,
                        if (selectedApps.isNotEmpty()) resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 2 else resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6
                    )
                    clipToPadding = false
                }
            },
            update = { recyclerView ->
                val adapter = recyclerView.adapter as DiscoveryDashboardAdapter
                adapter.themeColors = themeColors
                adapter.submitList(dashboardItems)
                
                // Update padding based on selection
                recyclerView.setPadding(
                    recyclerView.paddingLeft,
                    recyclerView.paddingTop,
                    recyclerView.paddingRight,
                    if (selectedApps.isNotEmpty()) recyclerView.resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 2 
                    else recyclerView.resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6
                )
            }
        )

        // Floating action buttons
        if (selectedApps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val selected = selectedApps.toSet()
                        selectedApps = emptySet()
                        onBatchUninstallOnly(selected)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Uninstall Only", textAlign = TextAlign.Center)
                }
                Button(
                    onClick = {
                        val selected = selectedApps.toSet()
                        selectedApps = emptySet()
                        onBatchUninstall(selected)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Archive & Uninstall", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun bytesToMB(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) {
        String.format(java.util.Locale.US, "%.1f", mb)
    } else {
        String.format(java.util.Locale.US, "%.0f", mb)
    }
}

@Composable
fun SpecificAppListDisplay(
    title: String,
    appList: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    listType: DiscoveryViewState = DiscoveryViewState.ALL_APPS,
    onPrefetchPackages: (List<String>) -> Unit = {},
    onBack: () -> Unit,
    onBatchUninstall: (Set<String>) -> Unit,
    onBatchUninstallOnly: (Set<String>) -> Unit
) {
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSideloadWarning by remember { mutableStateOf(false) }
    var appsToUninstall by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.NAME) }

    val filteredList = remember(appList, searchQuery, sortOption) {
        filterAndSortApps(appList, searchQuery, sortOption)
    }

    val appSizeByPackageId = remember(appList) {
        appList.associate { it.packageId to it.apkSizeBytes }
    }
    val selectedSize = remember(selectedApps, appSizeByPackageId) {
        selectedApps.sumOf { packageId -> appSizeByPackageId[packageId] ?: 0L }
    }

    // Extract Compose theme colors for native views
    val themeColors = NativeThemeColors(
        textPrimary = MaterialTheme.colorScheme.onSurface.toArgb(),
        textSecondary = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        textTertiary = MaterialTheme.colorScheme.tertiary.toArgb(),
        selectedBackground = MaterialTheme.colorScheme.primaryContainer.toArgb(),
        normalBackground = MaterialTheme.colorScheme.surface.toArgb(),
        checkboxTint = MaterialTheme.colorScheme.primary.toArgb()
    )

    if (showSideloadWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSideloadWarning = false },
            title = { androidx.compose.material3.Text("Sideloaded Apps Detected", fontWeight = FontWeight.Bold) },
            text = { androidx.compose.material3.Text("One or more of the selected apps were not installed from the Google Play Store. It is not possible to archive sideloaded apps.\n\nDo you wish to uninstall them anyway?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showSideloadWarning = false
                        val playStoreIds = appList.filter { it.packageId in appsToUninstall && it.isPlayStoreInstalled }.map { it.packageId }.toSet()
                        val sideloadedIds = appList.filter { it.packageId in appsToUninstall && !it.isPlayStoreInstalled }.map { it.packageId }.toSet()
                        selectedApps = emptySet()
                        if (playStoreIds.isNotEmpty()) onBatchUninstall(playStoreIds)
                        if (sideloadedIds.isNotEmpty()) onBatchUninstallOnly(sideloadedIds)
                    }
                ) {
                    androidx.compose.material3.Text("Uninstall Anyway")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSideloadWarning = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button, title, search, sort, and select all - all in Compose
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Back button and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search bar
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps") },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100),
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sort row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SortOption.entries.forEach { option ->
                    androidx.compose.material3.FilterChip(
                        selected = sortOption == option,
                        onClick = { sortOption = option },
                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Select All toggle + selection info
            if (filteredList.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val allFilteredSelected = filteredList.all { it.packageId in selectedApps }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = allFilteredSelected,
                            onCheckedChange = { checked ->
                                selectedApps = if (checked) {
                                    selectedApps + filteredList.map { it.packageId }
                                } else {
                                    selectedApps - filteredList.map { it.packageId }.toSet()
                                }
                            }
                        )
                        Text(
                            text = if (allFilteredSelected) "Deselect All" else "Select All",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (selectedApps.isNotEmpty()) {
                        Text(
                            text = "${selectedApps.size} of ${appList.size} • ${bytesToMB(selectedSize)} MB",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Action buttons when items selected
            if (selectedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val ids = selectedApps.toSet()
                            selectedApps = emptySet()
                            onBatchUninstallOnly(ids)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Uninstall Only", textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = {
                            val ids = selectedApps.toSet()
                            val hasSideloaded = appList.any { it.packageId in ids && !it.isPlayStoreInstalled }
                            if (hasSideloaded) {
                                appsToUninstall = ids
                                showSideloadWarning = true
                            } else {
                                selectedApps = emptySet()
                                onBatchUninstall(ids)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Archive &\nUninstall", textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // RecyclerView for app list
        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            "No apps match \"$searchQuery\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No apps to display in this category.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Map items with context labels
            val mappedItems = remember(filteredList, selectedApps, listType) {
                filteredList.map { app ->
                    val ctxLabel = when (listType) {
                        DiscoveryViewState.RARELY_USED -> {
                            if (app.lastTimeUsed > 0) {
                                val now = System.currentTimeMillis()
                                val daysAgo = ((now - app.lastTimeUsed) / DateUtils.DAY_IN_MILLIS).toInt()
                                "Not used in $daysAgo days"
                            } else "Never opened"
                        }
                        DiscoveryViewState.LARGE_APPS -> "Takes ${bytesToMB(app.apkSizeBytes)} MB"
                        else -> null
                    }
                    AppListItem(
                        info = app,
                        isSelected = app.packageId in selectedApps,
                        contextLabel = ctxLabel
                    )
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    RecyclerView(context).apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = DiscoveryAppsAdapter(
                            onToggle = { packageId ->
                                selectedApps = if (packageId in selectedApps) {
                                    selectedApps - packageId
                                } else {
                                    selectedApps + packageId
                                }
                            },
                            themeColors = themeColors
                        )
                        setPadding(
                            resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6,
                            resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6,
                            resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6,
                            resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 6
                        )
                        clipToPadding = false
                    }
                },
                update = { recyclerView ->
                    val adapter = recyclerView.adapter as DiscoveryAppsAdapter
                    adapter.themeColors = themeColors
                    adapter.submitList(mappedItems)
                }
            )
        }
    }
}

internal fun filterAndSortApps(
    appList: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    searchQuery: String,
    sortOption: SortOption
): List<GetInstalledAppsUseCase.InstalledAppInfo> {
    val filtered = if (searchQuery.isBlank()) appList
    else appList.filter { it.name.contains(searchQuery, ignoreCase = true) }
    return when (sortOption) {
        SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
        SortOption.SIZE -> filtered.sortedByDescending { it.apkSizeBytes }
        SortOption.LAST_USED -> filtered.sortedBy { it.lastTimeUsed }
    }
}
