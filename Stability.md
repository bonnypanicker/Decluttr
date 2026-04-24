Good, I see there's already an analysis file. Now I have all the context I need. Let me create the comprehensive fix documentation for the two specific issues the user mentioned.
Action: file_editor create /app/Decluttr/archive_drag_shadow_and_folder_animation_fix.md --file-text "# Decluttr Archive — Drag Shadow & Folder Animation Fix Guide

> **Scope**: Two issues on the Archive page
> 1. Square shadow flash on first press when starting a drag
> 2. Folder open/close animation not originating from folder icon; needs to feel more organic (Pixel Launcher QPR3 style)
>
> **Target**: Native Android (Kotlin)
> **No code edits in repo** — this file documents the root cause analysis and exact code changes needed.

---

## Table of Contents

1. [Issue 1: Square Shadow Flash on Drag Start](#issue-1-square-shadow-flash-on-drag-start)
   - [Root Cause Analysis](#11-root-cause-analysis)
   - [Fix Option A — Remove Shadow Completely (Recommended)](#12-fix-option-a--remove-shadow-completely-recommended)
   - [Fix Option B — Make Shadow Circular (Fallback)](#13-fix-option-b--make-shadow-circular-fallback)
2. [Issue 2: Folder Animation — Organic Pixel Launcher Style](#issue-2-folder-animation--organic-pixel-launcher-style)
   - [Root Cause Analysis](#21-root-cause-analysis)
   - [Fix — Rewrite Open/Close Animation](#22-fix--rewrite-openclose-animation)
3. [Files Changed Summary](#files-changed-summary)

---

## Issue 1: Square Shadow Flash on Drag Start

### 1.1 Root Cause Analysis

**Symptom**: On the Archive page, when the user long-presses an app icon to begin dragging, a square/rectangular shadow background flashes for one frame behind the icon, accompanied by haptic feedback. It then disappears once the drag shadow takes over.

**Files involved**:
- `app/src/main/res/layout/item_archived_app.xml` (line 8)
- `ArchivedAppsAdapter.kt` → `AppViewHolder.bind()` (lines 154–207)
- `FolderAppsAdapter.kt` → `onBindViewHolder()` (lines 79–112)

**Root cause**: The `item_archived_app.xml` layout has:
```xml
android:background=\"?android:attr/selectableItemBackground\"
```

This is a `RippleDrawable` that draws a **rectangular** ripple feedback. When the user long-presses:

1. Android's touch system immediately activates the `selectableItemBackground` ripple in its **pressed** state — this draws a rectangular highlight/ripple behind the icon.
2. The `onLongClickListener` fires and the code does:
   ```kotlin
   view.isPressed = false
   view.background?.state = intArrayOf()
   view.visibility = View.INVISIBLE
   ```
3. However, `isPressed = false` and `state = intArrayOf()` don't take effect until the **next draw pass**. The current frame has already committed the rectangular pressed-state background to the display pipeline.
4. On the very next frame `view.visibility = View.INVISIBLE` hides the view, but by then one frame of the rectangle has already been rendered and perceived by the user (especially with haptic feedback drawing attention to it).

**Why `view.background?.state = intArrayOf()` isn't enough**: The `RippleDrawable` uses its own internal animation state that doesn't respond to a simple state array reset. It needs either:
- `jumpDrawablesToCurrentState()` to skip to the final state without animation
- Or the background needs to be temporarily removed entirely

The same issue exists in `FolderAppsAdapter.kt` (lines 79–112) for dragging from within an expanded folder, though it doesn't attempt the pressed-state cleanup at all.

---

### 1.2 Fix Option A — Remove Shadow Completely (Recommended)

**Strategy**: Temporarily null out the background before making the view invisible, preventing any ripple frame from rendering. The background is automatically restored when the view is rebound by the adapter.

#### Change 1: `ArchivedAppsAdapter.kt` — `AppViewHolder.bind()` (around line 154)

Replace the existing long-click listener block (lines 154–207) with:

```kotlin
itemView.setOnLongClickListener { view ->
    onAppStartDrag(app)

    // 1. Create clip data for the drag
    val clipData = ClipData.newPlainText(\"packageId\", app.packageId)

    // 2. Build a scaled-up shadow from the icon (not the whole itemView)
    val shadowBuilder = ScaledDragShadowBuilder(icon, 1.1f)

    // 3. CRITICAL: Kill the ripple drawable BEFORE the next draw frame.
    //    Setting isPressed = false is not enough because RippleDrawable
    //    animates its exit and will render one \"pressed\" frame.
    //    Nulling the background prevents any ripple from being drawn.
    view.isPressed = false
    view.background = null          // <-- KEY CHANGE: remove ripple entirely
    view.visibility = View.INVISIBLE

    // 4. Start drag
    val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        view.startDragAndDrop(clipData, shadowBuilder, app, 0)
    } else {
        @Suppress(\"DEPRECATION\")
        view.startDrag(clipData, shadowBuilder, app, 0)
    }

    if (started) {
        dragSessionCounter += 1L
        activeDragSessionId = dragSessionCounter
        draggingPackageId = app.packageId
        draggingViewRef = WeakReference(view)
        pendingDropAction = null
        dragInFlight = true
        dragEndScheduled = false
        android.util.Log.d(
            TAG,
            \"session=$activeDragSessionId START_DRAG pkg=${app.packageId} pos=${bindingAdapterPosition} view=${describeView(view)}\"
        )

        // Haptic feedback like Pixel Launcher
        view.performHapticFeedback(
            android.view.HapticFeedbackConstants.LONG_PRESS,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    } else {
        // Drag failed – restore visibility and background
        view.visibility = View.VISIBLE
        view.setBackgroundResource(android.R.attr.selectableItemBackground.let {
            val attrs = intArrayOf(it)
            val ta = view.context.obtainStyledAttributes(attrs)
            val resId = ta.getResourceId(0, 0)
            ta.recycle()
            resId
        })
        android.util.Log.w(
            TAG,
            \"session=$activeDragSessionId START_DRAG_FAILED pkg=${app.packageId}\"
        )
    }

    started
}
```

**Why this works**: By setting `view.background = null` before `view.visibility = View.INVISIBLE`, there is no `RippleDrawable` to render on the current frame. The `ScaledDragShadowBuilder` already holds a direct reference to the `icon` ImageView and calls `icon.draw(canvas)` in its `onDrawShadow()`, so the drag shadow renders correctly regardless of the parent's background or visibility state.

The background doesn't need manual restoration because:
- On successful drag → view gets rebound by `onBindViewHolder` when the list updates (which re-inflates the XML background)
- On failed drag → we restore it in the `else` block

**Simpler alternative** (if you want to avoid the restoration complexity):

Instead of `view.background = null`, you can force the ripple to jump to its un-pressed state instantly:

```kotlin
view.isPressed = false
view.background?.let { bg ->
    bg.state = intArrayOf()  // clear state flags
    if (bg is android.graphics.drawable.RippleDrawable) {
        bg.setVisible(false, false)   // force ripple invisible without exit animation
    }
}
view.jumpDrawablesToCurrentState()    // <-- KEY: skip any in-progress ripple animation
view.visibility = View.INVISIBLE
```

This is less invasive and doesn't require background restoration.

#### Change 2: `FolderAppsAdapter.kt` — `onBindViewHolder()` (around line 79)

Apply the same fix to the folder-apps long-click handler. Currently it doesn't clear pressed state at all:

```kotlin
holder.itemView.setOnLongClickListener { view ->
    try {
        android.util.Log.d(TAG, \"FOLDER longPress start pkg=${app.packageId}\")
        val clipData = ClipData.newPlainText(\"packageId\", app.packageId)
        val shadowBuilder = ScaledDragShadowBuilder(holder.icon, 1.1f)

        // Kill ripple before drag starts
        view.isPressed = false
        view.background = null
        
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clipData, shadowBuilder, app, 0)
        } else {
            @Suppress(\"DEPRECATION\")
            view.startDrag(clipData, shadowBuilder, app, 0)
        }
        if (started) {
            view.visibility = View.INVISIBLE
            view.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            view.post { onDragStartFromFolder?.invoke() }
        } else {
            // Restore if drag failed
            view.visibility = View.VISIBLE
            android.util.Log.w(TAG, \"FOLDER drag failed to start pkg=${app.packageId}\")
        }
        started
    } catch (t: Throwable) {
        android.util.Log.e(TAG, \"FOLDER longPress exception pkg=${app.packageId}\", t)
        throw t
    }
}
```

---

### 1.3 Fix Option B — Make Shadow Circular (Fallback)

If you want to keep the press feedback but make it circular instead of rectangular:

#### Change: `item_archived_app.xml` (line 8)

Replace:
```xml
android:background=\"?android:attr/selectableItemBackground\"
```
With:
```xml
android:background=\"?android:attr/selectableItemBackgroundBorderless\"
```

**What this does**: `selectableItemBackgroundBorderless` renders a **circular** ripple centered on the touch point, unbounded by the view's rectangular bounds. The flash will still appear for one frame but it will be circular and centered on the icon, which looks much more natural.

**Also apply to**: `item_archived_folder.xml` (line 9) — same attribute for consistency.

**Pros**: Simple XML-only change, no Kotlin changes needed.
**Cons**: You still see a very brief circular flash. Combine with Option A's `jumpDrawablesToCurrentState()` for the cleanest result.

---

## Issue 2: Folder Animation — Organic Pixel Launcher Style

### 2.1 Root Cause Analysis

**Symptom**: When a folder opens (either by tapping or after drag-creating), the expand animation doesn't feel like it originates from the folder icon. The close animation similarly doesn't collapse back to the folder's position.

**Files involved**:
- `FolderExpandOverlay.kt` (all of it, especially lines 147–220 open, 236–289 close)
- `ArchiveFragment.kt` → `handleAppDropOnApp()` (line 885) and `showFolderOverlay()` (line 904)

**Root causes** (multiple):

1. **`anchorView` is null when folder is created via drag-drop**: In `handleAppDropOnApp()` (line 885), the folder overlay is shown with `showFolderOverlay(defaultName, null)`. When `anchorView` is null, the pivot calculation falls back to `(0, 0)` because:
   ```kotlin
   val anchorCenterX = (anchorLocation[0] - parentLocation[0] +
       (anchorView?.width ?: 0) / 2).toFloat()
   // anchorView is null → anchorLocation is [0,0], width is 0
   // Result: anchorCenterX = -parentLocation[0], which is wrong
   ```
   This causes the card to scale from the top-left corner of the screen instead of from the newly-created folder.

2. **Pivot is set after `card.post{}`**: The card's pivot is calculated inside `card.post{}` (line 184), but the card's initial scale (0.2f) is set BEFORE `post`. This means on the first frame the card is rendered at 0.2x scale with the default pivot (center), then on the next frame the pivot jumps to the calculated position. This creates a visual \"jump\" that makes the animation feel disconnected.

3. **Spring parameters are too bouncy and too slow**: The open animation uses:
   ```kotlin
   SpringForce.STIFFNESS_LOW      // 200f — very slow, takes too long to settle
   SpringForce.DAMPING_RATIO_LOW_BOUNCY  // 0.2f — excessive bounce
   ```
   The close animation uses:
   ```kotlin
   SpringForce.STIFFNESS_MEDIUM   // 1500f
   SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY  // 0.5f
   ```
   Pixel Launcher QPR3 uses a quicker, more controlled spring. The open should feel like a confident expansion; the close should feel like it \"sucks back\" into the folder icon.

4. **No content stagger**: All folder contents (icons, title) appear simultaneously, making the open feel flat. Pixel Launcher staggers the content appearance.

5. **Close animation doesn't recalculate pivot**: If the RecyclerView scrolled or re-laid-out during the folder being open, the anchor view position may have changed. The close animation does recalculate (lines 246-258), but it fails silently if `anchorViewRef?.get()` returns null (view was recycled).

---

### 2.2 Fix — Rewrite Open/Close Animation

Replace the animation sections in `FolderExpandOverlay.kt`. Here's the complete rewrite:

#### Open Animation (replace lines 147–220)

```kotlin
// ── OPEN ANIMATION ──

// Calculate anchor position for animation origin
val anchorLocation = IntArray(2)
val parentLocation = IntArray(2)
parentView.getLocationInWindow(parentLocation)

val hasAnchor = anchorView != null
if (hasAnchor) {
    anchorView!!.getLocationInWindow(anchorLocation)
}

// Scrim fade in — slightly slower for a more cinematic feel
scrim.alpha = 0f
scrim.animate()
    .alpha(1f)
    .setDuration(300)
    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
    .start()

// Apply blur if API 31+
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    scrim.setRenderEffect(
        android.graphics.RenderEffect.createBlurEffect(
            20f, 20f,
            android.graphics.Shader.TileMode.CLAMP
        )
    )
}

// Card: start fully invisible and at tiny scale.
// We set the initial state but defer the actual spring animation 
// until after layout so the pivot is correct from frame one.
card.alpha = 0f
card.scaleX = 0f
card.scaleY = 0f

// Hide content initially for staggered reveal
val contentLayout = card.getChildAt(0) // LinearLayout inside card
contentLayout?.alpha = 0f

card.post {
    val cardLocation = IntArray(2)
    card.getLocationInWindow(cardLocation)

    if (hasAnchor) {
        // Compute pivot relative to card's bounds
        val anchorWindowX = anchorLocation[0] + (anchorView?.width ?: 0) / 2
        val anchorWindowY = anchorLocation[1] + (anchorView?.height ?: 0) / 2
        card.pivotX = (anchorWindowX - cardLocation[0]).toFloat()
        card.pivotY = (anchorWindowY - cardLocation[1]).toFloat()
    } else {
        // No anchor (drag-created folder) — expand from center of card
        card.pivotX = card.width / 2f
        card.pivotY = card.height / 2f
    }

    // Phase 1: Card scale-up with spring (Pixel Launcher \"dolly zoom\" feel)
    //   - STIFFNESS_MEDIUM (1500f) for a quick, confident expansion
    //   - DAMPING_RATIO 0.65 for a slight bounce that settles fast
    val targetScale = 1f
    val stiffness = 800f  // between LOW (200) and MEDIUM (1500) — organic feel
    val dampingRatio = 0.65f  // slightly bouncy but controlled

    val scaleXSpring = SpringAnimation(card, DynamicAnimation.SCALE_X).apply {
        spring = SpringForce(targetScale)
            .setStiffness(stiffness)
            .setDampingRatio(dampingRatio)
    }
    val scaleYSpring = SpringAnimation(card, DynamicAnimation.SCALE_Y).apply {
        spring = SpringForce(targetScale)
            .setStiffness(stiffness)
            .setDampingRatio(dampingRatio)
    }

    // Fade in the card outline quickly
    card.animate()
        .alpha(1f)
        .setDuration(120)
        .start()

    scaleXSpring.setStartValue(0.15f).start()
    scaleYSpring.setStartValue(0.15f).start()

    // Phase 2: Content fade-in after card starts expanding (staggered)
    // Delay slightly so the card \"arrives\" first, then content materializes
    contentLayout?.animate()
        ?.alpha(1f)
        ?.setStartDelay(100)
        ?.setDuration(200)
        ?.setInterpolator(android.view.animation.DecelerateInterpolator())
        ?.start()
}
```

**Key improvements**:
- `card.scaleX/Y = 0f` (was 0.2f) — starts smaller for more dramatic expansion
- Pivot defaults to card center when `anchorView` is null (was broken, defaulting to top-left)
- Spring stiffness 800f (was 200f STIFFNESS_LOW) — settles much faster
- Damping ratio 0.65f (was 0.2f LOW_BOUNCY) — controlled bounce, not wobbly
- Content fades in with 100ms delay — staggered reveal like Pixel Launcher
- Blur radius increased to 20f (was 15f) for more depth

#### Close Animation (replace lines 236–289 in `dismiss()`)

```kotlin
fun dismiss(onDismiss: () -> Unit) {
    android.util.Log.d(TAG, \"FOLDER_OVERLAY dismiss requested isExpanded=$isExpanded\")
    if (!isExpanded) return
    isExpanded = false

    val overlay = overlayView ?: return
    val scrim = overlay.findViewById<View>(R.id.folder_scrim)
    val card = overlay.findViewById<MaterialCardView>(R.id.folder_card)
    val contentLayout = card.getChildAt(0)

    // Recalculate pivot for close animation
    val anchorView = anchorViewRef?.get()
    if (anchorView != null && anchorView.isAttachedToWindow) {
        val anchorLocation = IntArray(2)
        anchorView.getLocationInWindow(anchorLocation)
        val cardLocation = IntArray(2)
        card.getLocationInWindow(cardLocation)

        val anchorWindowX = anchorLocation[0] + anchorView.width / 2
        val anchorWindowY = anchorLocation[1] + anchorView.height / 2

        card.pivotX = (anchorWindowX - cardLocation[0]).toFloat()
        card.pivotY = (anchorWindowY - cardLocation[1]).toFloat()
    }
    // If no anchor, pivot stays where it was set during open (center or anchor)

    // Phase 1: Content fades out quickly
    contentLayout?.animate()
        ?.alpha(0f)
        ?.setDuration(80)
        ?.start()

    // Phase 2: Card springs back to small scale (Pixel QPR3 \"bouncy jiggle\" close)
    val closeStiffness = 1200f   // faster than open for a \"snap back\" feel
    val closeDamping = 0.72f     // slight bounce on close, but quick settle
    val closeScale = 0.05f       // shrink to near-nothing (was 0.2f)

    val scaleXSpring = SpringAnimation(card, DynamicAnimation.SCALE_X).apply {
        spring = SpringForce(closeScale)
            .setStiffness(closeStiffness)
            .setDampingRatio(closeDamping)
    }
    val scaleYSpring = SpringAnimation(card, DynamicAnimation.SCALE_Y).apply {
        spring = SpringForce(closeScale)
            .setStiffness(closeStiffness)
            .setDampingRatio(closeDamping)
    }

    // Card fades out as it shrinks
    card.animate()
        .alpha(0f)
        .setDuration(180)
        .start()

    scaleXSpring.start()
    scaleYSpring.start()
    animateAnchorFolderIcons(visible = true, animate = true)

    // Phase 3: Scrim fades out and cleanup
    scrim.animate()
        .alpha(0f)
        .setDuration(250)
        .withEndAction {
            parentView.removeView(overlay)
            overlayView = null
            anchorViewRef = null
            android.util.Log.d(TAG, \"FOLDER_OVERLAY dismiss completed\")
            onDismiss()
        }
        .start()
}
```

**Key improvements**:
- Content fades out FIRST (80ms), then card shrinks — reverse of open sequence
- Close stiffness 1200f (was MEDIUM 1500f) — slightly softer for organic feel
- Close damping 0.72f (was MEDIUM_BOUNCY 0.5f) — less bouncy, more controlled
- Close target scale 0.05f (was 0.2f) — shrinks almost to a point, matching the folder icon's tiny size
- Added `isAttachedToWindow` check for anchor view safety

#### Fix for null anchor on drag-created folders

In `ArchiveFragment.kt` → `handleAppDropOnApp()` (around line 876), when showing the folder overlay after drag-creation, try to find the newly-created folder's view in the RecyclerView:

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    val ready = withTimeoutOrNull(1500L) {
        viewModel.archivedApps.first { list ->
            list.count { it.folderName == defaultName } >= 2
        }
    }
    if (ready != null) {
        expandedFolder = defaultName
        // Try to find the folder view in the RecyclerView for animation origin
        val folderAnchor = findFolderViewByName(defaultName)
        runCatching { showFolderOverlay(defaultName, folderAnchor) }
            .onFailure { android.util.Log.e(TAG, \"showFolderOverlay failed\", it) }
    } else {
        android.util.Log.w(TAG, \"handleAppDropOnApp timed out waiting for folder\")
    }
}
```

Add this helper method to `ArchiveFragment`:

```kotlin
/**
 * Scans the RecyclerView to find the folder item view for the given folder name.
 * Returns null if the folder hasn't been laid out yet (animation will use center fallback).
 */
private fun findFolderViewByName(folderName: String): View? {
    for (i in 0 until recyclerView.childCount) {
        val child = recyclerView.getChildAt(i)
        val item = child?.tag
        if (item is ArchivedItem.Folder && item.name == folderName) {
            return child
        }
    }
    return null
}
```

---

## Files Changed Summary

| File | Change Type | Description |
|------|------------|-------------|
| `ArchivedAppsAdapter.kt` | Modify | `AppViewHolder.bind()` — null out background before drag start to prevent square ripple flash |
| `FolderAppsAdapter.kt` | Modify | `onBindViewHolder()` — same ripple fix for folder-internal drag |
| `FolderExpandOverlay.kt` | Modify | `show()` — rewrite open animation with better pivot, stiffness, content stagger |
| `FolderExpandOverlay.kt` | Modify | `dismiss()` — rewrite close animation with content-first fade, tighter spring |
| `ArchiveFragment.kt` | Modify | `handleAppDropOnApp()` — find folder view for animation anchor; add `findFolderViewByName()` helper |
| `item_archived_app.xml` | Optional | Change `selectableItemBackground` → `selectableItemBackgroundBorderless` for circular ripple (if using Fix Option B) |
| `item_archived_folder.xml` | Optional | Same as above for consistency |

### Spring Parameter Reference (Pixel Launcher feel)

| Parameter | Open (Current) | Open (Fixed) | Close (Current) | Close (Fixed) |
|-----------|---------------|-------------|-----------------|--------------|
| Start Scale | 0.2f | 0.15f | 1.0f → 0.2f | 1.0f → 0.05f |
| Stiffness | 200f (LOW) | 800f | 1500f (MEDIUM) | 1200f |
| Damping | 0.2f (LOW_BOUNCY) | 0.65f | 0.5f (MEDIUM_BOUNCY) | 0.72f |
| Content Delay | None | 100ms fade-in | None | 80ms fade-out first |

### Testing Checklist

- [ ] Long-press app icon in archive grid → no square/rectangular flash, only drag shadow appears
- [ ] Long-press app icon inside expanded folder → same, no flash
- [ ] Tap existing folder → card expands FROM the folder icon position with organic spring
- [ ] Drag app onto another app to create folder → folder overlay appears with center-expand (or from folder view if found)
- [ ] Close folder by tapping scrim → card shrinks BACK to folder icon with content fading first
- [ ] Folder animation completes in < 500ms (no lingering wobble)
- [ ] Verify haptic feedback still fires on drag start
- [ ] Verify drag-and-drop still creates folders correctly (functional regression)
- [ ] Test on both light and dark themes
- [ ] Test on API 31+ (blur) and API < 31 (no blur)
"
Observation: Create successful: /app/Decluttr/archive_drag_shadow_and_folder_animation_fix.md
