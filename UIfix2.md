
## Prompt for Claude Sonnet 4.5

You are fixing the search interaction UX on the Decluttr Android app's **Discovery Dashboard** page. The Discovery page uses a **Compose wrapper** (`DiscoveryDashboard`) around a native **RecyclerView** (`AndroidView`) for scroll performance. The search bar is currently rendered as a regular RecyclerView item — it scrolls away with the list and does not pin to the top. This must change.

---

## Desired Behavior (UX Spec)

### When the user taps the search icon button (in the \"All Apps\" header):

1. **Top cards auto-scroll upward and exit the viewport** — the StorageMeter card, PermissionWarning/SmartCard(s), and the \"Large Apps\" SmartCard should smoothly animate upward and disappear from view. They are NOT destroyed — they are removed from the RecyclerView's data list so they don't occupy space.

2. **Search bar locks/pins to the top of the screen** — the search bar appears pinned at the very top of the content area (below the Scaffold TopAppBar), overlaying or sitting above the RecyclerView. It does NOT scroll with the list. It is a **fixed Compose element**, not a RecyclerView item.

3. **Keyboard opens automatically** — the search `TextField` receives focus immediately, and the software keyboard appears.

4. **App list shows below the pinned search bar** — only the filtered app items appear in the RecyclerView. The \"All Apps\" header text can optionally remain as the first item (now showing a back/close button instead of search icon), or be hidden entirely with the close action moved to the pinned search bar's trailing icon.

5. **Seamless animation** — the transition from dashboard mode to search mode should feel fluid: cards slide up and fade out, search bar slides down from top, all within ~300ms.

### When the user cancels/exits search (taps the X/clear button with empty text, or presses back):

1. **Search bar unpins and animates out** — slides back up or fades out.
2. **Top cards return** — StorageMeter, SmartCards re-appear in the RecyclerView list with the standard DiffUtil animation.
3. **Keyboard dismisses**.
4. **RecyclerView scrolls back to position 0** (top of the full dashboard).

### Important constraints:
- The search bar must stay pinned/locked at the top **as long as search is active** — scrolling the app list must NOT move the search bar.
- The search bar only \"unlocks\" (disappears) when the user explicitly cancels (taps X when text is empty, or presses hardware back).
- If the user has typed text and taps X, it should **clear the text first** (not exit search). A second tap on X (with empty text) exits search mode entirely.

---

## Current Architecture (Do NOT change the scrolling architecture)

```
DashboardScreen.kt (Compose Scaffold)
  └── DiscoveryScreen.kt (Compose, handles view states)
        └── DiscoveryDashboard (Compose @Composable)
              ├── State: isSearchActive, searchQuery, selectedApps
              ├── dashboardItems: List<DashboardItem> (built with remember)
              ├── BackHandler (exits search on back press)
              │
              ├── Box(fillMaxSize)
              │     └── AndroidView { RecyclerView }
              │           └── DiscoveryDashboardAdapter (heterogeneous ListAdapter)
              │                 ├── VIEW_TYPE_STORAGE_METER   → item_storage_meter.xml
              │                 ├── VIEW_TYPE_PERMISSION_WARNING → item_permission_warning.xml
              │                 ├── VIEW_TYPE_SMART_CARD       → item_smart_card.xml
              │                 ├── VIEW_TYPE_ALL_APPS_HEADER  → item_all_apps_header.xml
              │                 ├── VIEW_TYPE_SEARCH_BAR       → item_search_bar.xml
              │                 └── VIEW_TYPE_APP_ITEM         → item_discovery_app.xml
              │
              └── Floating action buttons (when selectedApps.isNotEmpty())
```

### Key state variables in `DiscoveryDashboard`:
```kotlin
var isSearchActive by remember { mutableStateOf(false) }
var searchQuery by remember { mutableStateOf(\"\") }
var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
```

### Current `dashboardItems` builder logic (lines 255-297 of DiscoveryScreen.kt):
```kotlin
val dashboardItems = remember(...) {
    buildList {
        // 1. Storage meter (always)
        if (allApps.isNotEmpty()) { add(DashboardItem.StorageMeter(...)) }
        // 2. Permission warning OR Rarely Used SmartCard
        if (!hasUsagePermission) { add(DashboardItem.PermissionWarning()) }
        else { add(DashboardItem.SmartCard(\"Rarely Used Apps\", ...)) }
        // 3. Large Apps SmartCard
        add(DashboardItem.SmartCard(\"Large Apps\", ...))
        // 4. All Apps Header
        add(DashboardItem.AllAppsHeader(isSearchActive))
        // 5. Search bar (only if search active)
        if (isSearchActive) { add(DashboardItem.SearchBar(searchQuery)) }
        // 6. App items
        filteredApps.forEach { add(DashboardItem.AppItem(app, isSelected)) }
    }
}
```

### Current search toggle handler (passed to adapter):
```kotlin
onSearchToggle = {
    if (isSearchActive) {
        isSearchActive = false
        searchQuery = \"\"
    } else {
        isSearchActive = true
    }
}
```

---

## Files to Modify

| File | Path | What Changes |
|------|------|-------------|
| **DiscoveryScreen.kt** | `presentation/screens/dashboard/DiscoveryScreen.kt` | Major changes to `DiscoveryDashboard` composable — add pinned Compose search bar, modify `dashboardItems` builder, add animations |
| **DiscoveryDashboardAdapter.kt** | `presentation/screens/dashboard/DiscoveryDashboardAdapter.kt` | Minor — may remove `VIEW_TYPE_SEARCH_BAR` handling since search bar moves to Compose |
| **item_search_bar.xml** | `res/layout/item_search_bar.xml` | Can be deleted or kept as unused — the search bar will now be a Compose element |
| **item_all_apps_header.xml** | `res/layout/item_all_apps_header.xml` | No change needed (or minor: icon swap logic stays in adapter) |

---

## Implementation Plan

### Step 1: Move the search bar from RecyclerView to a pinned Compose element

**In `DiscoveryDashboard` composable** (`DiscoveryScreen.kt`):

The current layout is:
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    AndroidView(modifier = Modifier.fillMaxSize(), ...) // RecyclerView
    // Floating action buttons at bottom
}
```

Change it to a `Column` + `Box` structure where the search bar sits ABOVE the RecyclerView:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ---- PINNED SEARCH BAR (Compose) ----
        AnimatedVisibility(
            visible = isSearchActive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        ) {
            PinnedSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = \"\"  // First tap: clear text
                    } else {
                        isSearchActive = false  // Second tap: exit search
                        searchQuery = \"\"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ---- RECYCLERVIEW ----
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),  // Takes remaining space below search bar
            factory = { context ->
                RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = DiscoveryDashboardAdapter(...)
                    // Enable item animations for smooth add/remove
                    itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                        addDuration = 250
                        removeDuration = 200
                        moveDuration = 250
                    }
                    val dp12 = (12 * context.resources.displayMetrics.density).toInt()
                    setPadding(dp12, dp12, dp12, dp12)
                    clipToPadding = false
                }
            },
            update = { recyclerView ->
                val adapter = recyclerView.adapter as DiscoveryDashboardAdapter
                adapter.themeColors = themeColors
                adapter.submitList(dashboardItems) {
                    // After list update, scroll to top when entering search mode
                    if (isSearchActive) {
                        recyclerView.scrollToPosition(0)
                    }
                }
                val dp12 = (12 * recyclerView.context.resources.displayMetrics.density).toInt()
                val dp80 = (80 * recyclerView.context.resources.displayMetrics.density).toInt()
                recyclerView.setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)
            }
        )
    }

    // Floating action buttons (unchanged)
    if (selectedApps.isNotEmpty()) { ... }
}
```

### Step 2: Create the `PinnedSearchBar` composable

Add this new composable in `DiscoveryScreen.kt` (or a separate file if you prefer):

```kotlin
@Composable
fun PinnedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus and show keyboard when this composable enters composition
    LaunchedEffect(Unit) {
        // Small delay to let the animation start before grabbing focus
        kotlinx.coroutines.delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                \"Search apps...\",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = \"Search\",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = if (query.isNotEmpty()) Icons.Default.Clear else Icons.Default.Close,
                    contentDescription = if (query.isNotEmpty()) \"Clear text\" else \"Close search\",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}
```

**Required imports to add at top of `DiscoveryScreen.kt`:**
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
```

### Step 3: Modify the `dashboardItems` builder to exclude top cards during search

**Current logic** (lines 255-297 of DiscoveryScreen.kt) builds items unconditionally. Wrap the top cards in a `!isSearchActive` check:

```kotlin
val dashboardItems = remember(unusedApps, largeApps, filteredApps, hasUsagePermission, isSearchActive, searchQuery, selectedApps) {
    buildList {
        if (!isSearchActive) {
            // === TOP CARDS: Only shown when NOT searching ===

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
                    icon = \"📦\",
                    title = \"Rarely Used Apps\",
                    description = \"${unusedApps.size} apps • ${bytesToMB(unusedApps.sumOf { it.apkSizeBytes })} MB\",
                    viewState = DiscoveryViewState.RARELY_USED
                ))
            }

            // Large apps card
            add(DashboardItem.SmartCard(
                icon = \"💾\",
                title = \"Large Apps\",
                description = \"${largeApps.size} apps • ${bytesToMB(largeApps.sumOf { it.apkSizeBytes })} MB\",
                viewState = DiscoveryViewState.LARGE_APPS
            ))
        }

        // All apps header — ALWAYS shown (but icon changes based on search state)
        add(DashboardItem.AllAppsHeader(isSearchActive))

        // DO NOT add DashboardItem.SearchBar here anymore
        // The search bar is now a pinned Compose element above the RecyclerView

        // App items
        filteredApps.forEach { app ->
            add(DashboardItem.AppItem(app, app.packageId in selectedApps))
        }
    }
}
```

**Key change:** When `isSearchActive == true`, the list starts directly with `AllAppsHeader` followed by `AppItem`s. The `DashboardItem.SearchBar` type is no longer added to the list at all.

### Step 4: Update the `onSearchToggle` callback

The search toggle now only needs to set `isSearchActive = true`. The close/cancel logic is handled by the `PinnedSearchBar`'s `onClose` callback:

```kotlin
onSearchToggle = {
    if (isSearchActive) {
        // Exit search — clear everything
        isSearchActive = false
        searchQuery = \"\"
    } else {
        // Enter search mode
        isSearchActive = true
    }
},
```

This remains the same. The `AllAppsHeader` search icon button calls `onSearchToggle` to toggle. When in search mode, the header's icon changes to a close icon (already handled by the adapter's `AllAppsHeaderViewHolder.bind`).

### Step 5: Update `BackHandler` for two-step cancel

The existing `BackHandler` handles exiting search on back press. Update it to support the two-step cancel (clear text first, then exit):

```kotlin
BackHandler(enabled = isSearchActive) {
    if (searchQuery.isNotEmpty()) {
        searchQuery = \"\"  // First back press: clear text
    } else {
        isSearchActive = false  // Second back press: exit search
        searchQuery = \"\"
    }
}
```

### Step 6: Dismiss keyboard when exiting search

Add a `LaunchedEffect` that watches `isSearchActive` and dismisses the keyboard when search is deactivated:

```kotlin
val keyboardController = LocalSoftwareKeyboardController.current

LaunchedEffect(isSearchActive) {
    if (!isSearchActive) {
        keyboardController?.hide()
    }
}
```

Place this inside the `DiscoveryDashboard` composable, before the `Box` layout.

### Step 7 (Optional cleanup): Remove `VIEW_TYPE_SEARCH_BAR` from the adapter

Since the search bar is no longer a RecyclerView item, you can optionally remove:

1. `DashboardItem.SearchBar` data class from `DiscoveryDashboardAdapter.kt`
2. `VIEW_TYPE_SEARCH_BAR` constant
3. `SearchBarViewHolder` inner class
4. The corresponding cases in `getItemViewType`, `onCreateViewHolder`, `onBindViewHolder`
5. The `item_search_bar.xml` layout file

However, this is optional — leaving them in place doesn't cause harm; the `SearchBar` item type simply won't be added to the list anymore.

---

## Complete Modified `DiscoveryDashboard` Composable

Here is the full replacement for the `DiscoveryDashboard` function (lines 223-383 of the current `DiscoveryScreen.kt`):

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
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
    var searchQuery by remember { mutableStateOf(\"\") }

    // Keyboard controller for dismissing keyboard
    val keyboardController = LocalSoftwareKeyboardController.current

    // Dismiss keyboard when exiting search
    LaunchedEffect(isSearchActive) {
        if (!isSearchActive) {
            keyboardController?.hide()
        }
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

    // Filter apps based on search query
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Build the list of items for RecyclerView
    val dashboardItems = remember(unusedApps, largeApps, filteredApps, hasUsagePermission, isSearchActive, searchQuery, selectedApps) {
        buildList {
            // TOP CARDS: Only shown when NOT in search mode
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
                        icon = \"\uD83D\uDCE6\",
                        title = \"Rarely Used Apps\",
                        description = \"${unusedApps.size} apps \u2022 ${bytesToMB(unusedApps.sumOf { it.apkSizeBytes })} MB\",
                        viewState = DiscoveryViewState.RARELY_USED
                    ))
                }

                // Large apps card
                add(DashboardItem.SmartCard(
                    icon = \"\uD83D\uDCBE\",
                    title = \"Large Apps\",
                    description = \"${largeApps.size} apps \u2022 ${bytesToMB(largeApps.sumOf { it.apkSizeBytes })} MB\",
                    viewState = DiscoveryViewState.LARGE_APPS
                ))
            }

            // All apps header (always shown — icon toggles between search/close)
            add(DashboardItem.AllAppsHeader(isSearchActive))

            // NO DashboardItem.SearchBar added here — search bar is now pinned in Compose

            // App items
            filteredApps.forEach { app ->
                add(DashboardItem.AppItem(app, app.packageId in selectedApps))
            }
        }
    }

    // Handle back press: two-step cancel (clear text first, then exit search)
    BackHandler(enabled = isSearchActive) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = \"\"
        } else {
            isSearchActive = false
            searchQuery = \"\"
        }
    }

    // Keep a reference to the RecyclerView for imperative scroll control
    var recyclerViewRef by remember { mutableStateOf<RecyclerView?>(null) }

    // Scroll to top when entering search mode
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            // Small delay to let the list update propagate
            kotlinx.coroutines.delay(100)
            recyclerViewRef?.smoothScrollToPosition(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ═══════════════════════════════════════════════════
            // PINNED SEARCH BAR (Compose element, above RecyclerView)
            // ═══════════════════════════════════════════════════
            AnimatedVisibility(
                visible = isSearchActive,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> -fullHeight },
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 250))
            ) {
                PinnedSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = \"\"  // First tap: just clear the text
                        } else {
                            isSearchActive = false  // Second tap: exit search entirely
                            searchQuery = \"\"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ═══════════════════════════════════════════════════
            // RECYCLERVIEW (takes remaining space)
            // ═══════════════════════════════════════════════════
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                                    searchQuery = \"\"
                                } else {
                                    isSearchActive = true
                                }
                            },
                            onSearchQueryChange = { query ->
                                searchQuery = query
                            },
                            themeColors = themeColors
                        )
                        // Enable item change animations for smooth card removal/addition
                        itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                            addDuration = 250
                            removeDuration = 200
                            moveDuration = 250
                        }
                        val dp12 = (12 * context.resources.displayMetrics.density).toInt()
                        val dp80 = (80 * context.resources.displayMetrics.density).toInt()
                        setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)
                        clipToPadding = false
                        recyclerViewRef = this  // Store reference for imperative scroll
                    }
                },
                update = { recyclerView ->
                    val adapter = recyclerView.adapter as DiscoveryDashboardAdapter
                    adapter.themeColors = themeColors
                    adapter.submitList(dashboardItems)

                    val dp12 = (12 * recyclerView.context.resources.displayMetrics.density).toInt()
                    val dp80 = (80 * recyclerView.context.resources.displayMetrics.density).toInt()
                    recyclerView.setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)
                }
            )
        }

        // ═══════════════════════════════════════════════════
        // FLOATING ACTION BUTTONS (unchanged)
        // ═══════════════════════════════════════════════════
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
                    Text(\"Uninstall Only\", textAlign = TextAlign.Center)
                }
                Button(
                    onClick = {
                        val selected = selectedApps.toSet()
                        selectedApps = emptySet()
                        onBatchUninstall(selected)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(\"Archive & Uninstall\", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun PinnedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus and show keyboard when search bar appears
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)  // Let animation start first
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                \"Search apps...\",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = \"Search\",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = if (query.isNotEmpty()) Icons.Default.Clear else Icons.Default.ArrowBack,
                    contentDescription = if (query.isNotEmpty()) \"Clear text\" else \"Close search\",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier.focusRequester(focusRequester),
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}
```

---

## Additional Imports Required at Top of DiscoveryScreen.kt

Add these imports (some may already exist — deduplicate):

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.recyclerview.widget.RecyclerView
```

---

## Animation Flow Diagram

```
┌──────────────────────────────────────────────────────────┐
│  STATE: isSearchActive = false (Dashboard Mode)          │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  [StorageMeter Card]                                │ │
│  │  135 MB  •  Waste Score: 72%                        │ │
│  │  ████████████░░░░░░░░░░                             │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │  [SmartCard: Rarely Used Apps]          [Review]    │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │  [SmartCard: Large Apps]                [Review]    │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │  All Apps                                 (🔍)      │ │  ← User taps this
│  ├─────────────────────────────────────────────────────┤ │
│  │  ☐ [icon] App Name A         45 MB • 3 days ago    │ │
│  │  ☐ [icon] App Name B         12 MB • Never used    │ │
│  │  ☐ [icon] App Name C         8 MB  • Today         │ │
│  │  ...                                                │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘

        ↓  User taps 🔍  ↓  (~300ms transition)

    1. Top cards (StorageMeter, SmartCards) removed from list
       → RecyclerView's DefaultItemAnimator fades them out
    2. Compose search bar slides in from top (AnimatedVisibility)
    3. RecyclerView smoothScrollToPosition(0)
    4. Keyboard opens, search bar gets focus

┌──────────────────────────────────────────────────────────┐
│  STATE: isSearchActive = true (Search Mode)              │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  🔍 Search apps...                            (←)  │ │  ← PINNED (Compose)
│  └─────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  All Apps                                 (✕)       │ │  ← RecyclerView starts here
│  ├─────────────────────────────────────────────────────┤ │
│  │  ☐ [icon] App Name A         45 MB • 3 days ago    │ │
│  │  ☐ [icon] App Name B         12 MB • Never used    │ │
│  │  ☐ [icon] App Name C         8 MB  • Today         │ │
│  │  ...                                                │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  [KEYBOARD OPEN]                                         │
└──────────────────────────────────────────────────────────┘

    Scrolling the app list → search bar stays pinned at top
    Typing filters the app list in real-time
    
    Cancel flow:
      - Text present + tap ← : clears text only
      - Empty text + tap ← : exits search mode entirely
      - Back button: same two-step logic
```

---

## Edge Cases to Handle

1. **Keyboard insets**: Ensure the RecyclerView's content is not hidden behind the keyboard. The `Scaffold` from `DashboardScreen.kt` should already handle `WindowInsets` via `paddingValues`. If not, add `imePadding()` to the `Column` modifier:
   ```kotlin
   Column(modifier = Modifier.fillMaxSize().imePadding()) { ... }
   ```

2. **RecyclerView reference lifecycle**: The `recyclerViewRef` is set in `factory` and used in `LaunchedEffect`. Since the `AndroidView` factory only runs once per composition, the ref should be stable. But add a null check before using:
   ```kotlin
   recyclerViewRef?.smoothScrollToPosition(0)
   ```

3. **Empty search results**: When `filteredApps` is empty during search, the RecyclerView shows only the `AllAppsHeader`. Consider adding an empty state message. You can add a simple `DashboardItem` type for this, or overlay a Compose `Text`:
   ```kotlin
   if (isSearchActive && filteredApps.isEmpty() && searchQuery.isNotEmpty()) {
       Box(
           modifier = Modifier.fillMaxSize(),
           contentAlignment = Alignment.Center
       ) {
           Text(
               \"No apps match \\"$searchQuery\\"\",
               color = MaterialTheme.colorScheme.onSurfaceVariant,
               style = MaterialTheme.typography.bodyLarge
           )
       }
   }
   ```

4. **Selection state during search**: The `selectedApps` state persists across search/non-search mode. This is intentional — users can search, select, exit search, and still see their selections. The floating action buttons will appear when `selectedApps.isNotEmpty()`.

5. **Configuration changes**: The `remember` state (isSearchActive, searchQuery) will be lost on config change (rotation). If you want persistence, use `rememberSaveable`:
   ```kotlin
   var isSearchActive by rememberSaveable { mutableStateOf(false) }
   var searchQuery by rememberSaveable { mutableStateOf(\"\") }
   ```

---

## Performance Notes

- **No new recomposition overhead**: The `PinnedSearchBar` only recomposes when `query` changes (which is the minimum necessary for search). The `AnimatedVisibility` wrapper prevents unnecessary composition when hidden.

- **RecyclerView DiffUtil handles card removal efficiently**: When top cards are removed from `dashboardItems`, the existing `DashboardItemDiffCallback` correctly identifies which items were removed and which moved. The `DefaultItemAnimator` provides the fade/slide animation for free.

- **No additional RecyclerView layout passes**: The search bar is entirely in Compose, so the RecyclerView doesn't need to lay out or measure a sticky header.

---

## Testing Checklist

- [ ] Tap search icon → top cards animate out, search bar slides in from top, keyboard opens
- [ ] Search bar stays pinned when scrolling the app list up and down
- [ ] Type a query → app list filters in real-time
- [ ] Tap X with text present → text clears, search stays active, keyboard stays open
- [ ] Tap X with empty text → search exits, top cards return, keyboard dismisses
- [ ] Press hardware back with text → text clears first
- [ ] Press hardware back with empty text → exits search
- [ ] Tap search icon in \"All Apps\" header (now showing close icon) → exits search
- [ ] Select apps during search → floating action buttons appear
- [ ] Exit search → selections persist, floating buttons still visible
- [ ] Rotate device during search → search state preserved (if using rememberSaveable)
- [ ] Dark mode: search bar uses correct colors
- [ ] Dynamic colors (Android 12+): search bar follows wallpaper palette
- [ ] No scroll jank: RecyclerView performance unchanged

---

## Summary of Changes

| File | Change | Lines Affected |
|------|--------|---------------|
| `DiscoveryScreen.kt` | Replace `DiscoveryDashboard` composable with new version that has pinned Compose search bar + `AnimatedVisibility` + modified `dashboardItems` builder | ~Lines 223-383 (full function replacement) |
| `DiscoveryScreen.kt` | Add `PinnedSearchBar` composable | New function (~40 lines) |
| `DiscoveryScreen.kt` | Add new imports for animation, focus, keyboard | ~12 new import lines |
| `DiscoveryDashboardAdapter.kt` | Optional: Remove `SearchBarViewHolder`, `VIEW_TYPE_SEARCH_BAR` | ~30 lines removed (optional cleanup) |
| `item_search_bar.xml` | Optional: Delete file (no longer used) | Entire file (optional) |

**Total: ~1 file significantly changed, ~1 file optionally cleaned up. No new files required.**

---

## Why This Approach (vs Alternatives)

| Approach | Pros | Cons | Verdict |
|----------|------|------|---------|
| **Compose pinned bar + list manipulation** (this approach) | Clean separation; Compose handles pinning naturally; RecyclerView DiffUtil animates card removal for free; keyboard control via Compose APIs | Requires storing RecyclerView ref for scroll control | **Best fit** for this hybrid Compose+RecyclerView architecture |
| RecyclerView sticky header (ItemDecoration) | Pure native solution | Complex to implement; doesn't integrate well with Compose state; manual keyboard management | Over-engineered for this case |
| CoordinatorLayout + AppBarLayout | Standard Android pattern for collapsing headers | Would require major restructuring; conflicts with Compose Scaffold | Wrong architecture |
| Replace RecyclerView with LazyColumn | Pure Compose; trivial sticky header with `stickyHeader{}` | Already tried and rejected due to scroll jank on mid-range devices (see jankfix.md) | Not viable |
"
