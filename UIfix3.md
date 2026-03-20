

## Prompt for Claude Sonnet 4.5

You are fixing the search interaction UX on the Decluttr Android app's **Discovery Dashboard** page. The Discovery page uses a **Compose wrapper** (`DiscoveryDashboard`) around a native **RecyclerView** (`AndroidView`) for scroll performance. The search bar is currently rendered as a regular RecyclerView item — it scrolls away with the list and does not pin to the top. This must change.

**Critical constraint: Do NOT use Compose UI elements (no `AnimatedVisibility`, no `OutlinedTextField`, no Compose `Column`/`Box` overlays) for this fix.** The entire search bar and animation must be implemented with native Android Views inside the `AndroidView` block. Compose is only used for state management (which it already does). All visual elements must be native Views.

---

## Desired Behavior (UX Spec)

### When the user taps the search icon button (in the \"All Apps\" header):

1. **Top cards auto-scroll upward and exit the viewport** — the StorageMeter card, PermissionWarning/SmartCard(s), and the \"Large Apps\" SmartCard smoothly animate upward and disappear. They are removed from the RecyclerView's data list so they don't occupy space. RecyclerView's `DefaultItemAnimator` handles the removal animation.

2. **Search bar locks/pins to the top of the screen** — a native `EditText` inside a `MaterialCardView`, positioned ABOVE the RecyclerView in a parent `LinearLayout`, slides into view. It does NOT scroll with the list. It is a **fixed native View**, not a RecyclerView item.

3. **Keyboard opens automatically** — the `EditText` receives focus via `requestFocus()` and the soft keyboard is shown via `InputMethodManager`.

4. **App list shows below the pinned search bar** — only the \"All Apps\" header + filtered app items appear in the RecyclerView.

5. **Seamless animation** — the transition from dashboard mode to search mode should feel fluid: cards are removed (RecyclerView animates), search bar slides down from above (`translationY` animation), all within ~300ms.

### When the user cancels/exits search:

1. **Two-step cancel logic:**
   - If text is present and user taps X → **clear the text only** (stay in search mode).
   - If text is empty and user taps X → **exit search mode entirely**.
   - Hardware back button follows the same two-step logic.

2. **Search bar unpins and animates out** — slides back up via `translationY` animation, then `visibility = GONE`.
3. **Top cards return** — StorageMeter, SmartCards re-appear in the RecyclerView list (DiffUtil triggers add animation).
4. **Keyboard dismisses** via `InputMethodManager.hideSoftInputFromWindow()`.
5. **RecyclerView scrolls back to position 0**.

### Important constraints:
- The search bar must stay pinned/locked at the top **as long as search is active** — scrolling the app list must NOT move the search bar.
- The search bar only \"unlocks\" (disappears) when the user explicitly cancels.

---

## Current Architecture (Do NOT change the scrolling architecture)

```
DashboardScreen.kt (Compose Scaffold)
  └── DiscoveryScreen.kt (Compose, handles view states)
        └── DiscoveryDashboard (Compose @Composable — state management only)
              ├── State: isSearchActive, searchQuery, selectedApps
              ├── dashboardItems: List<DashboardItem> (built with remember)
              ├── BackHandler (exits search on back press)
              │
              ├── Box(fillMaxSize)
              │     └── AndroidView { RecyclerView }   ← CHANGE THIS to { LinearLayout(RecyclerView + search bar) }
              │           └── DiscoveryDashboardAdapter (heterogeneous ListAdapter)
              │                 ├── VIEW_TYPE_STORAGE_METER   → item_storage_meter.xml
              │                 ├── VIEW_TYPE_PERMISSION_WARNING → item_permission_warning.xml
              │                 ├── VIEW_TYPE_SMART_CARD       → item_smart_card.xml
              │                 ├── VIEW_TYPE_ALL_APPS_HEADER  → item_all_apps_header.xml
              │                 ├── VIEW_TYPE_SEARCH_BAR       → item_search_bar.xml  (will no longer be used as RV item)
              │                 └── VIEW_TYPE_APP_ITEM         → item_discovery_app.xml
              │
              └── Floating action buttons (when selectedApps.isNotEmpty())
```

### Key state variables in `DiscoveryDashboard` (Compose side — unchanged):
```kotlin
var isSearchActive by remember { mutableStateOf(false) }
var searchQuery by remember { mutableStateOf(\"\") }
var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
```

### Current `dashboardItems` builder (lines 255-297 of DiscoveryScreen.kt):
```kotlin
val dashboardItems = remember(...) {
    buildList {
        // 1. Storage meter
        if (allApps.isNotEmpty()) { add(DashboardItem.StorageMeter(...)) }
        // 2. Permission warning OR Rarely Used SmartCard
        if (!hasUsagePermission) { add(DashboardItem.PermissionWarning()) }
        else { add(DashboardItem.SmartCard(\"Rarely Used Apps\", ...)) }
        // 3. Large Apps SmartCard
        add(DashboardItem.SmartCard(\"Large Apps\", ...))
        // 4. All Apps Header
        add(DashboardItem.AllAppsHeader(isSearchActive))
        // 5. Search bar (only if search active) ← REMOVE THIS
        if (isSearchActive) { add(DashboardItem.SearchBar(searchQuery)) }
        // 6. App items
        filteredApps.forEach { add(DashboardItem.AppItem(app, isSelected)) }
    }
}
```

---

## Files to Modify

| File | Path | What Changes |
|------|------|-------------|
| **DiscoveryScreen.kt** | `presentation/screens/dashboard/DiscoveryScreen.kt` | Modify `DiscoveryDashboard`: change `AndroidView` factory to create `LinearLayout` container (pinned search bar + RecyclerView), modify `dashboardItems` builder, add `update` block logic for search bar visibility |
| **DiscoveryDashboardAdapter.kt** | `presentation/screens/dashboard/DiscoveryDashboardAdapter.kt` | Optional cleanup: remove `VIEW_TYPE_SEARCH_BAR` / `SearchBarViewHolder` |
| **item_search_bar.xml** | `res/layout/item_search_bar.xml` | No change needed — we reuse this same layout, but inflate it as a standalone view instead of a RecyclerView item |

---

## Implementation Plan

### Step 1: Modify `dashboardItems` builder — exclude top cards during search, remove SearchBar item

In `DiscoveryDashboard` composable, modify the `dashboardItems` `buildList` block:

```kotlin
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

        // All apps header — ALWAYS shown (icon toggles search/close)
        add(DashboardItem.AllAppsHeader(isSearchActive))

        // *** DO NOT add DashboardItem.SearchBar here anymore ***
        // The search bar is now a pinned native View above the RecyclerView

        // App items
        filteredApps.forEach { app ->
            add(DashboardItem.AppItem(app, app.packageId in selectedApps))
        }
    }
}
```

**Key change:** When `isSearchActive == true`, the list contains only `AllAppsHeader` + `AppItem`s. No `StorageMeter`, `SmartCard`, `PermissionWarning`, or `SearchBar` items.

### Step 2: Change `AndroidView` factory to create a parent `LinearLayout` with pinned search bar + RecyclerView

**Current factory** creates just a `RecyclerView`:
```kotlin
AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { context ->
        RecyclerView(context).apply { ... }
    },
    update = { recyclerView -> ... }
)
```

**New factory** creates a `LinearLayout` containing:
1. A pinned search bar view (inflated from `item_search_bar.xml`) — initially `GONE`
2. The `RecyclerView`

```kotlin
AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { context ->
        // ══════════════════════════════════════════════════════
        // ROOT CONTAINER: LinearLayout (vertical)
        //   ├── Pinned Search Bar (item_search_bar.xml) — GONE initially
        //   └── RecyclerView (full list)
        // ══════════════════════════════════════════════════════
        val density = context.resources.displayMetrics.density

        // --- Inflate the pinned search bar from existing layout ---
        val searchBarView = android.view.LayoutInflater.from(context)
            .inflate(R.layout.item_search_bar, null, false)
        searchBarView.visibility = android.view.View.GONE
        searchBarView.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            val dp8 = (8 * density).toInt()
            val dp12 = (12 * density).toInt()
            setMargins(dp12, dp8, dp12, dp8)
        }

        // Tag the search bar subviews for easy access in update block
        val searchEditText = searchBarView.findViewById<android.widget.EditText>(R.id.search_edit_text)
        val clearButton = searchBarView.findViewById<android.widget.ImageView>(R.id.clear_button)

        // Clear button logic: two-step cancel
        clearButton.setOnClickListener {
            if (searchEditText.text.isNotEmpty()) {
                searchEditText.setText(\"\")  // Step 1: clear text
            } else {
                // Step 2: exit search — handled via onSearchToggle callback
                // (set in update block since it captures Compose state)
            }
        }

        // Text change listener — fires onSearchQueryChange
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString()
                clearButton.visibility = if (query.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                // onSearchQueryChange will be called from the update block's TextWatcher
            }
        })

        // --- Create RecyclerView ---
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
            // Enable item animations for smooth card add/remove
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
                0,  // 0 height + weight = fill remaining space
                1f  // weight
            )
        }

        // --- Root container ---
        android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(searchBarView)
            addView(recyclerView)
            // Store references via tags for the update block
            setTag(R.id.search_edit_text, searchBarView)  // reuse existing ID as tag key
            setTag(R.id.clear_button, recyclerView)        // reuse existing ID as tag key
        }
    },
    update = { rootLayout ->
        // Retrieve child views from tags
        val searchBarView = rootLayout.getTag(R.id.search_edit_text) as android.view.View
        val recyclerView = rootLayout.getTag(R.id.clear_button) as RecyclerView
        val searchEditText = searchBarView.findViewById<android.widget.EditText>(R.id.search_edit_text)
        val clearButton = searchBarView.findViewById<android.widget.ImageView>(R.id.clear_button)
        val density = rootLayout.context.resources.displayMetrics.density
        val imm = rootLayout.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager

        // --- Update adapter ---
        val adapter = recyclerView.adapter as DiscoveryDashboardAdapter
        adapter.themeColors = themeColors
        adapter.submitList(dashboardItems) {
            // After DiffUtil processes, scroll to top if entering search
            if (isSearchActive) {
                recyclerView.scrollToPosition(0)
            }
        }

        // --- Update RecyclerView bottom padding ---
        val dp12 = (12 * density).toInt()
        val dp80 = (80 * density).toInt()
        recyclerView.setPadding(dp12, dp12, dp12, if (selectedApps.isNotEmpty()) dp80 else dp12)

        // --- Animate search bar visibility ---
        if (isSearchActive && searchBarView.visibility != android.view.View.VISIBLE) {
            // SHOW search bar: slide down from above
            searchBarView.visibility = android.view.View.VISIBLE
            searchBarView.translationY = -searchBarView.height.toFloat().coerceAtLeast(150f)
            searchBarView.alpha = 0f
            searchBarView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    // Request focus and show keyboard after animation
                    searchEditText.requestFocus()
                    imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                .start()

        } else if (!isSearchActive && searchBarView.visibility == android.view.View.VISIBLE) {
            // HIDE search bar: slide up and fade out
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
            searchEditText.setText(\"\")
            searchEditText.clearFocus()
            searchBarView.animate()
                .translationY(-searchBarView.height.toFloat())
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

        // --- Sync EditText text with Compose state ---
        // Only update if different (avoid infinite loop with TextWatcher)
        if (searchEditText.text.toString() != searchQuery) {
            searchEditText.setText(searchQuery)
            searchEditText.setSelection(searchQuery.length)
        }

        // --- Update clear button click to call onSearchToggle when text is empty ---
        clearButton.setOnClickListener {
            if (searchEditText.text.isNotEmpty()) {
                searchEditText.setText(\"\")  // Clear text first
            } else {
                isSearchActive = false  // Exit search
                searchQuery = \"\"
            }
        }
    }
)
```

### Step 3: Handle the TextWatcher → Compose state bridge

The `TextWatcher` on the `EditText` needs to call back into Compose state. The cleanest way: set a new `TextWatcher` in the `update` block each time (removing the old one). But since `update` runs frequently, use a wrapper approach:

**Create a small callback holder class** (add inside `DiscoveryScreen.kt` or as a top-level internal class):

```kotlin
/**
 * Mutable callback reference that the TextWatcher can hold.
 * Updated from the Compose update block without removing/re-adding the TextWatcher.
 */
internal class SearchQueryCallback {
    var onQueryChange: ((String) -> Unit)? = null
}
```

**In the `factory` block**, create and attach this callback:

```kotlin
val queryCallback = SearchQueryCallback()

searchEditText.addTextChangedListener(object : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {
        val query = s.toString()
        clearButton.visibility = if (query.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        queryCallback.onQueryChange?.invoke(query)
    }
})

// Store the callback on the root layout for the update block to access
rootLayout.setTag(R.id.app_checkbox, queryCallback)  // use any unique existing ID as tag key
```

**In the `update` block**, update the callback reference:

```kotlin
val queryCallback = rootLayout.getTag(R.id.app_checkbox) as SearchQueryCallback
queryCallback.onQueryChange = { query ->
    searchQuery = query
}
```

This avoids adding/removing `TextWatcher`s on every recomposition and prevents infinite loops.

### Step 4: Fix tag key conflicts — use dedicated IDs

Using existing resource IDs as tag keys (`R.id.search_edit_text`, `R.id.clear_button`, `R.id.app_checkbox`) works but is fragile. Better approach: define tag key constants as unique integers:

```kotlin
// Add to DiscoveryScreen.kt or a constants file
private const val TAG_SEARCH_BAR_VIEW = 0x7F_0E_FF_01
private const val TAG_RECYCLER_VIEW = 0x7F_0E_FF_02
private const val TAG_QUERY_CALLBACK = 0x7F_0E_FF_03
```

Or even simpler — add IDs to `res/values/ids.xml`:

**Create `app/src/main/res/values/ids.xml`:**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<resources>
    <item name=\"tag_search_bar_view\" type=\"id\" />
    <item name=\"tag_recycler_view\" type=\"id\" />
    <item name=\"tag_query_callback\" type=\"id\" />
</resources>
```

Then use `R.id.tag_search_bar_view`, `R.id.tag_recycler_view`, `R.id.tag_query_callback` as tag keys.

### Step 5: Update `BackHandler` for two-step cancel

In the Compose `DiscoveryDashboard` function, the `BackHandler` already exists. Update it:

```kotlin
BackHandler(enabled = isSearchActive) {
    if (searchQuery.isNotEmpty()) {
        searchQuery = \"\"  // First back press: clear text only
    } else {
        isSearchActive = false  // Second back press: exit search
        searchQuery = \"\"
    }
}
```

### Step 6 (Optional cleanup): Remove SearchBar from adapter

Since `DashboardItem.SearchBar` is no longer added to `dashboardItems`, you can clean up:

**In `DiscoveryDashboardAdapter.kt`:**
1. Remove `DashboardItem.SearchBar` data class
2. Remove `VIEW_TYPE_SEARCH_BAR` constant
3. Remove `SearchBarViewHolder` inner class
4. Remove corresponding branches in `getItemViewType`, `onCreateViewHolder`, `onBindViewHolder`
5. Remove corresponding branch in `DashboardItemDiffCallback`

**Optional:** Delete `item_search_bar.xml` if you're inflating the pinned search bar programmatically instead. Or keep it — it's reused as the pinned bar's layout via `LayoutInflater.inflate(R.layout.item_search_bar, ...)`.

---

## Complete Modified `DiscoveryDashboard` Composable

Here is the full replacement for the `DiscoveryDashboard` function (replaces lines 223-383 of the current `DiscoveryScreen.kt`):

```kotlin
/**
 * Mutable callback reference bridging native TextWatcher → Compose state.
 * Stored as a View tag on the root layout; the update block refreshes the lambda.
 */
internal class SearchQueryCallback {
    var onQueryChange: ((String) -> Unit)? = null
    var onExitSearch: (() -> Unit)? = null
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
    var searchQuery by remember { mutableStateOf(\"\") }

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
                if (allApps.isNotEmpty()) {
                    val totalSize = allApps.sumOf { it.apkSizeBytes }
                    val wasteSize = unusedApps.sumOf { it.apkSizeBytes }
                    val percentage = if (totalSize > 0) ((wasteSize.toFloat() / totalSize.toFloat()) * 100).roundToInt() else 0
                    add(DashboardItem.StorageMeter(wasteSize, totalSize, percentage))
                }
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
                add(DashboardItem.SmartCard(
                    icon = \"💾\",
                    title = \"Large Apps\",
                    description = \"${largeApps.size} apps • ${bytesToMB(largeApps.sumOf { it.apkSizeBytes })} MB\",
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
            searchQuery = \"\"
        } else {
            isSearchActive = false
            searchQuery = \"\"
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
                    .inflate(R.layout.item_search_bar, null, false)
                searchBarView.visibility = android.view.View.GONE
                searchBarView.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val dp8 = (8 * density).toInt()
                    val dp12 = (12 * density).toInt()
                    setMargins(dp12, dp8, dp12, dp8)
                }

                val searchEditText = searchBarView.findViewById<android.widget.EditText>(R.id.search_edit_text)
                val clearButton = searchBarView.findViewById<android.widget.ImageView>(R.id.clear_button)

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
                        searchEditText.setText(\"\")
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

                    // Store references via tags for the update block
                    tag = Triple(searchBarView, recyclerView, queryCallback)
                }
            },
            update = { rootLayout ->
                val (searchBarView, recyclerView, queryCallback) =
                    rootLayout.tag as Triple<android.view.View, RecyclerView, SearchQueryCallback>

                val searchEditText = searchBarView.findViewById<android.widget.EditText>(R.id.search_edit_text)
                val clearButton = searchBarView.findViewById<android.widget.ImageView>(R.id.clear_button)
                val imm = rootLayout.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                val density = rootLayout.context.resources.displayMetrics.density

                // ── Bridge callbacks to current Compose state ──
                queryCallback.onQueryChange = { query -> searchQuery = query }
                queryCallback.onExitSearch = {
                    isSearchActive = false
                    searchQuery = \"\"
                }

                // ── Update clear button click handler ──
                clearButton.setOnClickListener {
                    if (searchEditText.text.isNotEmpty()) {
                        searchEditText.setText(\"\")
                    } else {
                        isSearchActive = false
                        searchQuery = \"\"
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
                    searchEditText.setText(\"\")
                    searchEditText.clearFocus()
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
```

---

## Animation Flow Diagram

```
┌──────────────────────────────────────────────────────────┐
│  STATE: isSearchActive = false (Dashboard Mode)          │
│                                                          │
│  [Pinned search bar — GONE, invisible, 0 height]        │
│                                                          │
│  ┌─ RecyclerView ──────────────────────────────────────┐ │
│  │  [StorageMeter Card]                                │ │
│  │  135 MB  •  Waste Score: 72%                        │ │
│  │  ████████████░░░░░░░░░░                             │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │  [SmartCard: Rarely Used Apps]          [Review]    │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │  [SmartCard: Large Apps]                [Review]    │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │  All Apps                                 (🔍)      │ │  ← tap
│  ├─────────────────────────────────────────────────────┤ │
│  │  ☐ [icon] App Name A         45 MB • 3 days ago    │ │
│  │  ☐ [icon] App Name B         12 MB • Never used    │ │
│  │  ...                                                │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘

        ↓  User taps 🔍  ↓  (~300ms)

    1. dashboardItems rebuilt WITHOUT StorageMeter/SmartCards
       → DiffUtil detects removals → DefaultItemAnimator fades them out
    2. Pinned search bar: GONE → VISIBLE, translationY slides from -200dp to 0
    3. RecyclerView.scrollToPosition(0)
    4. EditText.requestFocus() + IMM.showSoftInput()

┌──────────────────────────────────────────────────────────┐
│  STATE: isSearchActive = true (Search Mode)              │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  🔍 [Search apps...                        ] (✕)   │ │  ← PINNED native View
│  └─────────────────────────────────────────────────────┘ │
│  ┌─ RecyclerView ──────────────────────────────────────┐ │
│  │  All Apps                                 (✕)       │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │  ☐ [icon] App Name A         45 MB • 3 days ago    │ │
│  │  ☐ [icon] App Name B         12 MB • Never used    │ │
│  │  ☐ [icon] App Name C         8 MB  • Today         │ │
│  │  ...  (scrollable, search bar stays pinned)         │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌ KEYBOARD ───────────────────────────────────────────┐ │
│  │                                                     │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘

    Cancel flow:
      Tap ✕ with text → clears text, stays in search
      Tap ✕ with no text → exits search, cards return
      Back button → same two-step logic
```

---

## Edge Cases to Handle

### 1. Keyboard insets
The `Scaffold` from `DashboardScreen.kt` applies `paddingValues` which should handle IME insets. If the RecyclerView content is hidden behind the keyboard, add `android:windowSoftInputMode=\"adjustResize\"` to the `<activity>` in `AndroidManifest.xml`:

```xml
<activity
    android:name=\".MainActivity\"
    android:windowSoftInputMode=\"adjustResize\"
    ... />
```

### 2. Text sync loop prevention
The `TextWatcher` fires `onQueryChange` → Compose state updates → `update` block runs → sees text is different → calls `setText()` → `TextWatcher` fires again. **This is prevented** by the check:
```kotlin
if (currentText != searchQuery) {
    searchEditText.setText(searchQuery)
}
```
Since `setText` triggers `afterTextChanged` which calls `queryCallback.onQueryChange`, and that sets `searchQuery` to the same value, Compose won't recompose (same value = no state change).

### 3. Animation overlap
If the user rapidly toggles search, the `ViewPropertyAnimator` calls may overlap. Add `.cancel()` before starting a new animation:
```kotlin
searchBarView.animate().cancel()  // Cancel any in-flight animation
searchBarView.animate()
    .translationY(...)
    ...
```

### 4. Empty search results
When `filteredApps` is empty during search, only `AllAppsHeader` shows in the RecyclerView. This looks bare. Consider either:
- Adding an empty state item type to the adapter (e.g., `DashboardItem.EmptySearch(query)`)
- Or overlaying a message via a native `TextView` in the root `LinearLayout`

### 5. Selection persistence across search
`selectedApps` state persists across search/non-search mode. This is intentional — users can search, select, exit search, and keep their selections.

### 6. Initial translationY for slide animation
On first show, `searchBarView.height` may be 0 (not yet measured). Use a fixed dp value (e.g., `200 * density`) as the initial `translationY` offset instead of relying on the view's height. After the first show/hide cycle, you can use the actual measured height.

---

## Performance Notes

- **No Compose recomposition for search bar UI**: The entire search bar is a native `View`. The only Compose involvement is state (`isSearchActive`, `searchQuery`) which is the minimum needed.
- **RecyclerView DiffUtil handles card removal**: When top cards are removed from `dashboardItems`, the `DashboardItemDiffCallback` identifies removals. `DefaultItemAnimator` provides fade/slide animation without custom code.
- **No additional layout passes**: The pinned search bar is a simple `GONE`/`VISIBLE` toggle in a `LinearLayout`. When `GONE`, it takes zero space.
- **TextWatcher bridge is lightweight**: The `SearchQueryCallback` avoids creating new `TextWatcher` objects on each recomposition.

---

## Testing Checklist

- [ ] Tap search icon → top cards animate out (fade/slide via DefaultItemAnimator), pinned search bar slides down, keyboard opens
- [ ] Pinned search bar stays fixed when scrolling the app list up and down
- [ ] Type a query → app list filters in real-time
- [ ] Tap ✕ with text present → text clears, search stays active, keyboard stays open
- [ ] Tap ✕ with empty text → search exits, top cards animate back in, keyboard dismisses
- [ ] Tap close icon in \"All Apps\" header → same as ✕ with empty text
- [ ] Press hardware back with text → text clears first
- [ ] Press hardware back with empty text → exits search mode
- [ ] Select apps during search → floating action buttons appear at bottom
- [ ] Exit search → selections persist, floating buttons still visible if selections exist
- [ ] Rapid toggle: tap search icon 5x quickly → no crash, no animation glitch
- [ ] Dark mode: search bar uses correct theme colors (`?attr/colorSurfaceContainerHighest` etc.)
- [ ] Dynamic colors (Android 12+): search bar follows wallpaper palette
- [ ] No scroll jank: RecyclerView performance unchanged from pre-fix
- [ ] Keyboard insets: app list scrollable content visible above keyboard

---

## Summary of Changes

| File | Change | Scope |
|------|--------|-------|
| `DiscoveryScreen.kt` | Replace `DiscoveryDashboard` composable: `AndroidView` factory now creates `LinearLayout` root with pinned search bar + RecyclerView; modified `dashboardItems` builder; added `SearchQueryCallback` class | ~160 lines changed |
| `DiscoveryScreen.kt` | Add `SearchQueryCallback` internal class | ~4 lines added |
| `res/values/ids.xml` | New file: tag key IDs (optional, for clean tag usage) | ~5 lines |
| `DiscoveryDashboardAdapter.kt` | Optional: remove `SearchBarViewHolder`, `VIEW_TYPE_SEARCH_BAR` | ~30 lines removed |
| `item_search_bar.xml` | No change — reused as the pinned bar's inflated layout | 0 lines |

**Total: ~1 file significantly changed. No new layout XML needed. The existing `item_search_bar.xml` is reused as-is for the pinned search bar.**

---

## Why This Approach (vs Alternatives)

| Approach | Pros | Cons | Verdict |
|----------|------|------|---------|
| **Native LinearLayout container with pinned View** (this approach) | Pure native views; no Compose UI elements; `item_search_bar.xml` reused; simple `ViewPropertyAnimator` for transitions; consistent with existing RecyclerView architecture | Slightly more imperative code than Compose equivalent; need TextWatcher→Compose bridge | **Best fit** — stays native, avoids Compose for visual elements |
| Compose `AnimatedVisibility` overlay | Cleaner code, Compose-native animations | Mixes Compose UI into a performance-sensitive native View area; Compose search bar recomposes separately from RecyclerView updates | Avoided per user constraint |
| RecyclerView sticky header (ItemDecoration) | Pure RecyclerView solution | Complex to implement correctly; sticky header needs manual drawing; doesn't integrate well with search bar input/focus management | Over-engineered |
| CoordinatorLayout + AppBarLayout | Standard Android collapsing toolbar pattern | Major restructuring needed; conflicts with Compose Scaffold; doesn't match the current architecture | Wrong architecture |
| Replace RecyclerView with LazyColumn | Trivial `stickyHeader{}` in Compose | Already rejected due to scroll jank on mid-range devices (Moto G52) — see `jankfix.md` | Not viable |
"
