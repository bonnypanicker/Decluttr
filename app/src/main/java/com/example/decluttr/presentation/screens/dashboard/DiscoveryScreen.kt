package com.example.decluttr.presentation.screens.dashboard

import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import android.view.Choreographer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.decluttr.BuildConfig
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.example.decluttr.presentation.util.AppIconModel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
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
                        val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
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
                .clickable(enabled = false) {}, // Intercept clicks
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
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
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
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.screenWidthDp >= 840) 24.dp else 16.dp
    var scrollImpulseTarget by remember { mutableFloatStateOf(0f) }
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(listState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val delta = ((index - lastIndex) * 320 + (offset - lastOffset)).toFloat()
                scrollImpulseTarget = (-delta * 0.15f).coerceIn(-20f, 20f)
                lastIndex = index
                lastOffset = offset
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) {
                    scrollImpulseTarget = 0f
                }
            }
    }

    val scrollImpulse by animateFloatAsState(
        targetValue = scrollImpulseTarget,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 430f),
        label = "dashboardScrollImpulse"
    )

    val allAppsHeaderProgress by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val largeCardItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == "large_apps_card" }
            if (largeCardItem == null) {
                if (listState.firstVisibleItemIndex > 2) 1f else 0f
            } else {
                val visibleStart = maxOf(layoutInfo.viewportStartOffset, largeCardItem.offset)
                val visibleEnd = minOf(layoutInfo.viewportEndOffset, largeCardItem.offset + largeCardItem.size)
                val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
                val visibleFraction = if (largeCardItem.size > 0) visiblePx.toFloat() / largeCardItem.size else 0f
                (1f - visibleFraction).coerceIn(0f, 1f)
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "storage_meter") {
                DashboardMotionContainer(
                    motion = scrollImpulse,
                    intensity = 0.35f
                ) {
                    StorageImpactMeter(unusedApps = unusedApps, allApps = allApps)
                }
            }

            item(key = "rarely_used_card") {
                if (!hasUsagePermission) {
                    DashboardMotionContainer(
                        motion = scrollImpulse,
                        intensity = 0.55f
                    ) {
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
                    }
                } else {
                    DashboardMotionContainer(
                        motion = scrollImpulse,
                        intensity = 0.55f
                    ) {
                        SmartDeclutterCard(
                            icon = "📦",
                            title = "Rarely Used Apps",
                            description = "${unusedApps.size} apps • ${bytesToMB(unusedApps.sumOf { it.apkSizeBytes })} MB",
                            onClick = { onNavigateToSpecificList(DiscoveryViewState.RARELY_USED) }
                        )
                    }
                }
            }

            item(key = "large_apps_card") {
                DashboardMotionContainer(
                    motion = scrollImpulse,
                    intensity = 0.75f
                ) {
                    SmartDeclutterCard(
                        icon = "💾",
                        title = "Large Apps",
                        description = "${largeApps.size} apps • ${bytesToMB(largeApps.sumOf { it.apkSizeBytes })} MB",
                        onClick = { onNavigateToSpecificList(DiscoveryViewState.LARGE_APPS) }
                    )
                }
            }

            item(key = "all_apps_header") {
                AllAppsSection(headerProgress = allAppsHeaderProgress)
            }

            items(
                items = allApps,
                key = { it.packageId },
                contentType = { "all_apps_row" }
            ) { app ->
                val isSelected = app.packageId in selectedApps
                AllAppsSelectableCard(
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
            }

            if (selectedApps.isNotEmpty()) {
                item(key = "all_apps_bottom_spacer") {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }

        if (selectedApps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
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

@Composable
private fun DashboardMotionContainer(
    motion: Float,
    intensity: Float,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = motion * intensity
        }
    ) {
        content()
    }
}

@Composable
private fun AllAppsSection(
    headerProgress: Float
) {
    val transitionEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val headerScale by animateFloatAsState(
        targetValue = 1f + (0.5f * headerProgress),
        animationSpec = tween(durationMillis = 300, easing = transitionEasing),
        label = "allAppsHeaderScale"
    )
    val headerAlpha by animateFloatAsState(
        targetValue = 1f - (0.1f * headerProgress),
        animationSpec = tween(durationMillis = 300, easing = transitionEasing),
        label = "allAppsHeaderAlpha"
    )
    val headerBias by animateFloatAsState(
        targetValue = -1f + headerProgress,
        animationSpec = tween(durationMillis = 300, easing = transitionEasing),
        label = "allAppsHeaderBias"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
    ) {
        Text(
            text = "All Apps",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.12f),
                    offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                    blurRadius = 2f
                )
            ),
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = headerBias, verticalBias = 0f))
                .graphicsLayer {
                    scaleX = headerScale
                    scaleY = headerScale
                    alpha = headerAlpha
                }
        )
    }
}

@Composable
private fun AllAppsSelectableCard(
    app: GetInstalledAppsUseCase.InstalledAppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(app.packageId) {
        ImageRequest.Builder(context)
            .data(AppIconModel(app.packageId))
            .memoryCacheKey(app.packageId)
            .size(96)
            .crossfade(false)
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        AsyncImage(
            model = imageRequest,
            contentDescription = "${app.name} icon",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${bytesToMB(app.apkSizeBytes)} MB",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
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

    val filteredList by remember(appList, searchQuery, sortOption) {
        derivedStateOf { filterAndSortApps(appList, searchQuery, sortOption) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, filteredList) {
        snapshotFlow {
            Pair(
                listState.firstVisibleItemIndex,
                listState.layoutInfo.visibleItemsInfo.size
            )
        }
            .distinctUntilChanged()
            .collect { (firstIndex, visibleCount) ->
                if (filteredList.isEmpty()) return@collect
                val start = (firstIndex - 4).coerceAtLeast(0)
                val window = (visibleCount + 10).coerceAtLeast(12)
                val endExclusive = (start + window).coerceAtMost(filteredList.size)
                if (start >= endExclusive) return@collect
                onPrefetchPackages(
                    filteredList.subList(start, endExclusive).map { it.packageId }
                )
            }
    }
    ScrollPerfDebugProbe(
        listState = listState,
        listType = listType,
        listSize = filteredList.size,
        selectedCount = selectedApps.size
    )

    val appSizeByPackageId = remember(appList) {
        appList.associate { it.packageId to it.apkSizeBytes }
    }
    val selectedSize = remember(selectedApps, appSizeByPackageId) {
        selectedApps.sumOf { packageId -> appSizeByPackageId[packageId] ?: 0L }
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
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                factory = { ctx ->
                    androidx.recyclerview.widget.RecyclerView(ctx).apply {
                        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(ctx)
                        adapter = DiscoveryAppsAdapter(
                            onToggle = { packageId ->
                                selectedApps = if (packageId in selectedApps) {
                                    selectedApps - packageId
                                } else {
                                    selectedApps + packageId
                                }
                            }
                        )
                    }
                },
                update = { recyclerView ->
                    val adapter = recyclerView.adapter as DiscoveryAppsAdapter
                    val mappedItems = filteredList.map { app ->
                        val now = System.currentTimeMillis()
                        val sizeLabel = "${bytesToMB(app.apkSizeBytes)} MB"
                        val ctxLabel = when (listType) {
                            DiscoveryViewState.RARELY_USED -> {
                                if (app.lastTimeUsed > 0) {
                                    val daysAgo = ((now - app.lastTimeUsed) / DateUtils.DAY_IN_MILLIS).toInt()
                                    "Not used in $daysAgo days"
                                } else "Never opened"
                            }
                            DiscoveryViewState.LARGE_APPS -> "Takes $sizeLabel"
                            else -> null
                        }
                        
                        AppListItem(
                            info = app,
                            isSelected = app.packageId in selectedApps,
                            contextLabel = ctxLabel
                        )
                    }
                    adapter.submitList(mappedItems)
                }
            )
        }
    }
}

private fun bytesToMB(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) {
        String.format(Locale.US, "%.1f", mb)
    } else {
        String.format(Locale.US, "%.0f", mb)
    }
}

@Composable
private fun ScrollPerfDebugProbe(
    listState: LazyListState,
    listType: DiscoveryViewState,
    listSize: Int,
    selectedCount: Int
) {
    if (!BuildConfig.DEBUG) return
    val currentListSize by rememberUpdatedState(listSize)
    val currentSelectedCount by rememberUpdatedState(selectedCount)
    val currentListType by rememberUpdatedState(listType)
    DisposableEffect(listState) {
        Log.i("DecluttrScroll", "adb logcat -c")
        Log.i("DecluttrScroll", "adb logcat -s DecluttrScroll")
        Log.i("DecluttrScroll", "adb logcat -v time -s DecluttrScroll > decluttr_scroll_trace.txt")
        val choreographer = Choreographer.getInstance()
        var previousFrameNanos = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (previousFrameNanos != 0L) {
                    val deltaMs = (frameTimeNanos - previousFrameNanos) / 1_000_000
                    if (deltaMs > 20 && listState.isScrollInProgress) {
                        val layoutInfo = listState.layoutInfo
                        Log.w(
                            "DecluttrScroll",
                            "jank_frame_ms=$deltaMs type=$currentListType first=${listState.firstVisibleItemIndex}:${listState.firstVisibleItemScrollOffset} visible=${layoutInfo.visibleItemsInfo.size} total=${layoutInfo.totalItemsCount} filtered=$currentListSize selected=$currentSelectedCount"
                        )
                    }
                }
                previousFrameNanos = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            choreographer.removeFrameCallback(callback)
        }
    }
}

internal fun filterAndSortApps(
    appList: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    searchQuery: String,
    sortOption: SortOption
): List<GetInstalledAppsUseCase.InstalledAppInfo> {
    val startNanos = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L
    val filtered = if (searchQuery.isBlank()) appList
    else appList.filter { it.name.contains(searchQuery, ignoreCase = true) }
    val result = when (sortOption) {
        SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
        SortOption.SIZE -> filtered.sortedByDescending { it.apkSizeBytes }
        SortOption.LAST_USED -> filtered.sortedBy { it.lastTimeUsed }
    }
    if (BuildConfig.DEBUG) {
        val durationMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
        if (durationMs >= 8.0) {
            Log.d(
                "DecluttrScroll",
                "filter_sort_ms=${"%.2f".format(durationMs)} input=${appList.size} output=${result.size} queryLen=${searchQuery.length} sort=$sortOption"
            )
        }
    }
    return result
}
