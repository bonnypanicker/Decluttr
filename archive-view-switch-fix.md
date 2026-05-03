# Archive View Switch — Folder Loading Delay: Root Cause & Fix

## TL;DR

Three compounding bugs cause the folder-content flash when switching between grid and list view. The dominant one is a **double-render**: the adapter fires `notifyDataSetChanged()` with stale data the moment the mode flag changes, then `submitList()` fires again via DiffUtil 1–3 frames later with correct data. Folders visibly appear in the wrong state between those two frames. The other two bugs worsen the timing: DiffUtil has no way to detect view-type changes from item content alone (so the double-render is architecturally required by the current design), and Coil's async dispatch adds another frame of blank icons on the folder preview.

---

## Bug 1 — Double Render (Primary Cause)

### Code path

```
btnViewSwitch.setOnClickListener {
    isListMode = !isListMode
    adapter.setListMode(isListMode)          ← (A) notifyDataSetChanged() fires HERE
    recyclerView.layoutManager = ...
    updateUI(viewModel.archivedApps.value)   ← (B) submitList() fires HERE
}
```

```kotlin
// ArchivedAppsAdapter.kt  lines 87–92
fun setListMode(enabled: Boolean) {
    if (isListMode != enabled) {
        isListMode = enabled
        notifyDataSetChanged()               ← triggers full rebind of the OLD list
    }
}
```

### What happens frame-by-frame

**Scenario: switching Grid → List, with one folder "Productivity" containing 2 apps**

| Frame | Event | State of AsyncListDiffer's currentList | What RecyclerView shows |
|-------|-------|---------------------------------------|------------------------|
| 0 | User taps switch | `[App(A), Folder("Productivity", [B,C]), App(D)]` | Grid: icons + folder tile |
| 1 | **(A)** `notifyDataSetChanged()` executes | Still `[App(A), Folder("Productivity", …), App(D)]` — `submitList()` hasn't been called yet | RecyclerView calls `getItemViewType()` (now reads `isListMode=true`) and `onBindViewHolder()` for every position using OLD items. Position 1 is a `Folder` item → binds into `FolderListViewHolder` showing "Productivity • 2 apps" row |
| 2 | Layout manager swapped | unchanged | unchanged |
| 3–4 | **(B)** `submitList([App(A), App(B), App(C), App(D)])` queues DiffUtil on a worker thread | unchanged | unchanged — user still sees "Productivity • 2 apps" row in the list |
| 5 | DiffUtil result dispatched on main thread | Now `[App(A), App(B), App(C), App(D)]` | RecyclerView updates: Folder row replaced by App(B) row, App(C) inserted, App(D) shifted |

The user sees: **a brief flash of the wrong item** ("Productivity • 2 apps" as a list-mode folder row) before the folder correctly expands into individual app rows. This is exactly what the screen recording shows.

The reverse path (List → Grid) shows individual app rows momentarily before the folder preview collapses them, with icons loading asynchronously.

### Why `notifyDataSetChanged()` exists in the first place

`getItemViewType()` reads the adapter-level `isListMode` flag, not the item itself:

```kotlin
override fun getItemViewType(position: Int): Int {
    return when(getItem(position)) {
        is ArchivedItem.App    -> if (isListMode) 2 else 0
        is ArchivedItem.Folder -> if (isListMode) 3 else 1
    }
}
```

DiffUtil's `areContentsTheSame()` compares `ArchivedItem` instances. `ArchivedItem.App` and `ArchivedItem.Folder` are data classes whose equality depends only on their fields — none of which encode the current display mode. So for apps that exist in both modes with the same data, DiffUtil reports `areItemsTheSame=true, areContentsTheSame=true` → **no rebind dispatched** → RecyclerView keeps the old `AppViewHolder` (grid type 0) when it should now use `AppListViewHolder` (list type 2).

The `notifyDataSetChanged()` call is therefore load-bearing: without it, items that don't change data but do change view type would be permanently stuck with the wrong holder type. The double-render is a direct consequence of this design.

---

## Bug 2 — View Type Not Encoded in Items (Structural Root)

`ArchivedItem` carries no information about which display mode it belongs to. This makes it impossible for DiffUtil to detect view-type changes, which forces `notifyDataSetChanged()` as a crutch.

```kotlin
// Current — mode is external to the item
sealed class ArchivedItem {
    data class App(val app: ArchivedApp) : ArchivedItem()
    data class Folder(val name: String, val apps: List<ArchivedApp>) : ArchivedItem()
}
```

An `ArchivedItem.App(app)` in grid mode is structurally identical to `ArchivedItem.App(app)` in list mode. DiffUtil cannot distinguish them.

---

## Bug 3 — Coil Async Dispatch for Folder Preview Icons (Secondary)

When switching List → Grid, new `FolderViewHolder` instances are created (view type changed, so they can't be recycled from `AppListViewHolder`). `renderFolderPreview()` calls `loadIcon()` for each of up to 4 apps:

```kotlin
private fun loadIcon(imageView: ImageView, app: ArchivedApp) {
    val bmp = IconBitmapCache.getOrDecode(app)  // only hits if iconBytes is non-null
    if (bmp != null) {
        imageView.setImageBitmap(bmp)
    } else {
        imageView.load(AppIconModel(app.packageId)) { ... }  // Coil async path
    }
}
```

```kotlin
// IconBitmapCache.kt
fun getOrDecode(app: ArchivedApp): Bitmap? {
    val bytes = app.iconBytes ?: return null   // ← returns null when iconBytes absent
    ...
}
```

Most archived apps were uninstalled without capturing their icon as bytes (or the icon was never serialised into the `ArchivedApp` entity). So `iconBytes` is null → `IconBitmapCache.getOrDecode()` returns null → Coil's `imageView.load()` is called. Even when Coil has the bitmap in its memory cache, the dispatch goes through a coroutine, adding at least one additional frame before the icon appears. With 4 icons per folder, this means 4 async dispatches, causing the "blank icons flash" visible in the recording.

---

## The Fix

### Fix A — Encode view mode in `ArchivedItem` (resolves Bugs 1 and 2 together)

Add `inListMode: Boolean` to `ArchivedItem.App`. This makes the item's identity include display-mode, so DiffUtil will detect view-type changes and dispatch `notifyItemChanged` without any `notifyDataSetChanged()`.

Folders never appear in list mode (list mode expands them into individual apps), so `ArchivedItem.Folder` needs no flag.

**`ArchivedItem` (in `ArchivedAppsAdapter.kt`):**

```kotlin
sealed class ArchivedItem {
    // Add inListMode — this is what lets DiffUtil see view-type changes
    data class App(val app: ArchivedApp, val inListMode: Boolean = false) : ArchivedItem()
    data class Folder(val name: String, val apps: List<ArchivedApp>) : ArchivedItem()
}
```

**`ArchiveDiffCallback`:** no change needed — data class equality already includes `inListMode`.

**`ArchivedAppsAdapter.getItemViewType()`:** reads from the item, not the adapter flag:

```kotlin
override fun getItemViewType(position: Int): Int {
    return when (val item = getItem(position)) {
        is ArchivedItem.App    -> if (item.inListMode) 2 else 0
        is ArchivedItem.Folder -> 1   // Folders only exist in grid mode
    }
}
```

**`ArchivedAppsAdapter.setListMode()`:** remove entirely. The adapter no longer needs a mode flag. Delete `isListMode`, `setListMode()`, and the `getItemId()` override if it was only there for `notifyDataSetChanged()` stability (stable IDs are still good to keep for move animation — keep `getItemId()`).

```kotlin
// REMOVE these from ArchivedAppsAdapter:
// private var isListMode: Boolean = false
// fun setListMode(enabled: Boolean) { ... }
```

**`ArchiveFragment.updateUI()` — list mode branch:**

```kotlin
val listItems = sortedApps.map { ArchivedItem.App(it, inListMode = true) }
```

**`ArchiveFragment.updateUI()` — grid mode branch:**

```kotlin
groupedItems += ArchivedItem.App(app, inListMode = false)
// Folder items unchanged:
groupedItems += ArchivedItem.Folder(folderName, folderApps)
```

**`ArchiveFragment.btnViewSwitch.setOnClickListener`:** remove `adapter.setListMode()`, reorder LayoutManager swap to happen before `updateUI()`:

```kotlin
btnViewSwitch.setOnClickListener {
    isListMode = !isListMode
    prefs.edit().putBoolean(PREF_KEY_ARCHIVE_LIST_MODE, isListMode).apply()

    // LayoutManager must be set BEFORE submitList() so RecyclerView
    // measures new items with the correct span count immediately.
    recyclerView.layoutManager = if (isListMode) {
        LinearLayoutManager(requireContext())
    } else {
        GridLayoutManager(requireContext(), 4)
    }
    btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE
    btnViewSwitch.setImageResource(
        if (isListMode) R.drawable.ic_grid_view else R.drawable.ic_list
    )

    // Single update path: submitList() handles everything.
    // Mode is encoded in the items; DiffUtil detects view-type changes.
    // No notifyDataSetChanged() call anywhere.
    updateUI(viewModel.archivedApps.value)
    scheduleTipsIfNeeded(forceImmediate = true)
}
```

**`ArchiveFragment.applyViewMode()`:** remove `adapter.setListMode()` call:

```kotlin
private fun applyViewMode() {
    // adapter.setListMode(isListMode)  ← REMOVE
    recyclerView.layoutManager = if (isListMode) {
        LinearLayoutManager(requireContext())
    } else {
        GridLayoutManager(requireContext(), 4)
    }
    btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE
    btnViewSwitch.setImageResource(if (isListMode) R.drawable.ic_grid_view else R.drawable.ic_list)
}
```

On first load `applyViewMode()` is called before any `submitList()`, so the adapter's list is empty — view types are never evaluated yet. The first `updateUI()` call (from `observeViewModel`) will submit items with the correct `inListMode` value, so the initial render is correct.

---

### Fix B — Pre-warm `IconBitmapCache` before grid-mode render (resolves Bug 3)

In `updateUI()`, synchronously decode any available `iconBytes` for folder apps before `submitList()`. This ensures `FolderViewHolder.loadIcon()` gets a synchronous cache hit and sets the bitmap in `onBindViewHolder()` without waiting for Coil.

Add this block in `updateUI()`, in the grid-mode branch, just before `adapter.submitList(groupedItems)`:

```kotlin
// Pre-warm icon cache for folder apps so FolderViewHolder binds icons
// synchronously, avoiding the Coil async-dispatch blank-icon flash.
filteredApps
    .filter { it.folderName != null && it.iconBytes != null }
    .forEach { IconBitmapCache.getOrDecode(it) }

adapter.submitList(groupedItems)
```

For apps where `iconBytes` is null (the common case), Coil is still used — but the pre-warm ensures that any apps whose bytes *were* stored don't contribute to the delay. Longer-term, capturing `iconBytes` at archive time (before uninstall) is the complete solution for all apps.

---

## File Change Summary

| File | Change |
|------|--------|
| `ArchivedAppsAdapter.kt` | Add `inListMode: Boolean = false` to `ArchivedItem.App`. Remove `isListMode` field, `setListMode()` method. Update `getItemViewType()` to read from item. Remove `viewType 3` (FolderListViewHolder remains, just triggered differently — actually: in list mode no Folder items are submitted, so viewType 3 can remain unused but harmless, or be removed). |
| `ArchiveFragment.kt` | Remove `adapter.setListMode(isListMode)` from `btnViewSwitch` click handler and `applyViewMode()`. Update `updateUI()` to pass `inListMode` when constructing items. Add `IconBitmapCache` pre-warm before grid `submitList()`. |

---

## Why Previous Attempts Didn't Work

The issue was likely approached as a timing/ordering problem (move `setListMode()` after `updateUI()`, add a `post {}`, etc.). Those approaches don't address the structural root cause: as long as `getItemViewType()` reads an external flag and `ArchivedItem` carries no mode information, DiffUtil is blind to view-type changes, making `notifyDataSetChanged()` unavoidable, which makes the double-render unavoidable. The only way out is to make mode part of the item's identity.

---

## What to Verify After the Fix

1. **Switch grid → list with a folder present**: folder should expand into individual app rows in a single animated transition with no "folder list row" intermediate state.
2. **Switch list → grid with apps that belong to a folder**: apps should collapse into a folder tile with icons appearing immediately (not blank then filled).
3. **Switch modes with no folders**: apps should transition smoothly, no blank frames.
4. **Apps that were in no folder**: `App(pkg, inListMode=false)` → `App(pkg, inListMode=true)` — DiffUtil sees `areContentsTheSame=false`, dispatches `notifyItemChanged`, RecyclerView creates `AppListViewHolder`. Verify this animation looks correct (default change animation or disable with `supportsChangeAnimations=false`).
5. **Rotate or tab switch (restores `isListMode` from prefs)**: `applyViewMode()` without `setListMode()` should still render correctly because the first `updateUI()` call submits items with the correct `inListMode` baked in.
6. **Drag-and-drop**: `ArchivedItem.App.inListMode` is now part of equality — verify `DragListener` and `finalizeDragSession` still work. The `draggingPackageId` lookup scans by `packageId`, not item equality, so it should be unaffected.
