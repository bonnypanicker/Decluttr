Now I have a comprehensive understanding of the codebase. Let me create the detailed analysis `.md` file.
Action: file_editor create /app/decluttr_analysis_and_fixes.md --file-text "# Decluttr — Code Analysis & Fix Guide

> **Scope**: Archive page, Reinstalled page (ghosting touch), Drag-and-drop folder creation (glitches/UI), Archive popup window, Bulk review popup  
> **Organized by**: Issue type (Stability, UI/UX, Performance, Code Quality)  
> **Severity**: CRITICAL / HIGH / MEDIUM / LOW  
> **Includes**: Specific code snippets and file references for LLM implementation

---

## Table of Contents

1. [Stability Issues](#1-stability-issues)
2. [UI/UX Issues](#2-uiux-issues)
3. [Performance Issues](#3-performance-issues)
4. [Code Quality Issues](#4-code-quality-issues)

---

## 1. Stability Issues

### 1.1 [CRITICAL] Reinstalled Page — Ghosting Touch / Phantom Click Passthrough

**File**: `ArchiveFragment.kt` (line 744–768), `fragment_archive.xml` (line 283–339)

**Problem**: The reinstalled page container (`reinstalled_page_container`) is a `LinearLayout` inside a `FrameLayout` that overlays the main archive `RecyclerView`. When it is made `VISIBLE`, touch events can bleed through to the archive grid beneath it because the `LinearLayout` does not intercept touches. This creates \"ghost touches\" — the user taps items on the reinstalled list but inadvertently activates items on the archive grid behind it, or vice versa.

Additionally, the reinstalled `RecyclerView` and the empty `TextView` exist as siblings. When the list is empty, the `TextView` appears **below** the RecyclerView (which has `layout_weight=1`), not centered — causing a layout jump that can feel like the touch target moved.

**Root Cause**: 
- `reinstalled_page_container` is a plain `LinearLayout` with no `android:clickable=\"true\"` or `android:focusable=\"true\"`. Android's touch dispatch sends the event to the first view that claims it; the `LinearLayout` doesn't consume it, so it falls through to the `RecyclerView` underneath.
- The reinstalled page doesn't disable interaction on the underlying archive RecyclerView when shown.

**Fix** (XML + Kotlin):

```xml
<!-- fragment_archive.xml — Add clickable + focusable to block touch passthrough -->
<LinearLayout
    android:id=\"@+id/reinstalled_page_container\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"match_parent\"
    android:background=\"?attr/colorSurface\"
    android:orientation=\"vertical\"
    android:clickable=\"true\"
    android:focusable=\"true\"
    android:visibility=\"gone\">
    <!-- ... existing children ... -->
</LinearLayout>
```

```kotlin
// ArchiveFragment.kt — setReinstalledPageVisible()
// Also disable touch on underlying RecyclerView to be extra safe:
private fun setReinstalledPageVisible(visible: Boolean) {
    isReinstalledPageVisible = visible
    reinstalledPageContainer.visibility = if (visible) View.VISIBLE else View.GONE
    recyclerView.isEnabled = !visible  // Block underlying grid interaction

    if (visible) {
        searchBar.visibility = View.GONE
        btnSort.visibility = View.GONE
        btnViewSwitch.visibility = View.GONE
        btnPremium.visibility = View.GONE
        creditsCard.visibility = View.GONE
        categoryBar.visibility = View.GONE
        infoCardsContainer.visibility = View.GONE

        reinstalledAdapter.submitArchivedApps(reinstatedApps)
        tvReinstalledEmpty.visibility = if (reinstatedApps.isEmpty()) View.VISIBLE else View.GONE
    } else {
        recyclerView.isEnabled = true
        searchBar.visibility = View.VISIBLE
        btnViewSwitch.visibility = View.VISIBLE
        btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE
        updateUI(viewModel.archivedApps.value)
    }
}
```

---

### 1.2 [CRITICAL] Drag-and-Drop — Stale `localState` Reference Causes Wrong App in Folder

**File**: `ArchivedAppsAdapter.kt` (line 375–416), `ArchiveFragment.kt` (line 249–287)

**Problem**: When a drag starts, the `ArchivedApp` data object snapshot is passed as `localState` in `startDragAndDrop()`. If the underlying data changes during the drag (e.g., a Firestore/Room sync fires mid-drag), the `localState` still holds the old reference. In `ACTION_DROP`, the code uses this stale object to look up the latest state via `viewModel.archivedApps.value.firstOrNull { it.packageId == draggedApp.packageId }`. This is defensive — **but** the `onAppDropOnApp` callback in `ArchiveFragment.handleAppDropOnApp()` (line 862) re-fetches `latestDragged` and `latestTarget`, which is correct. However, if the package was **deleted** between drag start and drop (e.g., undo-delete timing), `latestDragged` or `latestTarget` will be `null` and the function returns silently, with **no user feedback**.

Additionally, the `pendingDropAction` pattern has a race: `ACTION_DROP` sets `pendingDropAction`, then `ACTION_DRAG_ENDED` fires and reads it. Both happen on the main thread so the ordering is correct for a single listener — but multiple `DragListener` instances (one per ViewHolder) all receive `DRAG_ENDED`. Only the first one to run `finalizeDragSession` will execute the drop action, but later ones still enter the method. The `dragEndScheduled` flag partially guards this, but it's an adapter-level flag shared across all listeners, which is fragile.

**Fix**:

```kotlin
// ArchivedAppsAdapter.kt — finalizeDragSession()
// Add defensive null check + user feedback for \"app disappeared during drag\"
private fun finalizeDragSession(
    recyclerView: RecyclerView?,
    endingView: View,
    endingViewOriginalBackground: android.graphics.drawable.Drawable?,
    dropAction: (() -> Unit)?
) {
    // Guard: if this session was already finalized, bail out
    if (!dragInFlight && !dragEndScheduled) {
        android.util.Log.v(TAG, \"session=$activeDragSessionId FINALIZE already completed, skip\")
        return
    }
    try {
        // ... existing cleanup code ...
        
        if (dropAction != null) {
            android.util.Log.d(TAG, \"session=$activeDragSessionId FINALIZE execute drop action\")
            runCatching { dropAction.invoke() }
                .onFailure {
                    android.util.Log.e(TAG, \"session=$activeDragSessionId FINALIZE drop action failed\", it)
                }
        }
    } finally {
        draggingViewRef = null
        draggingPackageId = null
        dragInFlight = false
        dragEndScheduled = false
        pendingDropAction = null
    }
}
```

```kotlin
// ArchiveFragment.kt — handleAppDropOnApp()
// Add a Snackbar when the app disappears during drag
private fun handleAppDropOnApp(draggedApp: ArchivedApp, targetApp: ArchivedApp) {
    if (draggedApp.packageId == targetApp.packageId) return
    val apps = viewModel.archivedApps.value
    val latestDragged = apps.firstOrNull { it.packageId == draggedApp.packageId }
    val latestTarget = apps.firstOrNull { it.packageId == targetApp.packageId }

    if (latestDragged == null || latestTarget == null) {
        // App was deleted/modified during drag
        Snackbar.make(requireView(), \"App was modified during drag. Try again.\", Snackbar.LENGTH_SHORT).show()
        return
    }
    // ... rest of existing logic ...
}
```

---

### 1.3 [HIGH] Drag-and-Drop — Source View Visibility Leak (Invisible Ghost Items)

**File**: `ArchivedAppsAdapter.kt` (line 155–198, 464–538)

**Problem**: When a drag starts, the source view is set to `View.INVISIBLE` (line 161). If the drag is cancelled without `ACTION_DRAG_ENDED` being delivered to the correct listener (e.g., the RecyclerView was scrolled and the ViewHolder recycled mid-drag), the view remains `INVISIBLE` permanently. The `onViewRecycled` override (line 541) resets visibility, but this only fires when the VH is recycled — not when it's rebound in place. If the user sees a \"blank\" cell where an app icon should be, this is the cause.

The `finalizeDragSession` method tries to find the source via `WeakReference` and then falls back to a linear scan. But if `notifyDataSetChanged` was called (e.g., by `renderArchivedApps`), the `tag` on the child views may no longer match the dragged package ID.

**Fix**: Add a safety net in `onBindViewHolder` to guarantee visibility reset:

```kotlin
// ArchivedAppsAdapter.kt — onBindViewHolder()
override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    // Safety: always ensure the item view is visible when bound.
    // Prevents ghost items from a drag that didn't clean up properly.
    holder.itemView.animate().cancel()
    pulseAnimators.remove(holder.itemView)?.cancel()
    holder.itemView.visibility = View.VISIBLE
    holder.itemView.alpha = 1f
    holder.itemView.scaleX = 1f
    holder.itemView.scaleY = 1f

    val item = getItem(position)
    if (holder is AppViewHolder && item is ArchivedItem.App) {
        holder.bind(item)
    } else if (holder is FolderViewHolder && item is ArchivedItem.Folder) {
        holder.bind(item)
    } else if (holder is AppListViewHolder && item is ArchivedItem.App) {
        holder.bind(item)
    } else if (holder is FolderListViewHolder && item is ArchivedItem.Folder) {
        holder.bind(item)
    }
}
```

Each `bind()` method already resets these properties, but having the reset in `onBindViewHolder` guarantees it for all view types and catches edge cases where a VH is rebound without being recycled first.

---

### 1.4 [HIGH] Folder Overlay — Crash When `anchorView` Is Null + Card Pivot Off-Screen

**File**: `FolderExpandOverlay.kt` (line 150–202)

**Problem**: When `showFolderOverlay` is called from `handleAppDropOnApp` after a drag-to-create-folder, `anchorView` is passed as `null` (line 896 in `ArchiveFragment.kt`). The animation code then computes `anchorCenterX/Y` as 0,0 (since `anchorView?.width` is `null` → 0). This causes the card to scale from the top-left corner instead of the folder position, which looks jarring.

On API 31+ the blur `setRenderEffect` is applied to the scrim — but `RenderEffect.createBlurEffect` on a solid-color `View` has no visual effect (it blurs the View's own rendering, not what's behind it). The blur is cosmetic-only and silently does nothing.

**Fix**:

```kotlin
// FolderExpandOverlay.kt — show()
// When no anchor, default to screen center for a natural animation origin
val anchorCenterX: Float
val anchorCenterY: Float

if (anchorView != null) {
    val anchorLocation = IntArray(2)
    val parentLocation = IntArray(2)
    anchorView.getLocationInWindow(anchorLocation)
    parentView.getLocationInWindow(parentLocation)
    anchorCenterX = (anchorLocation[0] - parentLocation[0] + anchorView.width / 2).toFloat()
    anchorCenterY = (anchorLocation[1] - parentLocation[1] + anchorView.height / 2).toFloat()
} else {
    // Fallback: animate from screen center
    anchorCenterX = parentView.width / 2f
    anchorCenterY = parentView.height / 2f
}
```

For the blur fix, if you want actual background blur on API 31+:

```kotlin
// Apply blur to the ROOT frame (not just the scrim) so it blurs the content behind
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Remove blur from scrim (it has no effect on a solid color View)
    // Instead, apply it to the parent root which actually has content behind it
    root.setRenderEffect(
        android.graphics.RenderEffect.createBlurEffect(
            15f, 15f,
            android.graphics.Shader.TileMode.CLAMP
        )
    )
}

// And in dismiss():
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    root.setRenderEffect(null)
}
```

**Note**: Even the root approach only blurs _its own children_, not the content behind the overlay. For true background blur, you'd need to use `Window.setBackgroundBlurRadius()` on a Dialog window, or render the background to a bitmap and blur it manually. Consider removing the `setRenderEffect` call entirely if you don't want to invest in a proper blur implementation — the scrim alpha already provides adequate visual separation.

---

### 1.5 [HIGH] Folder Overlay — Drag From Folder Triggers Immediate Removal Without Cleanup

**File**: `FolderExpandOverlay.kt` (line 113–133), `FolderAppsAdapter.kt` (line 87–118)

**Problem**: When a user long-presses an app inside the expanded folder to drag it out, the overlay is **immediately removed** from the parent (`parentView.removeView(overlayView)`) on the same frame. This is intentional to allow the drag shadow to appear over the main grid. However:

1. The `view.post { onDragStartFromFolder?.invoke() }` means the overlay removal runs asynchronously via `post`, but `view.visibility = View.INVISIBLE` runs synchronously. There's a 1-frame window where the view is invisible inside the overlay, then the overlay is removed — creating a visual \"flash\" where the icon disappears before the drag shadow appears.
2. `animateAnchorFolderIcons(visible = true, animate = false)` restores folder icons instantly, but the underlying RecyclerView may not have re-rendered yet, causing a frame where both the folder cell and the expanded overlay are gone.

**Fix**: Move the visibility set into the `post` block to ensure atomicity, and force a RecyclerView layout pass after overlay removal:

```kotlin
// FolderAppsAdapter.kt — onBindViewHolder long-click listener
holder.itemView.setOnLongClickListener { view ->
    try {
        val clipData = ClipData.newPlainText(\"packageId\", app.packageId)
        val shadowBuilder = ScaledDragShadowBuilder(holder.icon, 1.1f)

        // DON'T hide the view before starting drag — let the overlay handle it
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clipData, shadowBuilder, app, 0)
        } else {
            @Suppress(\"DEPRECATION\")
            view.startDrag(clipData, shadowBuilder, app, 0)
        }
        if (started) {
            // Hide AFTER drag started so shadow builder already captured the view
            view.visibility = View.INVISIBLE
            view.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            // Use post to ensure the drag shadow is already attached before removing overlay
            view.post { onDragStartFromFolder?.invoke() }
        }
        started
    } catch (t: Throwable) {
        android.util.Log.e(TAG, \"FOLDER longPress exception pkg=${app.packageId}\", t)
        throw t
    }
}
```

```kotlin
// FolderExpandOverlay.kt — onDragStartFromFolder callback
// After removing the overlay, request a layout pass
onDragStartFromFolder = {
    // Restore hidden views first
    for (i in 0 until grid.childCount) {
        val child = grid.getChildAt(i)
        if (child.visibility == View.INVISIBLE) {
            child.visibility = View.VISIBLE
            child.alpha = 1f
            child.scaleX = 1f
            child.scaleY = 1f
        }
    }
    parentView.removeView(overlayView)
    overlayView = null
    isExpanded = false
    animateAnchorFolderIcons(visible = true, animate = false)
    
    // Force the RecyclerView to re-layout immediately
    val rv = parentView.findViewWithTag<RecyclerView>(\"archive_recycler\")
    rv?.requestLayout()
    
    onDismiss()
    onDragStartFromFolder?.invoke()
}
```

---

### 1.6 [MEDIUM] Singleton Folder Collapse Race Condition

**File**: `ArchiveFragment.kt` (line 694–727)

**Problem**: `collapseSingletonFoldersIfNeeded` fires on every `renderArchivedApps` call. It updates the app in the ViewModel (`viewModel.updateArchivedApp(app.copy(folderName = null))`), which triggers a Room/Firestore write. If the write is slow and `renderArchivedApps` is called again before the write completes, the same app could be collapsed twice. The `singletonCollapseInFlight` guard prevents this for the same package ID, but the guard is only in-memory and not tied to the actual write completion.

More importantly, after a drag-to-create-folder, there's a `pendingFolderCreationWindowMs = 3_000L` grace period. But if the user creates a folder and immediately deletes one of the two apps from it (via swipe-to-delete or the detail popup), the remaining app becomes a singleton. The 3-second window may not have expired, so the singleton is preserved. But after 3 seconds, on the next render, the singleton is auto-collapsed — which is correct behavior but might surprise users if they manually renamed the folder.

**Fix**: Add a check for folders that have been renamed by the user (i.e., folder name != \"New Folder\" pattern):

```kotlin
// ArchiveFragment.kt — collapseSingletonFoldersIfNeeded()
private fun collapseSingletonFoldersIfNeeded(apps: List<ArchivedApp>) {
    val now = System.currentTimeMillis()
    pendingFolderCreations.entries.removeAll { it.value < now }

    val folderCounts = apps
        .mapNotNull { it.folderName }
        .groupingBy { it }
        .eachCount()

    folderCounts.filterValues { it >= 2 }.keys
        .forEach { pendingFolderCreations.remove(it) }

    val singletonApps = apps.filter { app ->
        val folder = app.folderName
        folder != null &&
            (folderCounts[folder] ?: 0) == 1 &&
            !pendingFolderCreations.containsKey(folder) &&
            // Don't auto-collapse user-renamed folders — they represent intent
            isDefaultFolderName(folder)
    }
    // ... rest of existing logic ...
}

private fun isDefaultFolderName(name: String): Boolean {
    if (name == \"New Folder\") return true
    return name.matches(Regex(\"New Folder \\d+\"))
}
```

---

## 2. UI/UX Issues

### 2.1 [HIGH] Drag Highlight — Hardcoded Color in `bg_drag_target_highlight.xml`

**File**: `res/drawable/bg_drag_target_highlight.xml`

**Problem**: The highlight uses `android:color=\"#1A6750A4\"` (hardcoded purple with 10% alpha) and `android:color=\"?attr/colorPrimary\"` for the stroke. The solid fill color doesn't adapt to the theme (light vs dark mode). In dark mode, the purple tint looks washed out against dark backgrounds.

**Fix**:

```xml
<!-- bg_drag_target_highlight.xml -->
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:shape=\"rectangle\">
    <corners android:radius=\"16dp\" />
    <stroke
        android:width=\"2dp\"
        android:color=\"?attr/colorPrimary\"
        android:dashWidth=\"6dp\"
        android:dashGap=\"4dp\" />
    <!-- Use a theme-aware color instead of hardcoded purple -->
    <solid android:color=\"@color/drag_highlight_fill\" />
</shape>
```

```xml
<!-- res/values/colors.xml -->
<color name=\"drag_highlight_fill\">#1A6750A4</color>

<!-- res/values-night/colors.xml -->
<color name=\"drag_highlight_fill\">#2DCBB1FF</color>
```

Alternatively, create a `ColorStateList` or use `?attr/colorPrimaryContainer` with alpha at runtime in the adapter.

---

### 2.2 [HIGH] Folder Icon Background — Hardcoded Gray in `bg_folder_icon.xml`

**File**: `res/drawable/bg_folder_icon.xml`

**Problem**: The folder icon background uses `#40808080` (gray with 25% alpha). This doesn't adapt to light/dark theme and looks muddy in both modes. In dark mode, the gray-on-dark-gray has poor contrast. In light mode, it's too subtle.

**Fix**:

```xml
<!-- bg_folder_icon.xml — Use theme-aware surface variant -->
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:shape=\"rectangle\">
    <corners android:radius=\"16dp\" />
    <solid android:color=\"?attr/colorSurfaceContainerHighest\" />
</shape>
```

This uses Material 3's surface tint system which automatically adapts to light/dark themes.

---

### 2.3 [HIGH] Reinstalled Page — Back Button Uses Wrong Icon

**File**: `fragment_archive.xml` (line 299–307)

**Problem**: The back button for the reinstalled page uses `@android:drawable/ic_media_previous` (a media \"rewind\" icon). This is semantically wrong for navigation — it should be a back arrow. This confuses users about the button's purpose.

**Fix**:

```xml
<!-- fragment_archive.xml — Replace media icon with proper back arrow -->
<ImageButton
    android:id=\"@+id/btn_reinstalled_back\"
    android:layout_width=\"40dp\"
    android:layout_height=\"40dp\"
    android:background=\"?attr/selectableItemBackgroundBorderless\"
    android:contentDescription=\"Back\"
    android:padding=\"8dp\"
    android:src=\"@drawable/ic_arrow_back\"
    android:tint=\"?attr/colorOnSurface\" />
```

If you don't have `ic_arrow_back`, create one or use `@android:drawable/ic_menu_revert` as a closer match, or add the Material icon:

```xml
<!-- res/drawable/ic_arrow_back.xml -->
<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:width=\"24dp\"
    android:height=\"24dp\"
    android:viewportWidth=\"24\"
    android:viewportHeight=\"24\"
    android:tint=\"?attr/colorControlNormal\">
    <path
        android:fillColor=\"@android:color/white\"
        android:pathData=\"M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z\" />
</vector>
```

---

### 2.4 [HIGH] App Detail Popup — NestedScrollView + WRAP_CONTENT Height Causes Overflow

**File**: `dialog_app_details.xml`, `DashboardModalDialogWrapper.kt`

**Problem**: The app detail dialog uses `match_parent` height on the root `MaterialCardView` and `WRAP_CONTENT` on the dialog window (via `DashboardModalDialogWrapper`). When the notes section has long text, the dialog can overflow the screen height on smaller devices. The `NestedScrollView` handles scrolling, but since the card is `match_parent` height, it expands to fill the available space even when content is short, creating unnecessary whitespace.

**Fix**: Change the root card height to `wrap_content` and add a `maxHeight` constraint:

```xml
<!-- dialog_app_details.xml — Root card -->
<com.google.android.material.card.MaterialCardView
    ...
    android:layout_width=\"match_parent\"
    android:layout_height=\"wrap_content\"
    ... />
```

And in `NativeAppDetailsDialog.kt`, use a `fixedHeightFraction` to cap the dialog at 85% screen height:

```kotlin
// NativeAppDetailsDialog.kt — show()
dialog = DashboardModalDialogWrapper(
    context = context,
    contentLayoutRes = R.layout.dialog_app_details,
    dismissOnOutside = true,
    maxWidthDp = 640,
    horizontalMarginDp = 12,
    fixedHeightFraction = null, // Let it wrap, but add a max
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
).build().apply {
    // ... existing setup ...
    
    // Cap the dialog to 85% screen height to prevent overflow
    window?.decorView?.post {
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.85f).toInt()
        val currentHeight = window?.decorView?.height ?: 0
        if (currentHeight > maxHeight) {
            window?.setLayout(window?.attributes?.width ?: 0, maxHeight)
        }
    }
}
```

---

### 2.5 [MEDIUM] Bulk Review Popup — ViewPager2 Inside NestedScrollView Scroll Conflict

**File**: `dialog_bulk_review.xml`, `NativeBulkReviewDialog.kt`

**Problem**: `ViewPager2` is placed inside a `NestedScrollView` (line 45–95 of `dialog_bulk_review.xml`). ViewPager2 uses its own `RecyclerView` internally. This creates a scroll conflict: vertical swipes are ambiguous between the NestedScrollView's vertical scroll and any future vertical content in the pager items. Currently it works because the ViewPager2 only handles horizontal swipes, but the NestedScrollView wrapping adds unnecessary complexity and can cause janky measure passes.

Additionally, the `ViewPager2` has `android:layout_height=\"wrap_content\"`, but ViewPager2's internal RecyclerView does not support `wrap_content` natively (it's a known Android issue). This means all pages are measured once, and if page heights differ, the ViewPager2 won't resize per-page.

**Fix**: Remove the `NestedScrollView` wrapper and let the dialog handle its own sizing:

```xml
<!-- dialog_bulk_review.xml — Simplified structure -->
<com.google.android.material.card.MaterialCardView ...>
    <LinearLayout
        android:layout_width=\"match_parent\"
        android:layout_height=\"match_parent\"
        android:orientation=\"vertical\"
        android:paddingHorizontal=\"20dp\"
        android:paddingTop=\"20dp\"
        android:paddingBottom=\"16dp\">

        <!-- Title section -->
        <LinearLayout ... >
            <TextView android:id=\"@+id/tv_bulk_review_title\" ... />
            <TextView android:id=\"@+id/tv_bulk_review_subtitle\" ... />
        </LinearLayout>

        <!-- ViewPager — takes available space -->
        <androidx.viewpager2.widget.ViewPager2
            android:id=\"@+id/view_pager\"
            android:layout_width=\"match_parent\"
            android:layout_height=\"0dp\"
            android:layout_weight=\"1\"
            android:layout_marginTop=\"14dp\" />

        <!-- Dots -->
        <LinearLayout
            android:id=\"@+id/dots_container\"
            android:layout_width=\"match_parent\"
            android:layout_height=\"wrap_content\"
            android:layout_marginTop=\"12dp\"
            android:gravity=\"center\"
            android:orientation=\"horizontal\" />

        <!-- Context text -->
        <TextView android:id=\"@+id/tv_bulk_review_context\" ... />

        <!-- Buttons -->
        <LinearLayout ... >
            <com.google.android.material.button.MaterialButton android:id=\"@+id/btn_skip_all\" ... />
            <Space ... />
            <com.google.android.material.button.MaterialButton android:id=\"@+id/btn_next\" ... />
            <com.google.android.material.button.MaterialButton android:id=\"@+id/btn_done\" ... />
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

And use `fixedHeightFraction` in the dialog wrapper to give the ViewPager2 a defined height:

```kotlin
// NativeBulkReviewDialog.kt
private val dialog: Dialog = DashboardModalDialogWrapper(
    context = context,
    contentLayoutRes = R.layout.dialog_bulk_review,
    dismissOnOutside = false,
    maxWidthDp = 680,
    horizontalMarginDp = 12,
    fixedHeightFraction = 0.7f  // 70% of screen height
).build()
```

---

### 2.6 [MEDIUM] Bulk Review Popup — No Keyboard Dismiss on Swipe

**File**: `NativeBulkReviewDialog.kt`

**Problem**: When the user types a note and swipes to the next page, the keyboard stays open. On some devices it obscures the navigation dots and buttons. The keyboard should dismiss on page change.

**Fix**:

```kotlin
// NativeBulkReviewDialog.kt — Add keyboard dismiss on page change
viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
        // Dismiss keyboard when swiping between pages
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        viewPager.windowToken?.let { token ->
            imm.hideSoftInputFromWindow(token, 0)
        }
        
        // Save the note from the previous page before moving on
        val prevPosition = if (position > 0) position - 1 else null
        prevPosition?.let { saveCurrentPage(it) }
        
        dots.forEachIndexed { index, dot ->
            dot.setBackgroundResource(
                if (index == position) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
        btnNext.visibility = if (position < archivedApps.size - 1) View.VISIBLE else View.GONE
        btnDone.visibility = if (position == archivedApps.size - 1) View.VISIBLE else View.GONE
    }
})
```

---

### 2.7 [MEDIUM] Bulk Review Popup — Dots Overflow for Large App Counts

**File**: `NativeBulkReviewDialog.kt` (line 58–71)

**Problem**: When archiving many apps (e.g., 15+), the dot indicators overflow horizontally. Each dot is 8dp + 8dp margin = 16dp. At 15 apps, that's 240dp — which exceeds many phone widths minus dialog padding.

**Fix**: Add a `HorizontalScrollView` wrapper, or better, switch to a text indicator for large counts:

```kotlin
// NativeBulkReviewDialog.kt — Setup dots
if (archivedApps.size <= 8) {
    // Use dots for small counts
    dotsContainer.visibility = View.VISIBLE
    val dotSize = (8 * context.resources.displayMetrics.density).toInt()
    val dotMargin = (4 * context.resources.displayMetrics.density).toInt()
    for (i in archivedApps.indices) {
        val dot = View(context).apply {
            val lp = LinearLayout.LayoutParams(dotSize, dotSize)
            lp.setMargins(dotMargin, 0, dotMargin, 0)
            layoutParams = lp
            setBackgroundResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
        }
        dots.add(dot)
        dotsContainer.addView(dot)
    }
} else {
    // Use text indicator for large counts: \"3 of 15\"
    dotsContainer.visibility = View.GONE
    // Add a text indicator (you'll need to add this TextView to the layout)
    val pageIndicator = dialog.findViewById<TextView>(R.id.tv_page_indicator)
    pageIndicator?.visibility = View.VISIBLE
    pageIndicator?.text = \"1 of ${archivedApps.size}\"
}
```

---

### 2.8 [MEDIUM] Archive Page — Chip Container Visibility Flicker

**File**: `ArchiveFragment.kt` (line 577–579)

**Problem**: When there's only one category (\"All\"), the `chipContainerScrollView` is set to `View.INVISIBLE` (not `GONE`). This means it still occupies vertical space, creating a mysterious gap between the search bar and the grid. This should be `GONE` to reclaim the space, or the entire `categoryBar` should be hidden.

**Fix**:

```kotlin
// ArchiveFragment.kt — updateUI()
if (categories.size > 1) {
    chipContainerScrollView.visibility = View.VISIBLE
    // ... existing chip creation code ...
} else {
    // Use GONE instead of INVISIBLE to reclaim vertical space
    chipContainerScrollView.visibility = View.GONE
}
```

---

### 2.9 [MEDIUM] Drag-and-Drop — No Visual \"Cancel\" Animation on Empty Drop

**File**: `ArchivedAppsAdapter.kt` (line 464–538)

**Problem**: When a user drags an app and drops it on empty space (not on another app or folder), the source icon just \"pops\" back with the `restoreDraggedSourceView` spring animation. There's no visual indication that the drop was cancelled. This feels unpolished compared to Pixel Launcher which shows a distinct \"snap-back\" with a slight overshoot.

**Fix**: Add a distinct \"cancelled\" animation with a shake effect:

```kotlin
// ArchivedAppsAdapter.kt — Add a \"cancelled drop\" animation
private fun restoreDraggedSourceViewCancelled(view: View) {
    view.animate().cancel()
    view.visibility = View.VISIBLE
    view.alpha = 0f
    view.scaleX = 0.8f
    view.scaleY = 0.8f
    
    // Quick scale-up with overshoot + subtle horizontal shake
    view.animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(350)
        .setInterpolator(OvershootInterpolator(2.0f))
        .withEndAction {
            // Subtle shake to indicate \"nope, didn't land\"
            val shakeAnimator = ObjectAnimator.ofFloat(view, \"translationX\", 0f, -6f, 6f, -4f, 4f, 0f)
            shakeAnimator.duration = 250
            shakeAnimator.start()
        }
        .start()
}
```

Then in `finalizeDragSession`, use `restoreDraggedSourceViewCancelled` when there's no drop action:

```kotlin
if (dropAction != null) {
    restoreDraggedSourceView(dragSource)  // Successful drop
    runCatching { dropAction.invoke() }
} else {
    restoreDraggedSourceViewCancelled(dragSource)  // Cancelled
}
```

---

### 2.10 [LOW] App Detail Popup — Notes Starts in EDIT Mode for Empty Notes

**File**: `ArchiveNotesStateMachine.kt` (line 17–18)

**Problem**: The state machine initializes in `EDIT` mode when `savedText` is blank. This means every time a user opens an app detail that has no notes, the text field is immediately in edit mode with cursor blinking. This is aggressive — most users open the popup to view info, not to write notes. It should default to VIEW mode with a clear \"Add notes\" prompt.

**Fix**:

```kotlin
// ArchiveNotesStateMachine.kt
internal class ArchiveNotesStateMachine(initialText: String?) {
    private var savedText: String = initialText?.trim().orEmpty()
    private var draftText: String = savedText
    // Always start in VIEW mode — the placeholder text communicates editability
    private var mode: ArchiveNotesMode = ArchiveNotesMode.VIEW
}
```

Update the render logic in `ArchiveNotesCardMolecule.kt` to show a more inviting empty state:

```kotlin
// ArchiveNotesCardMolecule.kt — render()
private fun render() {
    val state = stateMachine.state()
    val hasSavedText = state.savedText.isNotBlank()
    notesSavedText.text = if (hasSavedText) {
        state.savedText
    } else {
        notesSavedText.context.getString(R.string.archive_popup_notes_empty)
    }
    notesSavedText.alpha = if (hasSavedText) 1f else 0.5f
    inlineEditButton.text = if (hasSavedText) {
        notesSavedText.context.getString(R.string.archive_popup_edit)
    } else {
        \"Add\" // Clearer CTA for empty state
    }
    // ... rest unchanged ...
}
```

---

### 2.11 [LOW] App Detail Popup — Reinstall Button Steals Focus on Show

**File**: `NativeAppDetailsDialog.kt` (line 87–89)

**Problem**: `setOnShowListener { btnReinstall.requestFocus() }` forces focus to the reinstall button when the dialog appears. On devices with keyboard/d-pad navigation this highlights the button with a focus ring, which may surprise users. On touch-only devices it has no visible effect but still moves the accessibility focus.

**Fix**: Remove the forced focus or only apply it for accessibility navigation:

```kotlin
// NativeAppDetailsDialog.kt — Remove forced focus
// setOnShowListener { btnReinstall.requestFocus() }  // Remove this line
```

If you want to support keyboard/d-pad navigation, set initial focus on the card itself instead:

```kotlin
setOnShowListener {
    // Only focus if not in touch mode (keyboard/d-pad navigation)
    if (!btnReinstall.isInTouchMode) {
        btnReinstall.requestFocus()
    }
}
```

---

## 3. Performance Issues

### 3.1 [HIGH] Duplicate Bitmap Cache in `ArchivedAppsAdapter` and `FolderAppsAdapter`

**File**: `ArchivedAppsAdapter.kt` (line 578–606), `FolderAppsAdapter.kt` (line 26–55)

**Problem**: Both adapters maintain their own independent `LruCache<String, Bitmap>` for icon decoding. The main adapter allocates 8MB and the folder adapter allocates 4MB — that's 12MB of heap dedicated to duplicate icon caches. When an app appears in both the grid and the folder overlay, the same bitmap is decoded and stored twice.

**Fix**: Extract the bitmap cache into a shared singleton:

```kotlin
// Create a new file: IconBitmapCache.kt
package com.tool.decluttr.presentation.screens.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.tool.decluttr.domain.model.ArchivedApp

object IconBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getOrDecode(app: ArchivedApp): Bitmap? {
        val bytes = app.iconBytes ?: return null
        var bmp = cache.get(app.packageId)
        if (bmp == null) {
            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                cache.put(app.packageId, bmp)
            }
        }
        return bmp
    }

    fun evict(packageId: String) {
        cache.remove(packageId)
    }
}
```

Then in both adapters, replace the private cache with:

```kotlin
// In ArchivedAppsAdapter.kt and FolderAppsAdapter.kt
private fun loadIcon(imageView: ImageView, app: ArchivedApp) {
    val bmp = IconBitmapCache.getOrDecode(app)
    if (bmp != null) {
        imageView.setImageBitmap(bmp)
    } else {
        imageView.load(AppIconModel(app.packageId)) {
            memoryCacheKey(app.packageId)
            size(coil.size.Size.ORIGINAL)
            crossfade(false)
            placeholder(R.drawable.ic_launcher)
            error(R.drawable.ic_launcher)
        }
    }
}
```

---

### 3.2 [MEDIUM] Chip Reconstruction on Every `updateUI` Call

**File**: `ArchiveFragment.kt` (line 561–579)

**Problem**: Every call to `updateUI()` removes all chips from `chipContainer` and recreates them. `updateUI()` is called on every text change in the search bar, every category select, and every `archivedApps` emission. This causes unnecessary layout passes and GC pressure from abandoned `Chip` views.

**Fix**: Only reconstruct chips when the category list actually changes:

```kotlin
// ArchiveFragment.kt — Add a field to track current categories
private var currentCategories: List<String> = emptyList()

// In updateUI():
if (categories != currentCategories) {
    currentCategories = categories
    if (categories.size > 1) {
        chipContainerScrollView.visibility = View.VISIBLE
        chipContainer.removeAllViews()
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = selectedCategory == category
                setOnClickListener {
                    selectedCategory = category
                    updateUI(viewModel.archivedApps.value)
                }
                val margin = (8 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = margin }
            }
            chipContainer.addView(chip)
        }
    } else {
        chipContainerScrollView.visibility = View.GONE
    }
} else {
    // Just update checked state without recreating
    for (i in 0 until chipContainer.childCount) {
        val chip = chipContainer.getChildAt(i) as? Chip ?: continue
        chip.isChecked = chip.text == selectedCategory
    }
}
```

---

### 3.3 [MEDIUM] `getInstalledPackageIds()` Called on Every UI Update

**File**: `ArchiveFragment.kt` (line 787–802, 529–540)

**Problem**: `updateUI()` calls `getInstalledPackageIds()` which queries `PackageManager.getInstalledApplications(0)` — a system call that iterates all installed packages. The 8-second TTL cache helps, but on rapid UI updates (e.g., during drag-and-drop where updates are deferred), the cache expires and re-queries happen frequently.

**Fix**: Increase TTL and move the cache refresh to a lifecycle-aware coroutine:

```kotlin
// ArchiveFragment.kt
private val installedPackagesCacheTtlMs: Long = 30_000L  // Increase from 8s to 30s

// Add a proactive refresh on fragment resume
override fun onResume() {
    super.onResume()
    // Proactively refresh the cache on resume, when the user has likely installed/uninstalled apps
    installedPackagesCacheAt = 0L  // Invalidate
    getInstalledPackageIds()  // Warm the cache
}
```

---

### 3.4 [LOW] `FolderAppsAdapter.updateData()` Uses `notifyDataSetChanged()`

**File**: `FolderAppsAdapter.kt` (line 61–64)

**Problem**: `updateData` calls `notifyDataSetChanged()` which forces a full rebind of every item. For small folders this is fine, but it prevents RecyclerView animations (item add/remove).

**Fix**: Use `DiffUtil` for proper change detection:

```kotlin
// FolderAppsAdapter.kt
fun updateData(newApps: List<ArchivedApp>) {
    val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
        override fun getOldListSize() = apps.size
        override fun getNewListSize() = newApps.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) =
            apps[oldPos].packageId == newApps[newPos].packageId
        override fun areContentsTheSame(oldPos: Int, newPos: Int) =
            apps[oldPos] == newApps[newPos]
    })
    apps = newApps
    diffResult.dispatchUpdatesTo(this)
}
```

---

## 4. Code Quality Issues

### 4.1 [HIGH] Excessive Verbose Logging in Production Code

**File**: Multiple — `ArchiveFragment.kt`, `ArchivedAppsAdapter.kt`, `FolderExpandOverlay.kt`, `FolderAppsAdapter.kt`

**Problem**: There are 50+ `android.util.Log.v/d` calls throughout the drag-and-drop code, all tagged `DecluttrDragDbg`. While useful for development, these add overhead in production (string interpolation + logcat I/O). More critically, they log potentially sensitive information like package IDs and folder names.

**Fix**: Wrap all debug logging behind a `BuildConfig` check or use a dedicated logger:

```kotlin
// Create a utility: DragDebugLog.kt
package com.tool.decluttr.presentation.screens.dashboard

import android.util.Log
import com.tool.decluttr.BuildConfig

internal object DragDebugLog {
    private const val TAG = \"DecluttrDragDbg\"
    
    inline fun v(message: () -> String) {
        if (BuildConfig.DEBUG) Log.v(TAG, message())
    }
    
    inline fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
    
    inline fun e(message: () -> String, throwable: Throwable? = null) {
        // Errors should always log
        Log.e(TAG, message(), throwable)
    }
}
```

Then replace all logging calls:

```kotlin
// Before:
android.util.Log.v(TAG, \"archivedApps.collect size=${apps.size} dragInProgress=$isDragInProgress\")

// After:
DragDebugLog.v { \"archivedApps.collect size=${apps.size} dragInProgress=$isDragInProgress\" }
```

The `inline` + lambda pattern means the string interpolation is never executed in release builds.

---

### 4.2 [MEDIUM] Duplicated `toDisplayName` / `humanizePackageId` Logic

**File**: `ArchivedAppsAdapter.kt` (line 613–637), `FolderAppsAdapter.kt` (line 127–151)

**Problem**: Identical helper functions are copy-pasted in both adapters. If one is updated (e.g., to handle a new edge case), the other will diverge.

**Fix**: Extract to a shared utility:

```kotlin
// Create: PackageNameFormatter.kt
package com.tool.decluttr.presentation.util

object PackageNameFormatter {
    fun toDisplayName(name: String?, packageId: String): String {
        val raw = name?.trim().orEmpty()
        if (raw.isNotBlank() && !isLikelyPackageId(raw)) return raw
        return humanizePackageId(packageId)
    }

    private fun isLikelyPackageId(value: String): Boolean {
        return value.contains('.') && value == value.lowercase()
    }

    private fun humanizePackageId(packageId: String): String {
        val segments = packageId.split('.').filter { it.isNotBlank() }
        var token = segments.lastOrNull().orEmpty()
        if (token.length <= 2 && segments.size > 1) {
            token = segments[segments.lastIndex - 1]
        }
        val cleaned = token.replace('_', ' ').replace('-', ' ')
            .replace(Regex(\"([a-z])([A-Z])\"), \"$1 $2\")
            .trim()
        if (cleaned.isBlank()) return packageId
        return cleaned.split(Regex(\"\\s+\"))
            .joinToString(\" \") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}
```

---

### 4.3 [MEDIUM] `DashboardModalDialogWrapper` — Missing Animation Cleanup on Config Change

**File**: `DashboardModalDialogWrapper.kt`

**Problem**: The dialog wrapper creates dialogs that aren't lifecycle-aware. If a configuration change occurs (rotation, dark mode toggle) while the dialog is showing, the dialog leaks its window. This is standard Android Dialog behavior, but since these are created with `Dialog(context)` (not `DialogFragment`), they bypass Fragment lifecycle management.

**Fix**: Consider migrating critical dialogs to `DialogFragment` or `BottomSheetDialogFragment`:

```kotlin
// For NativeAppDetailsDialog — migrate to DialogFragment pattern
class AppDetailsDialogFragment : DialogFragment() {
    companion object {
        private const val ARG_PACKAGE_ID = \"package_id\"
        
        fun newInstance(packageId: String) = AppDetailsDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_PACKAGE_ID, packageId) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val packageId = requireArguments().getString(ARG_PACKAGE_ID)!!
        // Build dialog using DashboardModalDialogWrapper as before
        // ...
    }
}
```

This ensures the dialog survives configuration changes and is properly dismissed when the hosting Fragment is destroyed.

---

### 4.4 [LOW] Empty State Logic Duplicated Between Grid and List Mode

**File**: `ArchiveFragment.kt` (line 598–631, 661–691)

**Problem**: The empty state handling (showing `emptyStateContainer`, setting `tvEmptyMessage`, toggling `btnFindApps`/`btnArchiveLogin`) is nearly identical between the grid mode and list mode branches of `updateUI()`. This violates DRY and makes it easy to introduce inconsistencies.

**Fix**: Extract to a helper:

```kotlin
// ArchiveFragment.kt
private fun showEmptyState(visibleArchiveApps: List<ArchivedApp>) {
    recyclerView.visibility = View.GONE
    emptyStateContainer.visibility = View.VISIBLE
    
    if (visibleArchiveApps.isEmpty()) {
        val loggedIn = viewModel.isLoggedIn.value
        val isAuthLoading = isGoogleSignInLoading || authViewModel.isLoading.value

        when {
            loggedIn == null || isAuthLoading -> {
                tvEmptyMessage.text = \"\"
                btnFindApps.visibility = View.GONE
                btnArchiveLogin.visibility = View.GONE
            }
            !loggedIn -> {
                tvEmptyMessage.text = getString(R.string.archive_empty_message_logged_out)
                btnFindApps.visibility = View.GONE
                btnArchiveLogin.visibility = View.VISIBLE
            }
            else -> {
                tvEmptyMessage.text = getString(R.string.archive_empty_message)
                btnFindApps.visibility = View.VISIBLE
                btnArchiveLogin.visibility = View.GONE
            }
        }
    } else {
        tvEmptyMessage.text = getString(R.string.archive_empty_filtered)
        btnFindApps.visibility = View.GONE
        btnArchiveLogin.visibility = View.GONE
    }
}

private fun showContent(items: List<ArchivedItem>) {
    recyclerView.visibility = View.VISIBLE
    emptyStateContainer.visibility = View.GONE
    adapter.submitList(items)
}
```

---

### 4.5 [LOW] ScaledDragShadowBuilder — Double `canvas.restore()` Without Matching Saves

**File**: `ScaledDragShadowBuilder.kt` (line 26–39)

**Problem**: The `onDrawShadow` method calls `canvas.save()`, then `canvas.saveLayerAlpha()`, then two `canvas.restore()` calls. This is technically correct (one restore for `saveLayerAlpha`, one for `save`), but the ordering is fragile. If any code is added between them, the stack could be unbalanced. Modern Kotlin convention prefers explicit scope management.

**Fix**: Use a cleaner pattern:

```kotlin
override fun onDrawShadow(canvas: Canvas) {
    val v = view ?: return
    val saveCount = canvas.save()
    canvas.scale(scaleFactor, scaleFactor)
    canvas.saveLayerAlpha(
        0f, 0f,
        v.width.toFloat(), v.height.toFloat(),
        230
    )
    v.draw(canvas)
    canvas.restoreToCount(saveCount)
}
```

`restoreToCount` is safer — it restores to the exact save point regardless of how many intermediate saves were made.

---

## Summary of Priority Actions

| Priority | Count | Key Items |
|----------|-------|-----------|
| CRITICAL | 2     | Ghost touch passthrough on reinstalled page; stale drag localState |
| HIGH     | 7     | Invisible ghost items; folder overlay null anchor; folder drag flash; drag highlight theming; folder icon theming; back button icon; dialog overflow |
| MEDIUM   | 7     | Singleton folder race; scroll conflict in bulk review; keyboard on swipe; dot overflow; chip flicker; chip rebuild perf; installed packages perf |
| LOW      | 5     | Notes auto-edit; focus steal; folder adapter DiffUtil; empty state duplication; canvas restore pattern |

### Recommended Fix Order

1. **Reinstalled page touch passthrough** (CRITICAL, 5-minute fix)
2. **Drag source view visibility leak** (HIGH, safety net in `onBindViewHolder`)
3. **Folder overlay null anchor fallback** (HIGH, center fallback)
4. **Theme-aware drag highlight + folder icon colors** (HIGH, XML changes)
5. **Back button icon replacement** (HIGH, XML change)
6. **Bulk review scroll conflict + keyboard handling** (MEDIUM)
7. **Performance: chip caching + bitmap cache consolidation** (MEDIUM)
8. **Logging cleanup** (HIGH for production release)
9. **Remaining LOW items** (polish pass)
"
Observation: Create successful: /app/decluttr_analysis_and_fixes.md

Fetching debug logs for expo

==> /var/log/supervisor/expo.err.log <==

==> /var/log/supervisor/expo.out.log <==
Starting project at /app/frontend
Fast resolver is enabled.
Metro is running in CI mode, reloads are disabled. Remove CI=true to enable watch mode.
Starting Metro Bundler
Tunnel connected.
Tunnel ready.
Waiting on http://localhost:3000
Logs for your project will appear below.
yarn run v1.22.22
$ /app/frontend/node_modules/.bin/expo start --tunnel --port 3000
env: load .env
env: export EXPO_TUNNEL_SUBDOMAIN EXPO_PACKAGER_HOSTNAME EXPO_PUBLIC_BACKEND_URL EXPO_USE_FAST_RESOLVER METRO_CACHE_ROOT
Starting project at /app/frontend
Fast resolver is enabled.
Metro is running in CI mode, reloads are disabled. Remove CI=true to enable watch mode.
Starting Metro Bundler
Tunnel connected.
Tunnel ready.
Waiting on http://localhost:3000
Logs for your project will appear below.


Fetching debug logs for backend

==> /var/log/supervisor/backend.err.log <==
INFO:     Will watch for changes in these directories: ['/app/backend']
INFO:     Uvicorn running on http://0.0.0.0:8001 (Press CTRL+C to quit)
INFO:     Started reloader process [123] using WatchFiles
INFO:     Started server process [147]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Shutting down
INFO:     Waiting for application shutdown.
INFO:     Application shutdown complete.
INFO:     Finished server process [147]
INFO:     Stopping reloader process [123]
INFO:     Will watch for changes in these directories: ['/app/backend']
INFO:     Uvicorn running on http://0.0.0.0:8001 (Press CTRL+C to quit)
INFO:     Started reloader process [280] using WatchFiles
INFO:     Started server process [312]
INFO:     Waiting for application startup.
INFO:     Application startup complete.

==> /var/log/supervisor/backend.out.log <==


Fetching debug logs for mongodb

tail: cannot open '/var/log/supervisor/mongodb*.log' for reading: No such file or directory
