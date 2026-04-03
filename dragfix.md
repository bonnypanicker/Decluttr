
**Repository:** https://github.com/bonnypanicker/Decluttr.git  
**Scope:** Archive page — drag-and-drop icon / folder creation  
**Symptoms reported:**
1. Drag an icon a little and release → icon permanently disappears
2. Drag an icon on top of another → app crashes

---

## Summary

There are **5 distinct bugs** in the drag-and-drop system. Two of them are crash-level; the rest produce the \"icon disappears\" symptom. All bugs are in:
- `ArchivedAppsAdapter.kt`
- `FolderAppsAdapter.kt`
- `ArchiveFragment.kt`

---

## Bug 1 — CRASH (Icon disappears permanently)
### Unchecked return value of `startDragAndDrop()`

**Files:** `ArchivedAppsAdapter.kt` lines 112–121, `FolderAppsAdapter.kt` lines 55–61

**What happens:**  
Both adapters call `view.startDragAndDrop(...)` and then unconditionally set `view.visibility = View.INVISIBLE` — regardless of whether the drag actually started.

```kotlin
// ArchivedAppsAdapter.kt — AppViewHolder.bind()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    view.startDragAndDrop(clipData, shadowBuilder, app, 0)  // return value IGNORED
} else {
    view.startDrag(clipData, shadowBuilder, app, 0)         // return value IGNORED
}
view.visibility = View.INVISIBLE  // always runs, even if drag never started
```

`startDragAndDrop()` returns `false` (and fires NO drag events) when:
- A drag operation is already in progress (rapid double long-press)
- The view has been detached from the window
- A system-level rejection occurs

When it returns `false`, `ACTION_DRAG_ENDED` is **never dispatched**. The restoration code that scans for `INVISIBLE` views never runs. The icon stays hidden for the lifetime of the app session.

**Fix:**
```kotlin
val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    view.startDragAndDrop(clipData, shadowBuilder, app, 0)
} else {
    @Suppress(\"DEPRECATION\")
    view.startDrag(clipData, shadowBuilder, app, 0)
}
if (started) {
    view.visibility = View.INVISIBLE
    // haptic feedback here
}
```

---

## Bug 2 — CRASH (App crashes when dropping icon on another icon)
### `DefaultItemAnimator.animateRemove()` conflicts with drag-end `ViewPropertyAnimator`

**Files:** `ArchivedAppsAdapter.kt` lines 291–306, `ArchiveFragment.kt` lines 144–154

**What happens:**  
When App A is dropped onto App B to create a folder, this sequence occurs:

1. `ACTION_DRAG_ENDED` fires → restoration code runs on App A's view:
   ```kotlin
   child.visibility = View.VISIBLE
   child.alpha = 0f
   child.scaleX = 0.5f
   child.scaleY = 0.5f
   child.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)...start()
   // ↑ ViewPropertyAnimator #1 is now running on App A's view
   ```

2. `rv.post { dropAction.invoke() }` runs → `viewModel.updateArchivedApp(...)` fires for both apps → Room emits → `updateUI()` → `adapter.submitList(newList)` — list now has a Folder instead of App A and App B.

3. `DiffUtil` computes diffs: App A and App B are **removed**. `DefaultItemAnimator.animateRemove(holder)` is called on App A's `ViewHolder`:
   ```kotlin
   // Inside DefaultItemAnimator — simplified
   ViewCompat.animate(view).alpha(0f).setDuration(200).setListener(removeListener).start()
   // ↑ ViewPropertyAnimator #2 cancels #1 and fades the view OUT
   ```

4. When ViewPropertyAnimator #2 completes, `dispatchRemoveFinished(holder)` puts the holder into the recycle pool. If ViewPropertyAnimator #1's listener callback fires AFTER the holder is recycled (timing-dependent), it references a stale/reused view — **causing an `IllegalStateException` or a visual state corruption that triggers a crash.**

The same dual-animation race occurs in `ArchiveFragment.setupRecyclerView()`, which has a **second** restoration scan inside `rv.post { ... }` that also calls `child.animate()...start()` on the same views already being animated by `DefaultItemAnimator`.

**Fix:**  
Cancel any running `ViewPropertyAnimator` on the view before `DefaultItemAnimator` touches it. The cleanest approach is to call `child.animate().cancel()` before setting the alpha/scale for restoration, AND to wrap the drop action execution only after the DiffUtil animation has settled (using a `RecyclerView.ItemAnimator` end listener, or simply using `rv.postDelayed(..., addDuration + removeDuration)`).

```kotlin
// In DRAG_ENDED restoration loop:
if (child.visibility == View.INVISIBLE) {
    child.animate().cancel()         // cancel any in-progress animator first
    child.visibility = View.VISIBLE
    child.alpha = 0f
    child.scaleX = 0.5f
    child.scaleY = 0.5f
    child.animate().alpha(1f).scaleX(1f).scaleY(1f)
        .setDuration(300).setInterpolator(OvershootInterpolator(1.5f)).start()
}
```

---

## Bug 3 — Icon disappears (scroll during drag)
### Source view recycled off-screen during drag — never restored

**File:** `ArchivedAppsAdapter.kt` lines 289–307

**What happens:**  
The `DRAG_ENDED` restoration loop only scans `rv.childCount` (currently laid-out views):
```kotlin
for (i in 0 until rv.childCount) {
    val child = rv.getChildAt(i)
    if (child.visibility == View.INVISIBLE) { ... }
}
```

`RecyclerView.childCount` only includes views **currently visible on screen** (and a small off-screen buffer). If the user scrolls during the drag, the source view scrolls off-screen, is recycled back into the `RecycledViewPool`, and is no longer in `childCount`. The restoration loop never finds it. The next time that ViewHolder is reused for a different item, `bind()` does not explicitly reset `visibility`, so the new item also appears invisible.

**Fix:**  
Store the source `ViewHolder` (or its `adapterPosition`) when the drag starts, and reference it directly in `DRAG_ENDED`:

```kotlin
// In adapter:
private var draggingItemPosition: Int = RecyclerView.NO_ID.toInt()

// In onLongClickListener (after drag starts successfully):
draggingItemPosition = bindingAdapterPosition

// In DRAG_ENDED, instead of scanning childCount:
val holder = recyclerView?.findViewHolderForAdapterPosition(draggingItemPosition)
holder?.itemView?.let { v ->
    v.animate().cancel()
    v.visibility = View.VISIBLE
    v.alpha = 0f
    v.animate().alpha(1f)...start()
}
draggingItemPosition = RecyclerView.NO_ID.toInt()
```

---

## Bug 4 — Visual Corruption / Potential Infinite Animation Leak
### Orphaned `INFINITE` `pulseAnimator` on recycled FolderViewHolder

**File:** `ArchivedAppsAdapter.kt` lines 210–222

**What happens:**  
When the drag cursor enters a Folder item, a pulse animation is started with `repeatCount = INFINITE`:
```kotlin
pulseAnimator = ObjectAnimator.ofFloat(view, \"scaleX\", 1.0f, 1.08f).apply {
    repeatCount = android.animation.ValueAnimator.INFINITE
    start()
}
```

`pulseAnimator` is a field on the **`DragListener` instance**. Each call to `bind()` creates a **new** `DragListener` and calls `itemView.setOnDragListener(DragListener())`. If `submitList()` is called while the pulse animator is running (e.g., triggered by a real-time Firebase sync update), `DiffUtil` may rebind or recycle the `FolderViewHolder`. A new `DragListener` is created with `pulseAnimator = null`. The old `DragListener` still holds the running `ObjectAnimator`, but it is now unreachable and uncancellable. The animator continues animating `scaleX` on the now-reused view indefinitely, corrupting the visual state of whichever item is bound to that ViewHolder next.

**Fix:**  
Move `pulseAnimator` to the `FolderViewHolder` itself (not the DragListener), and cancel it in `onViewRecycled`:
```kotlin
// In FolderViewHolder:
var pulseAnimator: ObjectAnimator? = null

// In ArchivedAppsAdapter:
override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
    super.onViewRecycled(holder)
    if (holder is FolderViewHolder) {
        holder.pulseAnimator?.cancel()
        holder.pulseAnimator = null
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
    }
}
```

---

## Bug 5 — Icon disappears (drag from folder overlay)
### Source view in `FolderExpandOverlay` never restored after overlay is dismissed

**Files:** `FolderAppsAdapter.kt` lines 61–71, `FolderExpandOverlay.kt` lines 111–118

**What happens:**  
When a user long-presses an icon inside the expanded folder overlay:
1. `view.visibility = View.INVISIBLE` (in `FolderAppsAdapter` ViewHolder)
2. `onDragStartFromFolder?.invoke()` is called
3. This immediately calls `parentView.removeView(overlayView)` — the overlay is torn from the window while the drag shadow is still active
4. `ACTION_DRAG_ENDED` fires on the **main archive RecyclerView**'s children — they scan `rv.childCount` of the main RecyclerView only, never finding the now-destroyed overlay view
5. The invisible view inside the removed overlay is garbage-collected still invisible — nobody ever resets it

This is not a crash but means the drag-from-folder feature silently corrupts view state.

**Fix:**  
Before removing the overlay view, reset the invisible source view's visibility:
```kotlin
onDragStartFromFolder = {
    // Reset visibility of the dragged view before removing the overlay
    val grid = overlayView?.findViewById<RecyclerView>(R.id.folder_grid)
    grid?.let { rv ->
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            if (child.visibility == View.INVISIBLE) {
                child.visibility = View.VISIBLE
            }
        }
    }
    parentView.removeView(overlayView)
    overlayView = null
    isExpanded = false
    onDismiss()
    onDragStartFromFolder?.invoke()
}
```

---

## Priority Order for Fixes

| # | Bug | Severity | Symptom |
|---|-----|----------|---------|
| 1 | `startDragAndDrop()` return value unchecked | **P0 — Crash** | Icon permanently disappears |
| 2 | `DefaultItemAnimator` vs drag-end `ViewPropertyAnimator` conflict | **P0 — Crash** | Crash when drop creates folder |
| 3 | Source view recycled off-screen during drag | **P1 — Broken feature** | Icon disappears on scroll |
| 4 | Orphaned `INFINITE` pulseAnimator | **P1 — Visual corruption** | Folder items animate forever |
| 5 | Folder overlay source view not restored | **P2 — Visual glitch** | Drag-from-folder broken |

---

## Files to Edit

| File | Lines to change |
|------|-----------------|
| `ArchivedAppsAdapter.kt` | Lines 112–121 (Bug 1), Lines 291–306 (Bug 2), Lines 210–222 (Bug 4), Add `onViewRecycled` override (Bug 4), DRAG_ENDED loop (Bug 3) |
| `FolderAppsAdapter.kt` | Lines 55–61 (Bug 1) |
| `FolderExpandOverlay.kt` | Lines 111–118 (Bug 5) |
| `ArchiveFragment.kt` | Lines 144–154 (duplicate restoration, Bug 2 contributor) |

---

