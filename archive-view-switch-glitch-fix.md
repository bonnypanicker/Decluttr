# Decluttr — Archive Grid/List Switch Glitch: Root Cause & Fix

---

## What the glitch looks like

When tapping the view switch button:
- **Grid → List:** Items briefly flash as list-rows squeezed into a 4-column grid before reflowing
- **List → Grid:** Items briefly flash as grid squares stacked in a single column before reflowing
- A flicker or jump animation plays over the reflow

---

## Root cause: three operations fire in the wrong order with animations running

**`ArchiveFragment.kt` line 331–343:**

```kotlin
btnViewSwitch.setOnClickListener {
    isListMode = !isListMode
    adapter.setListMode(isListMode)          // ← STEP 1: rebinds all items
    recyclerView.layoutManager = if (...) {  // ← STEP 2: swaps layout manager
        LinearLayoutManager(...)
    } else {
        GridLayoutManager(..., 4)
    }
    updateUI(viewModel.archivedApps.value)   // ← STEP 3: submitList + DiffUtil
}
```

**`ArchivedAppsAdapter.kt` line 80–84:**

```kotlin
fun setListMode(enabled: Boolean) {
    if (isListMode != enabled) {
        isListMode = enabled
        notifyDataSetChanged()   // ← fires immediately, LayoutManager not swapped yet
    }
}
```

### The three-frame glitch sequence

```
Frame 1 — notifyDataSetChanged() fires (Step 1)
    RecyclerView rebinds every item with the new viewType
    (list viewType=2/3 or grid viewType=0/1)
    BUT the LayoutManager is still the OLD one
    ┌─────────────────────────────────┐
    │  [LIST ROW] [LIST ROW] [LIST ROW] [LIST ROW]   ← list items forced into 4-col grid
    └─────────────────────────────────┘
    ← USER SEES THIS FRAME (the glitch)

Frame 2 — layoutManager swap fires (Step 2)
    RecyclerView detects new LayoutManager, triggers full relayout
    DefaultItemAnimator plays move animations on every item (moveDuration = 300ms)
    ← USER SEES ITEMS SLIDING AROUND

Frame 3 — submitList fires (Step 3)
    DiffUtil computes diff, dispatches change animations on top of still-running move animations
    Two animation passes overlap → second visible jank
```

Additionally, `recyclerView.layoutAnimation` is set to a `LayoutAnimationController` with `fade_in` (line 208–211). This fires again when the LayoutManager is replaced, causing a third animation on top of the two already running.

---

## Fix — 3 changes to `ArchiveFragment.kt` only

### Change 1 — Correct the operation order and suppress animations during switch

Replace the entire `btnViewSwitch.setOnClickListener` block:

```kotlin
// ArchiveFragment.kt
btnViewSwitch.setOnClickListener {
    isListMode = !isListMode

    // 1. Kill all animations before touching anything
    //    Prevents the 3-frame flicker entirely
    val previousAnimator = recyclerView.itemAnimator
    recyclerView.itemAnimator = null
    recyclerView.layoutAnimation = null    // suppress the fade-in controller during switch

    // 2. Swap LayoutManager FIRST — before the adapter knows about the mode change
    recyclerView.layoutManager = if (isListMode) {
        LinearLayoutManager(requireContext())
    } else {
        GridLayoutManager(requireContext(), 4).also { glm ->
            // Restore span size lookup for folders (full-width rows)
            glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (adapter.getItemViewType(position)) {
                        1 -> 4   // ArchivedItem.Folder in grid mode → full width
                        else -> 1
                    }
                }
            }
        }
    }

    // 3. Tell adapter about mode change WITHOUT notifyDataSetChanged
    //    submitList in updateUI will handle the full rebind in one pass
    adapter.setListModeQuiet(isListMode)

    // 4. Update icon and sort button visibility
    btnViewSwitch.setImageResource(
        if (isListMode) R.drawable.ic_grid_view else R.drawable.ic_list
    )
    btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE

    // 5. Submit new list — single rebind pass, no animation fighting
    updateUI(viewModel.archivedApps.value)

    // 6. Restore animator after one frame so subsequent item changes animate normally
    recyclerView.post {
        recyclerView.itemAnimator = previousAnimator
    }
}
```

### Change 2 — Add `setListModeQuiet` to `ArchivedAppsAdapter`

The existing `setListMode` always calls `notifyDataSetChanged()`. Add a quiet variant that only updates the flag — `submitList()` from `updateUI()` handles the rebind:

```kotlin
// ArchivedAppsAdapter.kt

// Existing method — keep for any call sites that need it
fun setListMode(enabled: Boolean) {
    if (isListMode != enabled) {
        isListMode = enabled
        notifyDataSetChanged()
    }
}

// New: updates the flag without triggering a redundant notify
// Use this when submitList() will immediately follow
fun setListModeQuiet(enabled: Boolean) {
    isListMode = enabled
}
```

### Change 3 — Add `SpanSizeLookup` for folders in initial GridLayoutManager setup

The initial setup on line 204 also creates a `GridLayoutManager` without a `SpanSizeLookup`. Folders should span all 4 columns in grid mode. Add it once in `setupRecyclerView()`:

```kotlin
// ArchiveFragment.kt — in setupRecyclerView(), replace line 204:

// BEFORE:
recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)

// AFTER:
recyclerView.layoutManager = GridLayoutManager(requireContext(), 4).also { glm ->
    glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return when (adapter.getItemViewType(position)) {
                1 -> 4   // viewType 1 = ArchivedItem.Folder in grid mode
                else -> 1
            }
        }
    }
}
```

---

## Why this works

```
Before fix (3 frames, 2 conflicting animations):
  notifyDataSetChanged (wrong LM) → LM swap (move anim) → submitList (change anim)

After fix (1 frame, 0 animations during switch):
  LM swap (no adapter attached yet) → setListModeQuiet (flag only) → submitList (single clean rebind)
  Animator restored on next frame for future interactions
```

`submitList()` with DiffUtil already handles the efficient diff between old and new items. By removing `notifyDataSetChanged()` and the intermediate LM swap, RecyclerView only ever sees one consistent state transition — the correct items bound to the correct views in the correct layout.

---

## Files changed

| File | Change |
|---|---|
| `ArchiveFragment.kt` | Reorder toggle operations, suppress animator, add `SpanSizeLookup` in both GridLayoutManager creations |
| `ArchivedAppsAdapter.kt` | Add `setListModeQuiet(enabled: Boolean)` |

*Generated April 2026 against Decluttr-main v4*
