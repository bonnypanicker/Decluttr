package com.example.decluttr.presentation.screens.dashboard

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase
import kotlin.math.roundToInt
import androidx.compose.runtime.Immutable

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
    bitmapCache: Map<String, ImageBitmap>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onForceRefresh: () -> Unit,
    onBatchUninstall: (Set<String>) -> Unit,
    onBatchUninstallOnly: (Set<String>) -> Unit,
    hasUsagePermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var viewState by remember { mutableStateOf(DiscoveryViewState.DASHBOARD) }
    
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // Check permission on resume
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
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

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (viewState) {
            DiscoveryViewState.DASHBOARD -> {
                DiscoveryDashboard(
                    unusedApps = unusedApps,
                    largeApps = largeApps,
                    allApps = allApps,
                    hasUsagePermission = hasUsagePermission,
                    onRequestPermission = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignored
                        }
                    },
                    onNavigateToSpecificList = { newState -> viewState = newState }
                )
            }
            DiscoveryViewState.RARELY_USED -> {
                SpecificAppListDisplay(
                    title = "Rarely Used Apps",
                    appList = unusedApps,
                    bitmapCache = bitmapCache,
                    listType = DiscoveryViewState.RARELY_USED,
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
                    bitmapCache = bitmapCache,
                    listType = DiscoveryViewState.LARGE_APPS,
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
                    bitmapCache = bitmapCache,
                    listType = DiscoveryViewState.ALL_APPS,
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
}

@Composable
fun DiscoveryDashboard(
    unusedApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    largeApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    allApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    hasUsagePermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToSpecificList: (DiscoveryViewState) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Graphic: Storage Impact
        item {
            StorageImpactMeter(unusedApps = unusedApps, allApps = allApps)
        }
        
        // Smart Card: Rarely Used
        item {
            if (!hasUsagePermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Usage Access Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "We need permission to detect which apps you haven't used recently.", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRequestPermission) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else {
                SmartDeclutterCard(
                    icon = "📦",
                    title = "Rarely Used Apps",
                    description = "${unusedApps.size} apps • ${bytesToMB(unusedApps.sumOf { it.apkSizeBytes })} MB",
                    onClick = { onNavigateToSpecificList(DiscoveryViewState.RARELY_USED) }
                )
            }
        }
        
        // Smart Card: Large Apps
        item {
            SmartDeclutterCard(
                icon = "💾",
                title = "Large Apps",
                description = "${largeApps.size} apps • ${bytesToMB(largeApps.sumOf { it.apkSizeBytes })} MB",
                onClick = { onNavigateToSpecificList(DiscoveryViewState.LARGE_APPS) }
            )
        }

        // Secondary Action: All Apps
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.TextButton(onClick = { onNavigateToSpecificList(DiscoveryViewState.ALL_APPS) }) {
                    Text("Browse All Apps")
                }
            }
        }
    }
}

@Composable
fun StorageImpactMeter(
    unusedApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    allApps: List<GetInstalledAppsUseCase.InstalledAppInfo>
) {
    if (allApps.isEmpty()) return
    
    val totalSize = allApps.sumOf { it.apkSizeBytes }
    val wasteSize = unusedApps.sumOf { it.apkSizeBytes }
    
    val wasteRatio = if (totalSize > 0) (wasteSize.toFloat() / totalSize.toFloat()) else 0f
    val percentage = (wasteRatio * 100).roundToInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Potential Storage Freed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${bytesToMB(wasteSize)} MB",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Waste Score: $percentage%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (percentage > 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar Graphic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = wasteRatio.coerceAtMost(1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun SmartDeclutterCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            androidx.compose.material3.OutlinedButton(onClick = onClick) {
                Text("Review")
            }
        }
    }
}

@Composable
fun SpecificAppListDisplay(
    title: String,
    appList: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    bitmapCache: Map<String, ImageBitmap>,
    listType: DiscoveryViewState = DiscoveryViewState.ALL_APPS,
    onBack: () -> Unit,
    onBatchUninstall: (Set<String>) -> Unit,
    onBatchUninstallOnly: (Set<String>) -> Unit
) {
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSideloadWarning by remember { mutableStateOf(false) }
    var appsToUninstall by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.NAME) }

    // Filtered + sorted list
    val filteredList = remember(appList, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) appList
            else appList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        when (sortOption) {
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortOption.SIZE -> filtered.sortedByDescending { it.apkSizeBytes }
            SortOption.LAST_USED -> filtered.sortedBy { it.lastTimeUsed }
        }
    }

    // Selection stats
    val selectedSize = remember(selectedApps, appList) {
        appList.filter { it.packageId in selectedApps }.sumOf { it.apkSizeBytes }
    }

    if (showSideloadWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSideloadWarning = false },
            title = { Text("Sideloaded Apps Detected", fontWeight = FontWeight.Bold) },
            text = { Text("One or more of the selected apps were not installed from the Google Play Store. It is not possible to archive sideloaded apps.\n\nDo you wish to uninstall them anyway?") },
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
                    Text("Uninstall Anyway")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSideloadWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Search bar
        androidx.compose.material3.OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(100),
            singleLine = true,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Sort row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        Spacer(modifier = Modifier.height(4.dp))

        // Select All toggle + selection info
        if (filteredList.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val allFilteredSelected = filteredList.all { it.packageId in selectedApps }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            "No apps match \"$searchQuery\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.size(16.dp))
                        Text(
                            "No apps to display in this category.", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(filteredList, key = { it.packageId }) { app ->
                    val isSelected = selectedApps.contains(app.packageId)
                    
                    AppListCard(
                        app = app,
                        isSelected = isSelected,
                        cachedBitmap = bitmapCache[app.packageId],
                        listType = listType,
                        onToggle = {
                            selectedApps = if (isSelected) {
                                selectedApps - app.packageId
                            } else {
                                selectedApps + app.packageId
                            }
                        }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
fun AppListCard(
    app: GetInstalledAppsUseCase.InstalledAppInfo,
    isSelected: Boolean,
    cachedBitmap: ImageBitmap?,
    listType: DiscoveryViewState = DiscoveryViewState.ALL_APPS,
    onToggle: () -> Unit
) {
    // Use pre-decoded bitmap from ViewModel cache — no BitmapFactory on main thread
    val bitmap: ImageBitmap? = cachedBitmap ?: remember(app.packageId) {
        // Fallback: decode if not yet in cache (e.g. during initial load race)
        app.iconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    
    val timeString = remember(app.lastTimeUsed) {
        if (app.lastTimeUsed > 0) {
            DateUtils.getRelativeTimeSpanString(
                app.lastTimeUsed,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            ).toString()
        } else {
            "Never used"
        }
    }
    
    // Flat Row design instead of Card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "App Icon",
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.name, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!app.isPlayStoreInstalled) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sideloaded App",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${bytesToMB(app.apkSizeBytes)} MB", 
                    style = MaterialTheme.typography.bodySmall, 
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " • $timeString", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            // Contextual label based on list type
            val contextLabel = remember(listType, app.lastTimeUsed, app.apkSizeBytes) {
                when (listType) {
                    DiscoveryViewState.RARELY_USED -> {
                        if (app.lastTimeUsed > 0) {
                            val daysAgo = ((System.currentTimeMillis() - app.lastTimeUsed) / (1000 * 60 * 60 * 24)).toInt()
                            "Not used in $daysAgo days"
                        } else "Never opened"
                    }
                    DiscoveryViewState.LARGE_APPS -> "Takes ${bytesToMB(app.apkSizeBytes)} MB"
                    else -> null
                }
            }
            if (contextLabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contextLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

private fun bytesToMB(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) {
        String.format("%.1f", mb)
    } else {
        String.format("%.0f", mb)
    }
}
