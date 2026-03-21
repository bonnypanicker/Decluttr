> **Scope:** Replace remaining Compose UI that sits above/around native `RecyclerView` instances and holds text-input or scroll state. Static Compose UI (empty states, one-shot dialogs, FABs) is left as-is.
>
> **Repo context:** `com.example.decluttr` — Kotlin, Compose + View interop, Material 3, Hilt, Room, Coil 2.

---

## Table of Contents

1. [Pre-requisites & New Resources](#1-pre-requisites--new-resources)
2. [Fix 1 — Archive Search Bar](#2-fix-1--archive-search-bar-highest-priority)
3. [Fix 2 — Category Pills](#3-fix-2--category-pills-high-priority)
4. [Fix 3 — Folder Creation Dialog](#4-fix-3--folder-creation-dialog-medium-priority)
5. [Fix 4 — Expanded Folder Dialog](#5-fix-4--expanded-folder-dialog-medium-priority)
6. [Fix 5 — SpecificAppListDisplay Header](#6-fix-5--specificapplistdisplay-header-lower-priority)
7. [Dead Code Removal](#7-dead-code-removal)
8. [Migration Checklist](#8-migration-checklist)

---

## 1. Pre-requisites & New Resources

### 1a. Add Resource IDs

Create **`res/values/ids.xml`** (new file):

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<resources>
    <item name=\"chip_container\" type=\"id\" />
    <item name=\"archive_search_callback\" type=\"id\" />
    <item name=\"specific_search_callback\" type=\"id\" />
    <item name=\"sort_chip_group\" type=\"id\" />
    <item name=\"select_all_container\" type=\"id\" />
    <item name=\"select_all_checkbox\" type=\"id\" />
    <item name=\"select_all_label\" type=\"id\" />
    <item name=\"selection_info\" type=\"id\" />
</resources>
```

### 1b. New Layout — `res/layout/dialog_expanded_folder.xml`

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    android:orientation=\"vertical\"
    android:padding=\"16dp\">

    <TextView
        android:id=\"@+id/folder_title\"
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:gravity=\"center\"
        android:textSize=\"20sp\"
        android:textStyle=\"bold\"
        android:textColor=\"?attr/colorOnSurface\" />

    <androidx.recyclerview.widget.RecyclerView
        android:id=\"@+id/folder_grid\"
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:layout_marginTop=\"16dp\"
        android:clipToPadding=\"false\" />
</LinearLayout>
```

### 1c. Callback Wrapper (reuse pattern from Dashboard)

The existing `SearchQueryCallback` in `DiscoveryScreen.kt` is the model. We'll reuse the same class across Archive and SpecificAppList screens — it's already `internal`.

```kotlin
// Already exists in DiscoveryScreen.kt — no change needed:
internal class SearchQueryCallback {
    var onQueryChange: ((String) -> Unit)? = null
    var onExitSearch: (() -> Unit)? = null
}
```

---

## 2. Fix 1 — Archive Search Bar *(Highest Priority)*

**File:** `ArchivedAppsList.kt`

**Problem:** Compose `OutlinedTextField` (lines 117-139) sits above the native `ArchivedAppsRecyclerView`. Every keystroke triggers full recomposition of `ArchivedAppsList`, re-evaluating `filteredApps` → `groupedItems` and pushing a new list through the `AndroidView` update block.

### What to remove

```kotlin
// DELETE lines 117-139 — the entire OutlinedTextField block:
OutlinedTextField(
    value = searchQuery,
    onValueChange = { searchQuery = it },
    placeholder = { Text(\"Search\") },
    leadingIcon = { Icon(Icons.Default.Search, contentDescription = \"Search\") },
    trailingIcon = {
        if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = \"\" }) {
                Icon(Icons.Default.Clear, contentDescription = \"Clear Search\")
            }
        }
    },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    shape = RoundedCornerShape(100),
    singleLine = true,
    colors = TextFieldDefaults.outlinedTextFieldColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedBorderColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.colorScheme.primary
    )
)
```

### What to add (replace at same location)

```kotlin
// Native search bar — identical pattern to DiscoveryDashboard
AndroidView(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    factory = { ctx ->
        val searchBarView = android.view.LayoutInflater.from(ctx)
            .inflate(R.layout.item_search_bar, null, false)

        val searchEditText = searchBarView
            .findViewById<android.widget.EditText>(R.id.search_edit_text)
        val clearButton = searchBarView
            .findViewById<android.widget.ImageView>(R.id.clear_button)

        searchEditText.hint = \"Search\"

        val callback = SearchQueryCallback()
        searchBarView.setTag(R.id.archive_search_callback, callback)

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
            searchEditText.setText(\"\")
        }

        searchBarView
    },
    update = { view ->
        val callback = view.getTag(R.id.archive_search_callback) as SearchQueryCallback
        callback.onQueryChange = { searchQuery = it }

        val editText = view.findViewById<android.widget.EditText>(R.id.search_edit_text)
        if (editText.text.toString() != searchQuery) {
            editText.setText(searchQuery)
            editText.setSelection(searchQuery.length)
        }
    }
)
```

### Imports to add

```kotlin
import com.example.decluttr.R
```

> `SearchQueryCallback` is already `internal` in `DiscoveryScreen.kt` and lives in the same package — no import needed.

### Imports to remove (after all 5 fixes are done)

```kotlin
// These become unused after removing the Compose OutlinedTextField:
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```

> **Note:** Some of these may still be used by the folder creation form or expanded folder dialog until those are ported too. Remove only when the last consumer is gone.

---

## 3. Fix 2 — Category Pills *(High Priority)*

**File:** `ArchivedAppsList.kt`

**Problem:** `LazyRow` (lines 142-158) is a horizontal Compose scroller above a vertical native `RecyclerView`. These are separate scroll systems, with unnecessary Compose overhead for a chip strip that rarely scrolls.

### What to remove

```kotlin
// DELETE lines 142-158 — the entire LazyRow / Spacer block:
if (categories.size > 1) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            CategoryPill(
                text = category,
                isSelected = selectedCategory == category,
                onClick = { selectedCategory = category }
            )
        }
    }
} else {
    Spacer(modifier = Modifier.height(8.dp))
}
```

### What to add (replace at same location)

```kotlin
if (categories.size > 1) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        factory = { ctx ->
            android.widget.HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                val dp16 = (16 * ctx.resources.displayMetrics.density).toInt()
                setPadding(dp16, 0, dp16, 0)

                addView(
                    android.widget.LinearLayout(ctx).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        id = R.id.chip_container
                    }
                )
            }
        },
        update = { hsv ->
            val container = hsv.findViewById<android.widget.LinearLayout>(R.id.chip_container)
            container.removeAllViews()

            val density = hsv.context.resources.displayMetrics.density
            val dp8 = (8 * density).toInt()

            categories.forEach { category ->
                com.google.android.material.chip.Chip(hsv.context).apply {
                    text = category
                    isCheckable = true
                    isChecked = selectedCategory == category

                    // Material 3 styling
                    chipBackgroundColor = android.content.res.ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(
                            com.google.android.material.R.attr.colorPrimary
                                .let { attr ->
                                    val ta = hsv.context.obtainStyledAttributes(intArrayOf(attr))
                                    ta.getColor(0, 0).also { ta.recycle() }
                                },
                            com.google.android.material.R.attr.colorSurfaceVariant
                                .let { attr ->
                                    val ta = hsv.context.obtainStyledAttributes(intArrayOf(attr))
                                    ta.getColor(0, 0).also { ta.recycle() }
                                }
                        )
                    )

                    setOnClickListener { selectedCategory = category }

                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp8
                    }
                }.also { container.addView(it) }
            }
        }
    )
} else {
    Spacer(modifier = Modifier.height(8.dp))
}
```

### Imports to add

```kotlin
// None new — android.widget.* and com.google.android.material.chip.Chip
// are resolved via fully-qualified names in the snippet above.
// Optionally add:
import com.google.android.material.chip.Chip
```

### Imports to remove

```kotlin
// After CategoryPill composable is deleted (see Dead Code Removal):
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
```

---

## 4. Fix 3 — Folder Creation Dialog *(Medium Priority)*

**File:** `ArchivedAppsList.kt`

**Problem:** The inline Compose form (lines 183-211) sits in the same `Column` as `ArchivedAppsRecyclerView`. Every keystroke on `newFolderName` recomposes the entire `ArchivedAppsList`, pushing a new list through the RecyclerView update block. It also squashes the grid above it visually.

### What to remove

```kotlin
// DELETE lines 183-211 — the entire \"if (newFolderAppPair != null)\" Column:
if (newFolderAppPair != null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(\"Name your new folder\", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newFolderName,
            onValueChange = { newFolderName = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.Button(
            onClick = {
                val folderName = newFolderName.trim().ifEmpty { \"New Folder\" }
                newFolderAppPair?.let { (app1, app2) ->
                    onAppUpdate(app1.copy(folderName = folderName))
                    onAppUpdate(app2.copy(folderName = folderName))
                }
                newFolderAppPair = null
                newFolderName = \"\"
            }
        ) {
            Text(\"Create Folder\")
        }
    }
}
```

### What to add (replace with a `LaunchedEffect` that shows a native `AlertDialog`)

Place this **inside** the `Column(modifier = Modifier.fillMaxSize())` block, **before** the `ArchivedAppsRecyclerView` call (i.e., where the removed block was):

```kotlin
val context = LocalContext.current

// Show native AlertDialog when drag-drop creates a new folder pair
LaunchedEffect(newFolderAppPair) {
    val pair = newFolderAppPair ?: return@LaunchedEffect

    val editText = android.widget.EditText(context).apply {
        hint = \"Folder name\"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        setPadding(dp16, dp16, dp16, dp16)
    }

    val frameLayout = android.widget.FrameLayout(context).apply {
        val dp24 = (24 * resources.displayMetrics.density).toInt()
        setPadding(dp24, 0, dp24, 0)
        addView(editText)
    }

    android.app.AlertDialog.Builder(context)
        .setTitle(\"Name your new folder\")
        .setView(frameLayout)
        .setPositiveButton(\"Create\") { _, _ ->
            val folderName = editText.text.toString().trim().ifEmpty { \"New Folder\" }
            onAppUpdate(pair.first.copy(folderName = folderName))
            onAppUpdate(pair.second.copy(folderName = folderName))
            newFolderAppPair = null
            newFolderName = \"\"
        }
        .setNegativeButton(\"Cancel\") { dialog, _ ->
            dialog.dismiss()
            newFolderAppPair = null
            newFolderName = \"\"
        }
        .setOnCancelListener {
            newFolderAppPair = null
            newFolderName = \"\"
        }
        .show()
}
```

### Imports to add

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
```

> `LocalContext` is already imported. `LaunchedEffect` may already be imported depending on future changes — verify.

### State to remove

The `newFolderName` state is no longer needed — the native `EditText` manages its own text. You can remove:

```kotlin
// DELETE this line (currently line 98):
var newFolderName by remember { mutableStateOf(\"\") }
```

The `newFolderAppPair` state is still needed as the trigger, keep it.

---

## 5. Fix 4 — Expanded Folder Dialog *(Medium Priority)*

**File:** `ArchivedAppsList.kt`

**Problem:** The `expandedFolder` branch (lines 238-293) renders a Compose `Dialog` → `Surface` → `LazyVerticalGrid` with `AsyncImage` items. This creates a full Compose composition for a grid of icons while the parent screen is already native.

### What to remove

```kotlin
// DELETE lines 238-293 — the entire \"if (expandedFolder != null)\" block:
if (expandedFolder != null) {
    val folderApps = apps.filter { it.folderName == expandedFolder }
    if (folderApps.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { expandedFolder = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    // ... entire dialog body
                ) {
                    // LazyVerticalGrid with AppDrawerItemDraggable items
                }
            }
        }
    } else {
        expandedFolder = null
    }
}
```

### What to add (replace with native dialog triggered by `LaunchedEffect`)

Place this **after** the closing `}` of the outer `Column` in `ArchivedAppsList`, at the same level where the removed block was:

```kotlin
val dialogContext = LocalContext.current

LaunchedEffect(expandedFolder) {
    val folderName = expandedFolder ?: return@LaunchedEffect
    val folderApps = apps.filter { it.folderName == folderName }

    if (folderApps.isEmpty()) {
        expandedFolder = null
        return@LaunchedEffect
    }

    val dialogView = android.view.LayoutInflater.from(dialogContext)
        .inflate(R.layout.dialog_expanded_folder, null)

    val titleView = dialogView.findViewById<android.widget.TextView>(R.id.folder_title)
    val gridRv = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.folder_grid)

    titleView.text = folderName
    gridRv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(dialogContext, 4)

    // Reuse ArchivedAppsAdapter's AppViewHolder pattern —
    // or create a minimal inline adapter:
    val folderAdapter = FolderAppsAdapter(
        apps = folderApps,
        onAppClick = { packageId ->
            expandedFolder = null
            onAppClick(packageId)
        }
    )
    gridRv.adapter = folderAdapter

    android.app.AlertDialog.Builder(dialogContext)
        .setView(dialogView)
        .setNegativeButton(\"Close\") { dialog, _ ->
            dialog.dismiss()
            expandedFolder = null
        }
        .setOnCancelListener {
            expandedFolder = null
        }
        .show()
}
```

### New class: `FolderAppsAdapter`

Create this in a new file **`FolderAppsAdapter.kt`** in the same package, or add it to `ArchivedAppsAdapter.kt`:

```kotlin
package com.example.decluttr.presentation.screens.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.decluttr.R
import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.presentation.util.AppIconModel

/**
 * Minimal adapter for the expanded-folder dialog grid.
 * Reuses item_archived_app.xml layout.
 */
class FolderAppsAdapter(
    private val apps: List<ArchivedApp>,
    private val onAppClick: (String) -> Unit
) : RecyclerView.Adapter<FolderAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archived_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.name.text = app.name
        holder.icon.load(AppIconModel(app.packageId)) {
            memoryCacheKey(app.packageId)
            crossfade(false)
        }
        holder.itemView.setOnClickListener { onAppClick(app.packageId) }
    }

    override fun getItemCount() = apps.size
}
```

### Imports to remove (from ArchivedAppsList.kt, after fix)

```kotlin
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.heightIn
```

---

## 6. Fix 5 — SpecificAppListDisplay Header *(Lower Priority)*

**File:** `DiscoveryScreen.kt`, function `SpecificAppListDisplay` (line 558+)

**Problem:** The header `Column` (lines 621-771) above the RecyclerView contains a Compose search bar, sort chips, select-all checkbox row, and action buttons — all in Compose. Every search keystroke recomposes the `Column`, which triggers the `AndroidView` update block and `adapter.submitList()`.

### 5a. Port the Search Bar

**What to remove** (lines 650-680):

```kotlin
// DELETE the Compose OutlinedTextField:
androidx.compose.material3.OutlinedTextField(
    value = searchQuery,
    onValueChange = { searchQuery = it },
    placeholder = { Text(\"Search apps\") },
    leadingIcon = { ... },
    trailingIcon = { ... },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(100),
    singleLine = true,
    colors = ...
)
```

**What to add:**

```kotlin
AndroidView(
    modifier = Modifier.fillMaxWidth(),
    factory = { ctx ->
        val searchBarView = android.view.LayoutInflater.from(ctx)
            .inflate(R.layout.item_search_bar, null, false)

        val searchEditText = searchBarView
            .findViewById<android.widget.EditText>(R.id.search_edit_text)
        val clearButton = searchBarView
            .findViewById<android.widget.ImageView>(R.id.clear_button)

        searchEditText.hint = \"Search apps\"

        val callback = SearchQueryCallback()
        searchBarView.setTag(R.id.specific_search_callback, callback)

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
            searchEditText.setText(\"\")
        }

        searchBarView
    },
    update = { view ->
        val callback = view.getTag(R.id.specific_search_callback) as SearchQueryCallback
        callback.onQueryChange = { searchQuery = it }

        val editText = view.findViewById<android.widget.EditText>(R.id.search_edit_text)
        if (editText.text.toString() != searchQuery) {
            editText.setText(searchQuery)
            editText.setSelection(searchQuery.length)
        }
    }
)
```

### 5b. Port the Sort Chips

**What to remove** (lines 684-696):

```kotlin
// DELETE the Compose FilterChip Row:
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
```

**What to add:**

```kotlin
AndroidView(
    modifier = Modifier.fillMaxWidth(),
    factory = { ctx ->
        android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                com.google.android.material.chip.ChipGroup(ctx).apply {
                    id = R.id.sort_chip_group
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
        val group = hsv.findViewById<com.google.android.material.chip.ChipGroup>(R.id.sort_chip_group)
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as com.google.android.material.chip.Chip
            chip.isChecked = chip.tag == sortOption
        }
    }
)
```

### 5c. Port the Select-All Row

**What to remove** (lines 700-733):

```kotlin
// DELETE the Compose Row with Checkbox + \"Select All\"/\"Deselect All\":
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
                onCheckedChange = { checked -> ... }
            )
            Text(
                text = if (allFilteredSelected) \"Deselect All\" else \"Select All\",
                ...
            )
        }
        if (selectedApps.isNotEmpty()) {
            Text(text = \"${selectedApps.size} of ${appList.size} • ${bytesToMB(selectedSize)} MB\", ...)
        }
    }
}
```

**What to add:**

```kotlin
if (filteredList.isNotEmpty()) {
    val allFilteredSelected = filteredList.all { it.packageId in selectedApps }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            android.widget.LinearLayout(ctx).apply {
                id = R.id.select_all_container
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val dp8 = (8 * ctx.resources.displayMetrics.density).toInt()
                setPadding(0, dp8, 0, dp8)

                // Checkbox
                addView(android.widget.CheckBox(ctx).apply {
                    id = R.id.select_all_checkbox
                })

                // Label
                addView(android.widget.TextView(ctx).apply {
                    id = R.id.select_all_label
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
                    id = R.id.selection_info
                    textSize = 12f
                })
            }
        },
        update = { layout ->
            val checkBox = layout.findViewById<android.widget.CheckBox>(R.id.select_all_checkbox)
            val label = layout.findViewById<android.widget.TextView>(R.id.select_all_label)
            val info = layout.findViewById<android.widget.TextView>(R.id.selection_info)

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = allFilteredSelected
            checkBox.setOnCheckedChangeListener { _, checked ->
                selectedApps = if (checked) {
                    selectedApps + filteredList.map { it.packageId }
                } else {
                    selectedApps - filteredList.map { it.packageId }.toSet()
                }
            }

            label.text = if (allFilteredSelected) \"Deselect All\" else \"Select All\"

            if (selectedApps.isNotEmpty()) {
                info.visibility = android.view.View.VISIBLE
                info.text = \"${selectedApps.size} of ${appList.size} \u2022 ${bytesToMB(selectedSize)} MB\"
            } else {
                info.visibility = android.view.View.GONE
            }
        }
    )
}
```

### 5d. Keep Action Buttons as Compose

The \"Uninstall Only\" and \"Archive & Uninstall\" buttons are conditionally shown and don't affect list rendering. **Leave them as-is** — their recomposition cost is negligible.

---

## 7. Dead Code Removal

After all five fixes are applied, the following composables have **zero callers** and should be deleted:

### 7a. `FolderDrawerItem` — `ArchivedAppsList.kt` lines 380-453

```kotlin
// DELETE entirely — replaced by FolderViewHolder in ArchivedAppsAdapter
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderDrawerItem(
    folderName: String,
    apps: List<ArchivedApp>,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // ... 70 lines
}
```

**Verification:** `grep -rn \"FolderDrawerItem(\" --include=\"*.kt\"` returns only the definition (line 382). No callers.

### 7b. `PlaceholderIcon` — `ArchivedAppsList.kt` lines 455-461

```kotlin
// DELETE entirely — not referenced anywhere
@Composable
fun PlaceholderIcon(modifier: Modifier = Modifier.size(56.dp)) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
    )
}
```

**Verification:** `grep -rn \"PlaceholderIcon\" --include=\"*.kt\"` returns only the definition. No callers.

### 7c. `AppDrawerItemDraggable` — `ArchivedAppsList.kt` lines 325-378

```kotlin
// DELETE entirely — only used in expanded folder dialog (now native)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerItemDraggable(
    app: ArchivedApp,
    isDragging: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    // ... 50 lines
}
```

**Verification:** Only called at line 266 inside the expanded folder dialog block, which was deleted in Fix 4.

### 7d. `CategoryPill` — `ArchivedAppsList.kt` lines 301-323

```kotlin
// DELETE entirely — replaced by native Chip views
@Composable
fun CategoryPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // ... 20 lines
}
```

**Verification:** Only called at line 149 inside the LazyRow block, which was deleted in Fix 2.

### 7e. Unused imports cleanup

After deleting all dead composables and applying all fixes, run \"Optimize Imports\" in Android Studio. The following are **expected** to become unused:

```kotlin
// From ArchivedAppsList.kt — likely removable:
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
```

> **Do not** blindly delete — use IDE's \"Optimize Imports\" to verify. Some may still be used by the `ArchivedItem` sealed class or the remaining empty state Compose code.

---

## 8. Migration Checklist

Use this checklist to track progress across all fixes. Each item should be verified **after** the code change.

### Pre-flight

- [ ] `res/values/ids.xml` created with all new IDs
- [ ] `res/layout/dialog_expanded_folder.xml` created
- [ ] `FolderAppsAdapter.kt` created in `presentation/screens/dashboard/`
- [ ] Clean build succeeds before starting changes

### Fix 1 — Archive Search Bar

- [ ] Compose `OutlinedTextField` removed from `ArchivedAppsList.kt`
- [ ] Native `AndroidView` with `item_search_bar.xml` added
- [ ] `SearchQueryCallback` bridging wired up
- [ ] **Verify:** Type in search bar — `filteredApps` updates, RecyclerView shows correct results
- [ ] **Verify:** Clear button clears text and restores full list
- [ ] **Verify:** No visible layout shift compared to before
- [ ] **Verify:** No logcat Compose recomposition spam on each keystroke (enable recomposition counter in Layout Inspector)

### Fix 2 — Category Pills

- [ ] Compose `LazyRow` + `CategoryPill` removed
- [ ] Native `HorizontalScrollView` + `Chip` views added
- [ ] **Verify:** Tapping chip filters the grid correctly
- [ ] **Verify:** Chip selection state visually correct (checked/unchecked)
- [ ] **Verify:** Horizontal scroll works if many categories
- [ ] **Verify:** Pills hidden when only \"All\" category exists

### Fix 3 — Folder Creation Dialog

- [ ] Compose inline `Column` with `OutlinedTextField` removed
- [ ] `newFolderName` state variable removed
- [ ] `LaunchedEffect` + native `AlertDialog` added
- [ ] **Verify:** Drag app onto app → dialog appears
- [ ] **Verify:** Enter name → folder created with correct name
- [ ] **Verify:** Cancel → no folder created, state reset
- [ ] **Verify:** Empty name → defaults to \"New Folder\"
- [ ] **Verify:** Grid no longer \"squashes\" during folder creation

### Fix 4 — Expanded Folder Dialog

- [ ] Compose `Dialog` + `LazyVerticalGrid` removed
- [ ] `FolderAppsAdapter` created and used in native `AlertDialog`
- [ ] `dialog_expanded_folder.xml` layout works correctly
- [ ] **Verify:** Tap folder → dialog shows apps in 4-column grid
- [ ] **Verify:** Tap app in dialog → navigates to app details, dialog closes
- [ ] **Verify:** \"Close\" button dismisses dialog
- [ ] **Verify:** Icons load correctly via Coil

### Fix 5 — SpecificAppListDisplay Header

- [ ] Compose `OutlinedTextField` replaced with native search bar
- [ ] Compose `FilterChip` row replaced with native `ChipGroup`
- [ ] Compose `Checkbox` + `Select All` row replaced with native views
- [ ] Action buttons (Uninstall Only / Archive & Uninstall) left as Compose — confirmed no change
- [ ] **Verify:** Search filters the list
- [ ] **Verify:** Sort chips change sort order
- [ ] **Verify:** Select All / Deselect All works
- [ ] **Verify:** Selection count and MB label update correctly
- [ ] **Verify:** No jank on rapid typing

### Dead Code Removal

- [ ] `FolderDrawerItem` composable deleted
- [ ] `PlaceholderIcon` composable deleted
- [ ] `AppDrawerItemDraggable` composable deleted
- [ ] `CategoryPill` composable deleted
- [ ] Unused imports cleaned (via IDE Optimize Imports)
- [ ] **Verify:** Clean build with zero warnings from removed code
- [ ] **Verify:** `grep -rn \"FolderDrawerItem\|PlaceholderIcon\|AppDrawerItemDraggable\|CategoryPill\" --include=\"*.kt\"` returns no results

### Post-flight

- [ ] Full clean build succeeds (`./gradlew assembleDebug`)
- [ ] App launches on device/emulator
- [ ] Discover tab — dashboard, search, selection, batch uninstall all work
- [ ] Discover tab — Rarely Used / Large Apps / All Apps sub-lists work
- [ ] Archive tab — search, category pills, grid display work
- [ ] Archive tab — drag-to-create-folder works
- [ ] Archive tab — folder tap opens dialog, app tap navigates
- [ ] Archive tab — long-press folder removes it
- [ ] Settings screen unaffected
- [ ] No Compose recomposition warnings in logcat for text input paths
- [ ] Layout Inspector shows reduced recomposition counts for Archive and Discovery screens

---

