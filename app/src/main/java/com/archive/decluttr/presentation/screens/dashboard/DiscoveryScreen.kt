package com.archive.decluttr.presentation.screens.dashboard

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.archive.decluttr.domain.usecase.GetInstalledAppsUseCase
import kotlin.math.roundToInt

enum class DiscoveryViewState {
    DASHBOARD, RARELY_USED, LARGE_APPS, ALL_APPS
}

enum class SortOption(val label: String) {
    NAME("Name"), SIZE("Size"), LAST_USED("Last Used")
}

/**
 * Mutable callback reference bridging native TextWatcher → Compose state.
 * Stored as a View tag on the root layout; the update block refreshes the lambda.
 */
internal class SearchQueryCallback {
    var onQueryChange: ((String) -> Unit)? = null
    var onExitSearch: (() -> Unit)? = null
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
            // ═══ TOP CARDS: Only shown when NOT searching ═══
            if (!isSearchActive) {
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
            }

            // All Apps header — always shown
            add(DashboardItem.AllAppsHeader(isSearchActive))

            // NO DashboardItem.SearchBar — search bar is now a pinned native View

            // App items
            filteredApps.forEach { app ->
                add(DashboardItem.AppItem(app, app.packageId in selectedApps))
            }
        }
    }

    // Two-step back handler: clear text first, then exit search
    BackHandler(enabled = isSearchActive) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else {
            isSearchActive = false
            searchQuery = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                val queryCallback = SearchQueryCallback()

                // ──────────────────────────────────────────────
                // 1. PINNED SEARCH BAR (native View, above RV)
                // ──────────────────────────────────────────────
                val searchBarView = android.view.LayoutInflater.from(context)
                    .inflate(com.archive.decluttr.R.layout.item_search_bar, null, false)
                searchBarView.visibility = android.view.View.GONE
                searchBarView.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val dp8 = (8 * density).toInt()
                    val dp12 = (12 * density).toInt()
                    setMargins(dp12, dp8, dp12, dp8)
                }

                val searchEditText = searchBarView.findViewById<android.widget.EditText>(com.archive.decluttr.R.id.search_edit_text)
                val clearButton = searchBarView.findViewById<android.widget.ImageView>(com.archive.decluttr.R.id.clear_button)

                // TextWatcher → bridges to Compose state via queryCallback
                searchEditText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val query = s.toString()
                        clearButton.visibility = if (query.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                        queryCallback.onQueryChange?.invoke(query)
                    }
                })

                // Clear/close button: two-step cancel
                clearButton.setOnClickListener {
                    if (searchEditText.text.isNotEmpty()) {
                        searchEditText.setText("")
                    } else {
                        queryCallback.onExitSearch?.invoke()
                    }
                }

                // ──────────────────────────────────────────────
                // 2. RECYCLERVIEW
                // ──────────────────────────────────────────────
                val recyclerView = RecyclerView(context).apply {
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
                    itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                        addDuration = 250
                        removeDuration = 200
                        moveDuration = 250
                    }
                    val dp12 = (12 * density).toInt()
                    val dp80 = (80 * density).toInt()
                    setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)
                    clipToPadding = false
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1f  // height=0, weight=1 → fill remaining space
                    )
                }

                // ──────────────────────────────────────────────
                // 3. ROOT CONTAINER (LinearLayout, vertical)
                // ──────────────────────────────────────────────
                android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    addView(searchBarView)
                    addView(recyclerView)

                    // Store references via tag for the update block
                    tag = Triple(searchBarView, recyclerView, queryCallback)
                }
            },
            update = { rootLayout ->
                val (searchBarView, recyclerView, queryCallback) =
                    rootLayout.tag as Triple<android.view.View, RecyclerView, SearchQueryCallback>

                val searchEditText = searchBarView.findViewById<android.widget.EditText>(com.archive.decluttr.R.id.search_edit_text)
                val clearButton = searchBarView.findViewById<android.widget.ImageView>(com.archive.decluttr.R.id.clear_button)
                val imm = rootLayout.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                val density = rootLayout.context.resources.displayMetrics.density

                // ── Bridge callbacks to current Compose state ──
                queryCallback.onQueryChange = { query -> searchQuery = query }
                queryCallback.onExitSearch = {
                    isSearchActive = false
                    searchQuery = ""
                }

                // ── Update clear button click handler ──
                clearButton.setOnClickListener {
                    if (searchEditText.text.isNotEmpty()) {
                        searchEditText.setText("")
                    } else {
                        isSearchActive = false
                        searchQuery = ""
                    }
                }

                // ── Update adapter data ──
                val adapter = recyclerView.adapter as DiscoveryDashboardAdapter
                adapter.themeColors = themeColors
                adapter.submitList(dashboardItems) {
                    if (isSearchActive) {
                        recyclerView.scrollToPosition(0)
                    }
                }

                // ── Update RecyclerView bottom padding ──
                val dp12 = (12 * density).toInt()
                val dp80 = (80 * density).toInt()
                recyclerView.setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)

                // ── Animate pinned search bar in/out ──
                val wasVisible = searchBarView.visibility == android.view.View.VISIBLE

                if (isSearchActive && !wasVisible) {
                    // ▸ SHOW: slide down + fade in
                    searchBarView.visibility = android.view.View.VISIBLE
                    searchBarView.translationY = -(200 * density)  // Start off-screen above
                    searchBarView.alpha = 0f
                    searchBarView.animate()
                        .cancel()  // Cancel any in-flight animation
                    searchBarView.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .withEndAction {
                            searchEditText.requestFocus()
                            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        }
                        .start()

                } else if (!isSearchActive && wasVisible) {
                    // ▸ HIDE: slide up + fade out
                    imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                    searchEditText.setText("")
                    searchEditText.clearFocus()
                    searchBarView.animate()
                        .cancel()  // Cancel any in-flight animation
                    searchBarView.animate()
                        .translationY(-(200 * density))
                        .alpha(0f)
                        .setDuration(250)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .withEndAction {
                            searchBarView.visibility = android.view.View.GONE
                            searchBarView.translationY = 0f
                            searchBarView.alpha = 1f
                        }
                        .start()
                }

                // ── Sync EditText text with Compose state (avoid TextWatcher loop) ──
                val currentText = searchEditText.text.toString()
                if (currentText != searchQuery) {
                    searchEditText.setText(searchQuery)
                    searchEditText.setSelection(searchQuery.length)
                }
            }
        )

        // ═══ FLOATING ACTION BUTTONS (unchanged) ═══
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

            // Native search bar
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    val searchBarView = android.view.LayoutInflater.from(ctx)
                        .inflate(com.archive.decluttr.R.layout.item_search_bar, null, false)

                    val searchEditText = searchBarView
                        .findViewById<android.widget.EditText>(com.archive.decluttr.R.id.search_edit_text)
                    val clearButton = searchBarView
                        .findViewById<android.widget.ImageView>(com.archive.decluttr.R.id.clear_button)

                    searchEditText.hint = "Search apps"

                    val callback = SearchQueryCallback()
                    searchBarView.setTag(com.archive.decluttr.R.id.specific_search_callback, callback)

                    searchEditText.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            val q = s.toString()
                            clearButton.visibility =
                                if (q.isEmpty()) android.view.View.GONE
                                else android.view.View.VISIBLE
                            callback.onQueryChange?.invoke(q)
                        }
                    })

                    clearButton.setOnClickListener {
                        searchEditText.setText("")
                    }

                    searchBarView
                },
                update = { view ->
                    val callback = view.getTag(com.archive.decluttr.R.id.specific_search_callback) as SearchQueryCallback
                    callback.onQueryChange = { searchQuery = it }

                    val editText = view.findViewById<android.widget.EditText>(com.archive.decluttr.R.id.search_edit_text)
                    if (editText.text.toString() != searchQuery) {
                        editText.setText(searchQuery)
                        editText.setSelection(searchQuery.length)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Native sort chips
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    android.widget.HorizontalScrollView(ctx).apply {
                        isHorizontalScrollBarEnabled = false
                        addView(
                            com.google.android.material.chip.ChipGroup(ctx).apply {
                                id = com.archive.decluttr.R.id.sort_chip_group
                                isSingleSelection = true
                                isSelectionRequired = true

                                SortOption.entries.forEach { option ->
                                    addView(
                                        com.google.android.material.chip.Chip(ctx).apply {
                                            text = option.label
                                            isCheckable = true
                                            isChecked = option == SortOption.NAME
                                            tag = option
                                            setOnClickListener {
                                                sortOption = tag as SortOption
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                },
                update = { hsv ->
                    val group = hsv.findViewById<com.google.android.material.chip.ChipGroup>(com.archive.decluttr.R.id.sort_chip_group)
                    for (i in 0 until group.childCount) {
                        val chip = group.getChildAt(i) as com.google.android.material.chip.Chip
                        chip.isChecked = chip.tag == sortOption
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Native select all row
            if (filteredList.isNotEmpty()) {
                val allFilteredSelected = filteredList.all { it.packageId in selectedApps }

                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        android.widget.LinearLayout(ctx).apply {
                            id = com.archive.decluttr.R.id.select_all_container
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            val dp8 = (8 * ctx.resources.displayMetrics.density).toInt()
                            setPadding(0, dp8, 0, dp8)

                            // Checkbox
                            addView(android.widget.CheckBox(ctx).apply {
                                id = com.archive.decluttr.R.id.select_all_checkbox
                            })

                            // Label
                            addView(android.widget.TextView(ctx).apply {
                                id = com.archive.decluttr.R.id.select_all_label
                                val dp4 = (4 * ctx.resources.displayMetrics.density).toInt()
                                setPadding(dp4, 0, 0, 0)
                                textSize = 14f
                            })

                            // Spacer
                            addView(android.view.View(ctx).apply {
                                layoutParams = android.widget.LinearLayout.LayoutParams(
                                    0, 1, 1f
                                )
                            })

                            // Selection info
                            addView(android.widget.TextView(ctx).apply {
                                id = com.archive.decluttr.R.id.selection_info
                                textSize = 12f
                            })
                        }
                    },
                    update = { layout ->
                        val checkBox = layout.findViewById<android.widget.CheckBox>(com.archive.decluttr.R.id.select_all_checkbox)
                        val label = layout.findViewById<android.widget.TextView>(com.archive.decluttr.R.id.select_all_label)
                        val info = layout.findViewById<android.widget.TextView>(com.archive.decluttr.R.id.selection_info)

                        checkBox.setOnCheckedChangeListener(null)
                        checkBox.isChecked = allFilteredSelected
                        checkBox.setOnCheckedChangeListener { _, checked ->
                            selectedApps = if (checked) {
                                selectedApps + filteredList.map { it.packageId }
                            } else {
                                selectedApps - filteredList.map { it.packageId }.toSet()
                            }
                        }

                        label.text = if (allFilteredSelected) "Deselect All" else "Select All"

                        if (selectedApps.isNotEmpty()) {
                            info.visibility = android.view.View.VISIBLE
                            info.text = "${selectedApps.size} of ${appList.size} • ${bytesToMB(selectedSize)} MB"
                        } else {
                            info.visibility = android.view.View.GONE
                        }
                    }
                )
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
                        val dp12 = (12 * context.resources.displayMetrics.density).toInt()
                        setPadding(dp12, dp12, dp12, dp12)
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
