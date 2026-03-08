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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

enum class DiscoveryViewState {
    DASHBOARD, RARELY_USED, LARGE_APPS, ALL_APPS
}

@Composable
fun DiscoveryScreen(
    unusedApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    largeApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    allApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
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
    onBack: () -> Unit,
    onBatchUninstall: (Set<String>) -> Unit,
    onBatchUninstallOnly: (Set<String>) -> Unit
) {
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSideloadWarning by remember { mutableStateOf(false) }
    var appsToUninstall by remember { mutableStateOf<Set<String>>(emptySet()) }

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

        if (appList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        "No apps to display in this category.", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(appList, key = { it.packageId }) { app ->
                    val isSelected = selectedApps.contains(app.packageId)
                    
                    AppListCard(
                        app = app,
                        isSelected = isSelected,
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
    onToggle: () -> Unit
) {
    // Cache the decoded bitmap — only decode once per unique iconBytes reference
    val cachedBitmap: ImageBitmap? = remember(app.iconBytes) {
        app.iconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            if (cachedBitmap != null) {
                Image(
                    bitmap = cachedBitmap,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${bytesToMB(app.apkSizeBytes)} MB", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun bytesToMB(bytes: Long): Long {
    return bytes / (1024 * 1024)
}
