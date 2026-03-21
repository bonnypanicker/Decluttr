> **Scope:** Fix drag-and-drop physics, folder expansion UX, re-drag-to-open-world, and modernize the Archive page to match Pixel Launcher (Android 16 QPR3) behavior.
>
> **Repo:** `com.example.decluttr` — Kotlin, Compose + View interop, Material 3, Hilt, Room, Coil 2, RecyclerView with Android View DragEvent system.
>
> **Target behavior:** Stock Pixel Launcher home screen — icon disappears from grid on drag start, follows finger with physics-based shadow, smooth folder creation/expansion with dolly-zoom + bouncy spring-back, and seamless re-drag out of folders back to \"open world.\"

---

## Table of Contents

1. [Problem Analysis](#1-problem-analysis)
2. [Dependencies & New Resources](#2-dependencies--new-resources)
3. [Fix 1 — Hide Source Icon During Drag (Physics-Correct Behavior)](#3-fix-1--hide-source-icon-during-drag)
4. [Fix 2 — Custom Physics-Based Drag Shadow (SpringAnimation Overlay)](#4-fix-2--custom-physics-based-drag-shadow)
5. [Fix 3 — Re-Drag Back to Open World (Unfolder on Drop Outside)](#5-fix-3--re-drag-back-to-open-world)
6. [Fix 4 — Modern Folder Expansion (Dolly Zoom + BottomSheet)](#6-fix-4--modern-folder-expansion)
7. [Fix 5 — Folder Naming UX (Inline Rename Like Pixel Launcher)](#7-fix-5--folder-naming-ux)
8. [Fix 6 — Modern Theme, Transitions & Polish](#8-fix-6--modern-theme-transitions--polish)
9. [Fix 7 — Drag Target Visual Feedback (Jiggle + Scale)](#9-fix-7--drag-target-visual-feedback)
10. [Full Modified File Listings](#10-full-modified-file-listings)
11. [Migration Checklist](#11-migration-checklist)

---

## 1. Problem Analysis

### Current State (Broken)

| Behavior | Current | Expected (Pixel Launcher) |
|----------|---------|---------------------------|
| Drag start | Icon **stays in place**, ghost shadow appears | Icon **disappears** from grid, shadow replaces it |
| Drag shadow | Default `View.DragShadowBuilder` — static, no physics | Scaled-up icon with spring-damped following |
| Drop on app | Creates folder via `AlertDialog` prompt | Smooth merge animation → folder forms in-place |
| Folder tap | `AlertDialog` with plain `RecyclerView` grid | Dolly-zoom expansion with blur backdrop + bounce close |
| Re-drag out of folder | **Impossible** — no handler for \"drop outside\" | Drag app out → removes from folder, returns to grid |
| Folder naming | Separate `AlertDialog` with `EditText` | Inline editable label under folder, tap-to-rename |
| Transitions | None — abrupt state changes | Spring-based scale/fade on all state transitions |

### Root Cause Analysis

**File: `ArchivedAppsAdapter.kt` — `AppViewHolder.bind()` (lines 78-101)**

```kotlin
// PROBLEM: setOnLongClickListener starts drag but never hides the source view
itemView.setOnLongClickListener {
    onAppStartDrag(app)
    val clipData = ClipData.newPlainText(\"packageId\", app.packageId)
    val shadowBuilder = View.DragShadowBuilder(itemView) // Static shadow
    itemView.startDragAndDrop(clipData, shadowBuilder, app, 0)
    true  // Returns true but itemView.visibility is still VISIBLE
}
```

**File: `ArchivedAppsAdapter.kt` — `DragListener` (lines 136-168)**

```kotlin
// PROBLEM: ACTION_DRAG_ENDED never restores visibility
// PROBLEM: No handler for \"drop on empty space\" (re-drag to open world)
// PROBLEM: No visual feedback on drag targets (ENTERED/EXITED)
DragEvent.ACTION_DRAG_ENDED -> {
    return true  // Does nothing — source stays invisible forever if we hide it
}
```

**File: `ArchivedAppsList.kt` — Folder dialog (lines 322-359)**

```kotlin
// PROBLEM: Uses android.app.AlertDialog — no animation, no blur, old-fashioned
android.app.AlertDialog.Builder(dialogContext)
    .setView(dialogView)
    .setNegativeButton(\"Close\") { dialog, _ -> ... }
    .show()
```

---

## 2. Dependencies & New Resources

### 2a. Add to `build.gradle.kts` (app module)

```kotlin
dependencies {
    // Physics-based animations (SpringAnimation)
    implementation(\"androidx.dynamicanimation:dynamicanimation:1.0.0\")
    
    // Already present — verify these exist:
    // implementation(\"com.google.android.material:material:1.12.0\")
    // implementation(\"androidx.recyclerview:recyclerview:1.3.2\")
}
```

### 2b. New Drawable — `res/drawable/bg_folder_expanded.xml`

Modern semi-transparent background for expanded folder overlay:

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:shape=\"rectangle\">
    <corners android:radius=\"28dp\" />
    <solid android:color=\"?attr/colorSurfaceContainerHigh\" />
</shape>
```

### 2c. New Drawable — `res/drawable/bg_drag_target_highlight.xml`

Visual feedback when dragging over a valid drop target:

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:shape=\"rectangle\">
    <corners android:radius=\"16dp\" />
    <stroke
        android:width=\"2dp\"
        android:color=\"?attr/colorPrimary\"
        android:dashWidth=\"6dp\"
        android:dashGap=\"4dp\" />
    <solid android:color=\"#1A6750A4\" />
</shape>
```

### 2d. New Layout — `res/layout/dialog_folder_expanded_modern.xml`

Replaces `dialog_expanded_folder.xml` with modern Pixel-Launcher-style folder overlay:

```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:id=\"@+id/folder_root\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"match_parent\"
    android:clickable=\"true\"
    android:focusable=\"true\">

    <!-- Blurred scrim backdrop — click to close -->
    <View
        android:id=\"@+id/folder_scrim\"
        android:layout_width=\"match_parent\"
        android:layout_height=\"match_parent\"
        android:background=\"#80000000\" />

    <!-- Folder content card — positioned over the folder icon origin -->
    <com.google.android.material.card.MaterialCardView
        android:id=\"@+id/folder_card\"
        android:layout_width=\"match_parent\"
        android:layout_height=\"wrap_content\"
        android:layout_gravity=\"center\"
        android:layout_marginHorizontal=\"32dp\"
        android:layout_marginVertical=\"80dp\"
        app:cardCornerRadius=\"28dp\"
        app:cardElevation=\"8dp\"
        app:cardBackgroundColor=\"?attr/colorSurfaceContainerHigh\"
        xmlns:app=\"http://schemas.android.com/apk/res-auto\">

        <LinearLayout
            android:layout_width=\"match_parent\"
            android:layout_height=\"wrap_content\"
            android:orientation=\"vertical\"
            android:padding=\"20dp\">

            <!-- Editable folder title -->
            <EditText
                android:id=\"@+id/folder_title_edit\"
                android:layout_width=\"match_parent\"
                android:layout_height=\"wrap_content\"
                android:gravity=\"center\"
                android:textSize=\"16sp\"
                android:textStyle=\"bold\"
                android:textColor=\"?attr/colorOnSurface\"
                android:background=\"@android:color/transparent\"
                android:inputType=\"text\"
                android:maxLines=\"1\"
                android:hint=\"Folder name\"
                android:textColorHint=\"?attr/colorOnSurfaceVariant\"
                android:imeOptions=\"actionDone\"
                android:importantForAutofill=\"no\"
                android:paddingVertical=\"8dp\" />

            <!-- App grid inside folder -->
            <androidx.recyclerview.widget.RecyclerView
                android:id=\"@+id/folder_grid\"
                android:layout_width=\"match_parent\"
                android:layout_height=\"wrap_content\"
                android:layout_marginTop=\"12dp\"
                android:clipToPadding=\"false\"
                android:overScrollMode=\"never\" />

        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
</FrameLayout>
```

### 2e. New IDs — Add to `res/values/ids.xml`

```xml
<!-- Add these new IDs to existing ids.xml -->
<item name=\"folder_root\" type=\"id\" />
<item name=\"folder_scrim\" type=\"id\" />
<item name=\"folder_card\" type=\"id\" />
<item name=\"folder_title_edit\" type=\"id\" />
<item name=\"drag_overlay_tag\" type=\"id\" />
```

### 2f. New Anim Resources — `res/anim/`

**`res/anim/folder_expand_in.xml`**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<set xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:interpolator=\"@android:anim/overshoot_interpolator\">
    <scale
        android:fromXScale=\"0.6\"
        android:fromYScale=\"0.6\"
        android:toXScale=\"1.0\"
        android:toYScale=\"1.0\"
        android:pivotX=\"50%\"
        android:pivotY=\"50%\"
        android:duration=\"350\" />
    <alpha
        android:fromAlpha=\"0.0\"
        android:toAlpha=\"1.0\"
        android:duration=\"200\" />
</set>
```

**`res/anim/folder_collapse_out.xml`**
```xml
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<set xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:interpolator=\"@android:anim/accelerate_interpolator\">
    <scale
        android:fromXScale=\"1.0\"
        android:fromYScale=\"1.0\"
        android:toXScale=\"0.6\"
        android:toYScale=\"0.6\"
        android:pivotX=\"50%\"
        android:pivotY=\"50%\"
        android:duration=\"250\" />
    <alpha
        android:fromAlpha=\"1.0\"
        android:toAlpha=\"0.0\"
        android:duration=\"200\"
        android:startOffset=\"50\" />
</set>
```

---

## 3. Fix 1 — Hide Source Icon During Drag

**File:** `ArchivedAppsAdapter.kt`
**Impact:** High — this is the core \"physics-breaking\" issue. The icon stays in its grid cell while a drag shadow also appears, making it look like the icon is duplicated.

### What to change in `AppViewHolder.bind()`

Replace the `setOnLongClickListener` block (lines 88-99):

```kotlin
// ─── BEFORE (broken) ───────────────────────────
itemView.setOnLongClickListener {
    onAppStartDrag(app)
    val clipData = ClipData.newPlainText(\"packageId\", app.packageId)
    val shadowBuilder = View.DragShadowBuilder(itemView)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        itemView.startDragAndDrop(clipData, shadowBuilder, app, 0)
    } else {
        @Suppress(\"DEPRECATION\")
        itemView.startDrag(clipData, shadowBuilder, app, 0)
    }
    true
}
```

```kotlin
// ─── AFTER (Pixel Launcher behavior) ───────────
itemView.setOnLongClickListener { view ->
    onAppStartDrag(app)

    // 1. Create clip data for the drag
    val clipData = ClipData.newPlainText(\"packageId\", app.packageId)

    // 2. Build a scaled-up shadow (Pixel Launcher uses ~1.1x scale)
    val shadowBuilder = ScaledDragShadowBuilder(view, 1.1f)

    // 3. Start drag
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        view.startDragAndDrop(clipData, shadowBuilder, app, 0)
    } else {
        @Suppress(\"DEPRECATION\")
        view.startDrag(clipData, shadowBuilder, app, 0)
    }

    // 4. CRITICAL: Hide the source view so it doesn't duplicate
    //    Pixel Launcher hides the icon from its cell during drag.
    view.visibility = View.INVISIBLE

    true
}
```

### What to change in `DragListener` (lines 136-168)

Replace the entire `DragListener` inner class:

```kotlin
// ─── BEFORE (incomplete) ───────────────────────
private inner class DragListener : View.OnDragListener {
    override fun onDrag(view: View, event: DragEvent): Boolean {
        val targetItem = view.tag as? ArchivedItem
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
            }
            DragEvent.ACTION_DRAG_ENTERED -> { return true }
            DragEvent.ACTION_DRAG_EXITED -> { return true }
            DragEvent.ACTION_DROP -> { /* ... existing logic ... */ }
            DragEvent.ACTION_DRAG_ENDED -> { return true }
            else -> return false
        }
    }
}
```

```kotlin
// ─── AFTER (full Pixel Launcher behavior) ──────
private inner class DragListener : View.OnDragListener {
    private var originalBackground: android.graphics.drawable.Drawable? = null

    override fun onDrag(view: View, event: DragEvent): Boolean {
        val targetItem = view.tag as? ArchivedItem
        val draggedApp = event.localState as? ArchivedApp

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(
                    android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
                )
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                // Visual feedback: scale up + highlight the drop target
                if (targetItem != null && draggedApp != null) {
                    val isValidTarget = when (targetItem) {
                        is ArchivedItem.App -> draggedApp.packageId != targetItem.app.packageId
                        is ArchivedItem.Folder -> true
                    }
                    if (isValidTarget) {
                        originalBackground = view.background
                        view.setBackgroundResource(R.drawable.bg_drag_target_highlight)
                        view.animate()
                            .scaleX(1.15f)
                            .scaleY(1.15f)
                            .setDuration(150)
                            .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                            .start()
                    }
                }
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                // Reset visual feedback
                view.background = originalBackground
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                return true
            }

            DragEvent.ACTION_DROP -> {
                // Reset target visuals
                view.background = originalBackground
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                if (draggedApp != null && targetItem != null) {
                    when {
                        targetItem is ArchivedItem.App &&
                            draggedApp.packageId != targetItem.app.packageId -> {
                            onAppDropOnApp(draggedApp, targetItem.app)
                        }
                        targetItem is ArchivedItem.Folder -> {
                            onAppDropOnFolder(draggedApp, targetItem.name)
                        }
                    }
                    return true
                }
                return false
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                // CRITICAL: Restore visibility of the source view
                // The event.result tells us if the drop was accepted
                // Find the source view in the RecyclerView and make it visible again
                val recyclerView = view.parent as? RecyclerView
                recyclerView?.let { rv ->
                    for (i in 0 until rv.childCount) {
                        val child = rv.getChildAt(i)
                        if (child.visibility == View.INVISIBLE) {
                            // Animate back in with a spring-like pop
                            child.visibility = View.VISIBLE
                            child.alpha = 0f
                            child.scaleX = 0.5f
                            child.scaleY = 0.5f
                            child.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(250)
                                .setInterpolator(
                                    android.view.animation.OvershootInterpolator(1.5f)
                                )
                                .start()
                        }
                    }
                }

                // Also reset this view's own visual state
                view.background = originalBackground
                view.scaleX = 1f
                view.scaleY = 1f
                return true
            }

            else -> return false
        }
    }
}
```

### New Class — `ScaledDragShadowBuilder`

Add this class to `ArchivedAppsAdapter.kt` or as a new file `ScaledDragShadowBuilder.kt` in the same package:

```kotlin
package com.example.decluttr.presentation.screens.dashboard

import android.graphics.Canvas
import android.graphics.Point
import android.view.View

/**
 * Custom DragShadowBuilder that renders the dragged view at a specified scale.
 * Pixel Launcher uses ~1.1x scale for the drag shadow to provide visual feedback
 * that the icon has been \"picked up\" from the grid.
 */
class ScaledDragShadowBuilder(
    view: View,
    private val scaleFactor: Float = 1.1f
) : View.DragShadowBuilder(view) {

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        val v = view ?: return
        val width = (v.width * scaleFactor).toInt()
        val height = (v.height * scaleFactor).toInt()
        outShadowSize.set(width, height)
        // Touch point at center of the shadow
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val v = view ?: return
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)
        // Slight alpha reduction to indicate \"lifted\" state
        canvas.saveLayerAlpha(
            0f, 0f,
            v.width.toFloat(), v.height.toFloat(),
            230 // ~90% opacity
        )
        v.draw(canvas)
        canvas.restore()
        canvas.restore()
    }
}
```

### Why This Works

1. **`view.visibility = View.INVISIBLE`** on drag start — the icon disappears from its grid cell, just like Pixel Launcher. The drag shadow becomes the sole visual representation of the icon.
2. **`ScaledDragShadowBuilder`** — the shadow is 1.1x larger than the original, giving the \"picked up\" feel.
3. **`ACTION_DRAG_ENDED`** — restores visibility with a spring-pop animation, so if the drop is cancelled, the icon animates back into its cell smoothly.
4. **`ACTION_DRAG_ENTERED/EXITED`** — scale-up + highlight on valid targets gives the user clear feedback about where they can drop.

---

## 4. Fix 2 — Custom Physics-Based Drag Shadow

The standard `View.DragShadowBuilder` tracks the finger 1:1 without any physics interpolation. For a truly Pixel-Launcher-like feel, we can optionally use a **SpringAnimation overlay** approach instead of the system drag shadow.

> **Note:** This is an **advanced enhancement**. Fix 1 alone solves the core \"icon stays in place\" issue. This fix adds the spring-damped \"physics feel\" where the drag shadow slightly lags behind rapid finger movement.

**File:** New file `DragOverlayController.kt` in `presentation/screens/dashboard/`

```kotlin
package com.example.decluttr.presentation.screens.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import coil.load
import com.example.decluttr.R
import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.presentation.util.AppIconModel

/**
 * Manages a physics-based drag overlay that replaces the system drag shadow.
 * Uses SpringAnimation for smooth, momentum-carrying icon following.
 *
 * Usage:
 *   1. Call [startDrag] on long press — creates overlay window
 *   2. Forward MotionEvents via [updatePosition] during drag
 *   3. Call [endDrag] on ACTION_UP/ACTION_CANCEL — animates away
 *
 * This approach gives the dragged icon a natural \"weight\" feel,
 * where it slightly trails the finger during fast movements and
 * overshoots slightly on direction changes — mimicking Pixel Launcher.
 */
class DragOverlayController(private val context: Context) {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var springX: SpringAnimation? = null
    private var springY: SpringAnimation? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Spring configuration — tuned to match Pixel Launcher feel
    companion object {
        private const val STIFFNESS = 800f  // Between MEDIUM and HIGH
        private const val DAMPING = SpringForce.DAMPING_RATIO_NO_BOUNCY
        private const val ICON_SIZE_DP = 64
        private const val ICON_ELEVATION_DP = 12f
    }

    /**
     * Creates a floating overlay at the touch position showing the app icon.
     * The overlay uses SpringAnimation to follow finger movement with physics.
     */
    @SuppressLint(\"ClickableViewAccessibility\")
    fun startDrag(app: ArchivedApp, startX: Float, startY: Float) {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val density = context.resources.displayMetrics.density
        val iconSizePx = (ICON_SIZE_DP * density).toInt()

        // Create the floating icon view
        overlayView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx)
            scaleType = ImageView.ScaleType.FIT_CENTER
            elevation = ICON_ELEVATION_DP * density
            alpha = 0.92f

            // Load the app icon via Coil
            load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
            }
        }

        // Window params for the overlay — TYPE_APPLICATION_PANEL works without special permissions
        layoutParams = WindowManager.LayoutParams(
            iconSizePx,
            iconSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (startX - iconSizePx / 2).toInt()
            y = (startY - iconSizePx / 2).toInt()
        }

        windowManager?.addView(overlayView, layoutParams)

        // Initialize spring animations
        val params = layoutParams!!
        springX = SpringAnimation(overlayView, object : DynamicAnimation.ViewProperty(\"windowX\") {
            override fun setValue(view: View, value: Float) {
                params.x = value.toInt()
                try {
                    windowManager?.updateViewLayout(view, params)
                } catch (_: Exception) {}
            }
            override fun getValue(view: View): Float = params.x.toFloat()
        }).apply {
            spring = SpringForce(startX - iconSizePx / 2)
                .setStiffness(STIFFNESS)
                .setDampingRatio(DAMPING)
        }

        springY = SpringAnimation(overlayView, object : DynamicAnimation.ViewProperty(\"windowY\") {
            override fun setValue(view: View, value: Float) {
                params.y = value.toInt()
                try {
                    windowManager?.updateViewLayout(view, params)
                } catch (_: Exception) {}
            }
            override fun getValue(view: View): Float = params.y.toFloat()
        }).apply {
            spring = SpringForce(startY - iconSizePx / 2)
                .setStiffness(STIFFNESS)
                .setDampingRatio(DAMPING)
        }

        // Entrance animation: scale from 0.5 to 1.1 with overshoot
        overlayView?.scaleX = 0.5f
        overlayView?.scaleY = 0.5f
        overlayView?.animate()
            ?.scaleX(1.1f)
            ?.scaleY(1.1f)
            ?.setDuration(200)
            ?.setInterpolator(android.view.animation.OvershootInterpolator(2f))
            ?.start()
    }

    /**
     * Update the spring target to follow the finger.
     * Springs create natural lag/overshoot for physics feel.
     */
    fun updatePosition(x: Float, y: Float) {
        val iconSizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        springX?.animateToFinalPosition(x - iconSizePx / 2)
        springY?.animateToFinalPosition(y - iconSizePx / 2)
    }

    /**
     * End the drag — animate out and remove overlay.
     */
    fun endDrag(dropX: Float? = null, dropY: Float? = null) {
        springX?.cancel()
        springY?.cancel()

        // Animate shrink + fade out
        overlayView?.animate()
            ?.scaleX(0.3f)
            ?.scaleY(0.3f)
            ?.alpha(0f)
            ?.setDuration(200)
            ?.setInterpolator(android.view.animation.AccelerateInterpolator())
            ?.withEndAction {
                removeOverlay()
            }
            ?.start()
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        windowManager = null
        springX = null
        springY = null
        layoutParams = null
    }
}
```

### Integration with `ArchivedAppsAdapter`

If you choose this approach, you'd replace the system drag with custom touch handling. Add a `DragOverlayController` reference to the adapter:

```kotlin
class ArchivedAppsAdapter(
    // ... existing params ...
    private val dragOverlayController: DragOverlayController? = null  // Optional physics overlay
) : ListAdapter<ArchivedItem, RecyclerView.ViewHolder>(ArchiveDiffCallback()) {
```

And in `AppViewHolder.bind()`, use `OnTouchListener` instead of `startDragAndDrop()`:

```kotlin
// ALTERNATIVE to Fix 1 — use this ONLY if you want physics-based overlay
// instead of system drag shadow. Both approaches hide the source icon.
@SuppressLint(\"ClickableViewAccessibility\")
private fun bindWithPhysicsDrag(app: ArchivedApp) {
    var isDragging = false
    var longPressTriggered = false

    val gestureDetector = android.view.GestureDetector(
        itemView.context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressTriggered = true
                isDragging = true
                itemView.visibility = View.INVISIBLE
                
                // Haptic feedback like Pixel Launcher
                itemView.performHapticFeedback(
                    android.view.HapticFeedbackConstants.LONG_PRESS
                )
                
                // Get absolute screen position
                val location = IntArray(2)
                itemView.getLocationOnScreen(location)
                val absX = location[0] + e.x
                val absY = location[1] + e.y

                dragOverlayController?.startDrag(app, absX, absY)
            }
        }
    )

    itemView.setOnTouchListener { view, event ->
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    dragOverlayController?.updatePosition(
                        location[0] + event.x,
                        location[1] + event.y
                    )
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    dragOverlayController?.endDrag()
                    // Restore source with spring animation
                    view.visibility = View.VISIBLE
                    view.alpha = 0f
                    view.scaleX = 0.5f
                    view.scaleY = 0.5f
                    view.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(250)
                        .setInterpolator(
                            android.view.animation.OvershootInterpolator(1.5f)
                        )
                        .start()
                }
                if (!longPressTriggered) {
                    // Regular tap — treat as click
                    if (event.action == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                }
                longPressTriggered = false
            }
        }
        true
    }
}
```

> **Recommendation:** Start with **Fix 1 (system drag + hide source)** which is simpler and more reliable. Add this physics overlay later if the client wants the extra polish. Both approaches achieve the core \"icon disappears\" behavior.

---

## 5. Fix 3 — Re-Drag Back to Open World

**Problem:** Currently, once an app is in a folder, there's no way to drag it back out. The user can only long-press the folder to \"ungroup all\" — which is destructive.

**Solution:** Set the drag listener on the **RecyclerView itself** (not just item views) to detect drops that land on empty grid space. When a foldered app is dropped on empty space, remove it from its folder.

### What to change in `ArchivedAppsRecyclerView.kt`

Add a `RecyclerView`-level drag listener in the `factory` block:

```kotlin
// ─── BEFORE ────────────────────────────────────
AndroidView(
    modifier = modifier.fillMaxSize(),
    factory = { ctx ->
        RecyclerView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val gridLayoutManager = GridLayoutManager(ctx, 4)
            layoutManager = gridLayoutManager
            val padding = (16 * ctx.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            clipToPadding = false
            this.adapter = adapter
        }
    },
    // ...
)
```

```kotlin
// ─── AFTER ─────────────────────────────────────
AndroidView(
    modifier = modifier.fillMaxSize(),
    factory = { ctx ->
        RecyclerView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val gridLayoutManager = GridLayoutManager(ctx, 4)
            layoutManager = gridLayoutManager
            val padding = (16 * ctx.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            clipToPadding = false
            this.adapter = adapter

            // ── NEW: RecyclerView-level drag listener for \"drop on empty space\" ──
            setOnDragListener { recyclerView, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        event.clipDescription.hasMimeType(
                            android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
                        )
                    }
                    DragEvent.ACTION_DROP -> {
                        val draggedApp = event.localState as? ArchivedApp
                        if (draggedApp != null && draggedApp.folderName != null) {
                            // Dropped on empty space — check if we're NOT over a child view
                            val rv = recyclerView as RecyclerView
                            val childUnder = rv.findChildViewUnder(event.x, event.y)
                            if (childUnder == null) {
                                // Drop on empty space = \"unfolder\" this app
                                onAppDropOnEmptySpace(draggedApp)
                                true
                            } else {
                                false // Let the child's DragListener handle it
                            }
                        } else {
                            false
                        }
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        // Restore all invisible children (drag source)
                        for (i in 0 until (recyclerView as RecyclerView).childCount) {
                            val child = (recyclerView as RecyclerView).getChildAt(i)
                            if (child.visibility == View.INVISIBLE) {
                                child.visibility = View.VISIBLE
                                child.alpha = 0f
                                child.scaleX = 0.5f
                                child.scaleY = 0.5f
                                child.animate()
                                    .alpha(1f).scaleX(1f).scaleY(1f)
                                    .setDuration(250)
                                    .setInterpolator(
                                        android.view.animation.OvershootInterpolator(1.5f)
                                    )
                                    .start()
                            }
                        }
                        true
                    }
                    else -> true
                }
            }
        }
    },
    // ...
)
```

### New callback parameter

Add to `ArchivedAppsRecyclerView` composable signature:

```kotlin
@Composable
fun ArchivedAppsRecyclerView(
    items: List<ArchivedItem>,
    onAppClick: (String) -> Unit,
    onDeleteClick: (ArchivedApp) -> Unit,
    onAppStartDrag: (ArchivedApp) -> Unit,
    onAppDropOnApp: (ArchivedApp, ArchivedApp) -> Unit,
    onAppDropOnFolder: (ArchivedApp, String) -> Unit,
    onAppDropOnEmptySpace: (ArchivedApp) -> Unit,  // ← NEW
    onRemoveFolder: (List<ArchivedApp>) -> Unit,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier
)
```

### Wire up in `ArchivedAppsList.kt`

In the `ArchivedAppsRecyclerView` call (around line 295):

```kotlin
ArchivedAppsRecyclerView(
    items = groupedItems,
    onAppClick = onAppClick,
    onDeleteClick = onDeleteClick,
    onAppStartDrag = { /* ... */ },
    onAppDropOnApp = { draggedApp, targetApp ->
        newFolderAppPair = Pair(draggedApp, targetApp)
    },
    onAppDropOnFolder = { draggedApp, folderName ->
        onAppUpdate(draggedApp.copy(folderName = folderName))
    },
    // ── NEW: Drop on empty space removes from folder ──
    onAppDropOnEmptySpace = { draggedApp ->
        onAppUpdate(draggedApp.copy(folderName = null))
    },
    onRemoveFolder = { folderApps ->
        folderApps.forEach { app ->
            onAppUpdate(app.copy(folderName = null))
        }
    },
    onFolderClick = { folderName ->
        expandedFolder = folderName
    },
    modifier = Modifier.weight(1f)
)
```

### Also enable drag-from-folder-dialog

Inside the expanded folder dialog (Fix 4 below), the `FolderAppsAdapter` items should also be draggable. Add long-press drag support to `FolderAppsAdapter`:

```kotlin
// In FolderAppsAdapter.onBindViewHolder():
holder.itemView.setOnLongClickListener { view ->
    val clipData = ClipData.newPlainText(\"packageId\", app.packageId)
    val shadowBuilder = ScaledDragShadowBuilder(view, 1.1f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        view.startDragAndDrop(clipData, shadowBuilder, app, 0)
    } else {
        @Suppress(\"DEPRECATION\")
        view.startDrag(clipData, shadowBuilder, app, 0)
    }
    view.visibility = View.INVISIBLE
    // Dismiss the folder dialog — the drag continues on the main grid
    onDragStartFromFolder?.invoke()
    true
}
```

Add a new callback to `FolderAppsAdapter`:

```kotlin
class FolderAppsAdapter(
    private val apps: List<ArchivedApp>,
    private val onAppClick: (String) -> Unit,
    private val onDragStartFromFolder: (() -> Unit)? = null  // ← NEW: dismiss dialog on drag out
) : RecyclerView.Adapter<FolderAppsAdapter.ViewHolder>()
```

---

## 6. Fix 4 — Modern Folder Expansion

**Problem:** Currently uses `android.app.AlertDialog` — flat, no animation, no blur, doesn't match Pixel Launcher's dolly-zoom expansion.

**Solution:** Replace with a custom full-screen overlay View that animates in with a scale + fade (dolly-zoom feel) and out with a bouncy spring-back. Uses the new `dialog_folder_expanded_modern.xml` layout.

### New file: `FolderExpandOverlay.kt`

```kotlin
package com.example.decluttr.presentation.screens.dashboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decluttr.R
import com.example.decluttr.domain.model.ArchivedApp
import com.google.android.material.card.MaterialCardView

/**
 * Full-screen overlay that expands a folder with Pixel-Launcher-style animations.
 *
 * Animation sequence (open):
 *   1. Scrim fades in (0 → 0.5 alpha) over 200ms
 *   2. Card scales from folder icon origin (0.3 → 1.0) with OvershootInterpolator
 *   3. Card translates from folder icon position to center
 *   4. Content fades in after card settles
 *
 * Animation sequence (close):
 *   1. Content fades out
 *   2. Card springs back to folder icon size (1.0 → 0.3) with spring damping
 *   3. Scrim fades out
 *   4. Overlay removed from window
 */
class FolderExpandOverlay(
    private val context: Context,
    private val parentView: ViewGroup
) {
    private var overlayView: View? = null
    private var isExpanded = false

    /**
     * Show the folder expansion overlay.
     *
     * @param folderName Name displayed in the editable title
     * @param folderApps Apps to show in the grid
     * @param anchorView The folder icon view — used as the animation origin
     * @param onAppClick Callback when an app in the folder is tapped
     * @param onFolderRenamed Callback when the user edits the folder name
     * @param onDragStartFromFolder Callback when user starts dragging from folder
     * @param onDismiss Callback when the overlay is dismissed
     */
    fun show(
        folderName: String,
        folderApps: List<ArchivedApp>,
        anchorView: View?,
        onAppClick: (String) -> Unit,
        onFolderRenamed: ((String) -> Unit)? = null,
        onDragStartFromFolder: (() -> Unit)? = null,
        onDismiss: () -> Unit
    ) {
        if (isExpanded) return
        isExpanded = true

        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.dialog_folder_expanded_modern, null)

        val root = overlayView!!.findViewById<FrameLayout>(R.id.folder_root)
        val scrim = overlayView!!.findViewById<View>(R.id.folder_scrim)
        val card = overlayView!!.findViewById<MaterialCardView>(R.id.folder_card)
        val titleEdit = overlayView!!.findViewById<EditText>(R.id.folder_title_edit)
        val grid = overlayView!!.findViewById<RecyclerView>(R.id.folder_grid)

        // Setup title
        titleEdit.setText(folderName)
        titleEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newName = titleEdit.text.toString().trim()
                if (newName.isNotEmpty() && newName != folderName) {
                    onFolderRenamed?.invoke(newName)
                }
                // Hide keyboard
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
                imm.hideSoftInputFromWindow(titleEdit.windowToken, 0)
                titleEdit.clearFocus()
                true
            } else false
        }

        // Setup grid
        grid.layoutManager = GridLayoutManager(context, 4)
        grid.adapter = FolderAppsAdapter(
            apps = folderApps,
            onAppClick = { packageId ->
                dismiss(onDismiss)
                onAppClick(packageId)
            },
            onDragStartFromFolder = {
                dismiss(onDismiss)
            }
        )

        // Tap scrim to close
        scrim.setOnClickListener { dismiss(onDismiss) }

        // Add overlay to parent
        parentView.addView(overlayView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // ── OPEN ANIMATION ──

        // Calculate anchor position for animation origin
        val anchorLocation = IntArray(2)
        val parentLocation = IntArray(2)
        anchorView?.getLocationInWindow(anchorLocation)
        parentView.getLocationInWindow(parentLocation)
        val anchorCenterX = (anchorLocation[0] - parentLocation[0] +
            (anchorView?.width ?: 0) / 2).toFloat()
        val anchorCenterY = (anchorLocation[1] - parentLocation[1] +
            (anchorView?.height ?: 0) / 2).toFloat()

        // Scrim fade in
        scrim.alpha = 0f
        scrim.animate()
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(AccelerateInterpolator())
            .start()

        // Apply blur if API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrim.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    15f, 15f,
                    android.graphics.Shader.TileMode.CLAMP
                )
            )
        }

        // Card: start from anchor, expand to center
        card.pivotX = anchorCenterX
        card.pivotY = anchorCenterY
        card.scaleX = 0.2f
        card.scaleY = 0.2f
        card.alpha = 0f

        // Use spring for dolly-zoom expand feel
        val scaleXSpring = SpringAnimation(card, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(1f)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
        }
        val scaleYSpring = SpringAnimation(card, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce(1f)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
        }

        card.animate()
            .alpha(1f)
            .setDuration(150)
            .start()

        scaleXSpring.setStartValue(0.2f).start()
        scaleYSpring.setStartValue(0.2f).start()
    }

    /**
     * Dismiss with bouncy spring-back animation (Pixel Launcher QPR3 style).
     */
    fun dismiss(onDismiss: () -> Unit) {
        if (!isExpanded) return
        isExpanded = false

        val overlay = overlayView ?: return
        val scrim = overlay.findViewById<View>(R.id.folder_scrim)
        val card = overlay.findViewById<MaterialCardView>(R.id.folder_card)

        // Card: spring back to small size with bounce
        val scaleXSpring = SpringAnimation(card, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(0.2f)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        }
        val scaleYSpring = SpringAnimation(card, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce(0.2f)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        }

        card.animate().alpha(0f).setDuration(200).start()
        scaleXSpring.start()
        scaleYSpring.start()

        // Scrim fade out
        scrim.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                parentView.removeView(overlay)
                overlayView = null
                onDismiss()
            }
            .start()
    }
}
```

### Integration in `ArchivedAppsList.kt`

Replace the `LaunchedEffect(expandedFolder)` block (lines 322-359) with:

```kotlin
// ─── BEFORE (old AlertDialog) ──────────────────
val dialogContext = LocalContext.current

LaunchedEffect(expandedFolder) {
    val folderName = expandedFolder ?: return@LaunchedEffect
    val folderApps = apps.filter { it.folderName == folderName }
    // ... AlertDialog.Builder ...
}
```

```kotlin
// ─── AFTER (modern overlay with dolly-zoom) ────
val context = LocalContext.current

// Keep track of the folder overlay controller
val folderOverlay = remember { mutableStateOf<FolderExpandOverlay?>(null) }

// Find the anchor view for animation origin
// This requires passing the RecyclerView reference
LaunchedEffect(expandedFolder) {
    val folderName = expandedFolder ?: return@LaunchedEffect
    val folderApps = apps.filter { it.folderName == folderName }

    if (folderApps.isEmpty()) {
        expandedFolder = null
        return@LaunchedEffect
    }

    // Get the activity's root decorView as parent for the overlay
    val activity = context as? android.app.Activity ?: return@LaunchedEffect
    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

    val overlay = FolderExpandOverlay(context, rootView)
    folderOverlay.value = overlay

    overlay.show(
        folderName = folderName,
        folderApps = folderApps,
        anchorView = null, // TODO: pass the folder ViewHolder itemView for precise origin
        onAppClick = { packageId ->
            expandedFolder = null
            onAppClick(packageId)
        },
        onFolderRenamed = { newName ->
            // Rename all apps in the folder
            folderApps.forEach { app ->
                onAppUpdate(app.copy(folderName = newName))
            }
        },
        onDragStartFromFolder = {
            expandedFolder = null
        },
        onDismiss = {
            expandedFolder = null
            folderOverlay.value = null
        }
    )
}
```

---

## 7. Fix 5 — Folder Naming UX

**Problem:** Currently uses `AlertDialog.Builder` with an `EditText` — modal, interruptive, not how Pixel Launcher does it.

**Pixel Launcher behavior:** When you create a folder, the folder opens immediately and the title is focused for editing. No separate dialog. You can tap the title later to rename.

### Changes to folder creation flow in `ArchivedAppsList.kt`

Replace the `LaunchedEffect(newFolderAppPair)` block:

```kotlin
// ─── BEFORE (AlertDialog for naming) ───────────
LaunchedEffect(newFolderAppPair) {
    val pair = newFolderAppPair ?: return@LaunchedEffect
    // ... AlertDialog.Builder(\"Name your new folder\") ...
}
```

```kotlin
// ─── AFTER (Pixel Launcher style: create folder → open → focus title) ─
LaunchedEffect(newFolderAppPair) {
    val pair = newFolderAppPair ?: return@LaunchedEffect

    // 1. Create the folder with a default name immediately
    val defaultName = \"New Folder\"
    onAppUpdate(pair.first.copy(folderName = defaultName))
    onAppUpdate(pair.second.copy(folderName = defaultName))
    newFolderAppPair = null

    // 2. Open the folder overlay immediately for the user to rename
    //    (Small delay to let recomposition settle with the new folder)
    kotlinx.coroutines.delay(150)
    expandedFolder = defaultName
    
    // The FolderExpandOverlay (Fix 4) automatically focuses the title EditText
    // so the user can type a name right away. They can also dismiss without
    // renaming and it stays as \"New Folder\".
}
```

### Auto-focus title in `FolderExpandOverlay.show()`

Add this after setting up the title EditText:

```kotlin
// Auto-focus title for new folders (when name is \"New Folder\")
if (folderName == \"New Folder\") {
    titleEdit.post {
        titleEdit.selectAll()
        titleEdit.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
    }
}
```

---

## 8. Fix 6 — Modern Theme, Transitions & Polish

### 8a. Item entrance animations on RecyclerView

**File:** `ArchivedAppsRecyclerView.kt`

Add a `LayoutAnimationController` to the RecyclerView for staggered entrance:

```kotlin
// In the factory block, after setting up the RecyclerView:
val enterAnim = android.view.animation.AnimationUtils.loadAnimation(ctx, android.R.anim.fade_in).apply {
    duration = 200
}
val controller = android.view.animation.LayoutAnimationController(enterAnim, 0.05f)
controller.order = android.view.animation.LayoutAnimationController.ORDER_NORMAL
layoutAnimation = controller
```

### 8b. Smooth item add/remove animations

Replace the default RecyclerView item animator with one that has physics-like timing:

```kotlin
// In ArchivedAppsRecyclerView factory block:
itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
    addDuration = 250
    removeDuration = 200
    moveDuration = 300
    changeDuration = 200
}
```

### 8c. Category pill transitions

**File:** `ArchivedAppsList.kt`

When switching categories, the grid should animate. Add layout animation scheduling:

```kotlin
// In the update block of the category pills AndroidView:
chip.setOnClickListener {
    selectedCategory = category
    // Trigger layout animation on next data update
    (recyclerView as? RecyclerView)?.scheduleLayoutAnimation()
}
```

This requires passing the RecyclerView reference. One approach:

```kotlin
// Store RecyclerView reference in a remember state
val recyclerViewRef = remember { mutableStateOf<RecyclerView?>(null) }

// In ArchivedAppsRecyclerView, expose the RV via a callback:
onRecyclerViewCreated = { rv -> recyclerViewRef.value = rv }
```

### 8d. Haptic feedback on drag events

```kotlin
// In AppViewHolder.bind(), on long press:
itemView.performHapticFeedback(
    android.view.HapticFeedbackConstants.LONG_PRESS,
    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
)

// On valid drop:
view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)

// On folder creation:
view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
```

### 8e. Folder icon animation

When a folder is created from two apps merging, animate the folder icon's sub-icons into position:

**File:** `ArchivedAppsAdapter.kt` — `FolderViewHolder.bind()`

```kotlin
fun bind(folderItem: ArchivedItem.Folder) {
    // ... existing binding ...

    // Entrance animation for newly created folders
    if (folderItem.apps.size <= 2) {
        // Just created — animate sub-icons flying in
        val icons = listOf(icon1, icon2, icon3, icon4)
        icons.forEachIndexed { index, iconView ->
            if (index < folderItem.apps.size) {
                iconView.alpha = 0f
                iconView.scaleX = 1.5f
                iconView.scaleY = 1.5f
                iconView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setStartDelay(index * 80L)
                    .setInterpolator(
                        android.view.animation.OvershootInterpolator(2f)
                    )
                    .start()
            }
        }
    }
}
```

### 8f. Update folder background drawable for modern look

Replace `res/drawable/bg_folder_icon.xml`:

```xml
<!-- ─── BEFORE ─── -->
<shape android:shape=\"rectangle\">
    <corners android:radius=\"16dp\" />
    <solid android:color=\"#40808080\" />
</shape>

<!-- ─── AFTER (Material You style with surface tint) ─── -->
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:shape=\"rectangle\">
    <corners android:radius=\"18dp\" />
    <solid android:color=\"?attr/colorSurfaceContainerHighest\" />
</shape>
```

> **Note:** Using `?attr/colorSurfaceContainerHighest` ties the folder background to Material You dynamic colors, matching Pixel Launcher's adaptive theming.

---

## 9. Fix 7 — Drag Target Visual Feedback (Jiggle + Scale)

Pixel Launcher provides clear visual cues when dragging an icon over a valid target:
- **Over another icon:** Target scales up slightly, indicating it will merge into a folder
- **Over a folder:** Folder \"breathes\" (subtle pulse) to indicate it will accept the icon
- **Over empty space:** No feedback (valid drop for unfolder)

This is already partially implemented in Fix 1's `DragListener`. Here's the complete feedback system:

### Enhanced `DragListener` with differentiated feedback

```kotlin
private inner class DragListener : View.OnDragListener {
    private var originalBackground: android.graphics.drawable.Drawable? = null
    private var pulseAnimator: android.animation.ObjectAnimator? = null

    override fun onDrag(view: View, event: DragEvent): Boolean {
        val targetItem = view.tag as? ArchivedItem
        val draggedApp = event.localState as? ArchivedApp

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(
                    android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
                )
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                if (targetItem == null || draggedApp == null) return true

                when (targetItem) {
                    is ArchivedItem.App -> {
                        if (draggedApp.packageId != targetItem.app.packageId) {
                            // Scale up + dashed border = \"will create folder\"
                            originalBackground = view.background
                            view.setBackgroundResource(R.drawable.bg_drag_target_highlight)
                            view.animate()
                                .scaleX(1.2f)
                                .scaleY(1.2f)
                                .setDuration(200)
                                .setInterpolator(OvershootInterpolator(3f))
                                .start()
                        }
                    }
                    is ArchivedItem.Folder -> {
                        // Folder \"breathing\" pulse animation
                        originalBackground = view.background
                        view.setBackgroundResource(R.drawable.bg_drag_target_highlight)

                        pulseAnimator = ObjectAnimator.ofFloat(
                            view, \"scaleX\", 1.0f, 1.08f
                        ).apply {
                            duration = 600
                            repeatMode = android.animation.ValueAnimator.REVERSE
                            repeatCount = android.animation.ValueAnimator.INFINITE
                            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                            addUpdateListener { anim ->
                                view.scaleY = anim.animatedValue as Float
                            }
                            start()
                        }
                    }
                }
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                pulseAnimator?.cancel()
                pulseAnimator = null
                view.background = originalBackground
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                return true
            }

            DragEvent.ACTION_DROP -> {
                pulseAnimator?.cancel()
                pulseAnimator = null
                view.background = originalBackground
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                if (draggedApp != null && targetItem != null) {
                    // Haptic on successful drop
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONFIRM
                    )

                    when {
                        targetItem is ArchivedItem.App &&
                            draggedApp.packageId != targetItem.app.packageId -> {
                            onAppDropOnApp(draggedApp, targetItem.app)
                        }
                        targetItem is ArchivedItem.Folder -> {
                            onAppDropOnFolder(draggedApp, targetItem.name)
                        }
                    }
                    return true
                }
                return false
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                pulseAnimator?.cancel()
                pulseAnimator = null
                view.background = originalBackground
                view.scaleX = 1f
                view.scaleY = 1f

                // Restore source view visibility with spring animation
                val recyclerView = findParentRecyclerView(view)
                recyclerView?.let { rv ->
                    for (i in 0 until rv.childCount) {
                        val child = rv.getChildAt(i)
                        if (child.visibility == View.INVISIBLE) {
                            child.visibility = View.VISIBLE
                            child.alpha = 0f
                            child.scaleX = 0.5f
                            child.scaleY = 0.5f
                            child.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(300)
                                .setInterpolator(OvershootInterpolator(1.5f))
                                .start()
                        }
                    }
                }
                return true
            }

            else -> return false
        }
    }

    private fun findParentRecyclerView(view: View): RecyclerView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is RecyclerView) return parent
            parent = parent.parent
        }
        return null
    }
}
```

### Import additions for `ArchivedAppsAdapter.kt`

```kotlin
import android.animation.ObjectAnimator
import android.view.animation.OvershootInterpolator
```

---

## 10. Full Modified File Listings

### Files to CREATE (new)

| File | Purpose |
|------|---------|
| `ScaledDragShadowBuilder.kt` | Custom drag shadow with scale |
| `DragOverlayController.kt` | Optional physics-based drag overlay |
| `FolderExpandOverlay.kt` | Modern folder expansion with dolly-zoom |
| `res/drawable/bg_folder_expanded.xml` | Folder overlay card background |
| `res/drawable/bg_drag_target_highlight.xml` | Drag target highlight drawable |
| `res/layout/dialog_folder_expanded_modern.xml` | Modern folder overlay layout |
| `res/anim/folder_expand_in.xml` | Folder open scale animation |
| `res/anim/folder_collapse_out.xml` | Folder close scale animation |

### Files to MODIFY (existing)

| File | Changes |
|------|---------|
| `ArchivedAppsAdapter.kt` | Hide source on drag, enhanced DragListener, haptics |
| `ArchivedAppsRecyclerView.kt` | RecyclerView-level drag listener for unfolder, layout animations |
| `ArchivedAppsList.kt` | Wire new callbacks, replace folder dialog, Pixel-style naming |
| `FolderAppsAdapter.kt` | Add long-press drag support + dismiss callback |
| `res/drawable/bg_folder_icon.xml` | Modern Material You background |
| `res/values/ids.xml` | Add new IDs |
| `build.gradle.kts` | Add dynamicanimation dependency |

### Files UNCHANGED

| File | Reason |
|------|--------|
| `DashboardScreen.kt` | No changes needed — archive tab wiring is clean |
| `DashboardViewModel.kt` | No changes — `updateArchivedApp` handles all folder ops |
| `DiscoveryScreen.kt` | Discovery tab is separate |
| `DiscoveryDashboardAdapter.kt` | Discovery tab is separate |
| `DiscoveryAppsAdapter.kt` | Discovery tab is separate |
| `NavGraph.kt` | Navigation is unaffected |
| `MainActivity.kt` | No changes |
| All domain/data layer files | Drag/drop is purely presentation |

---

## 11. Migration Checklist

### Phase 1: Core Drag Physics (Highest Priority)

- [ ] Add `dynamicanimation` dependency to `build.gradle.kts`
- [ ] Create `ScaledDragShadowBuilder.kt`
- [ ] Create `res/drawable/bg_drag_target_highlight.xml`
- [ ] Modify `ArchivedAppsAdapter.kt`:
  - [ ] Replace `setOnLongClickListener` to hide source view
  - [ ] Replace `DragListener` with enhanced version (visual feedback + restore visibility)
  - [ ] Add haptic feedback on long press, drop, and folder creation
- [ ] **Test:** Long-press app → icon disappears from grid, shadow appears
- [ ] **Test:** Drag over another app → target scales up with highlight
- [ ] **Test:** Drag away from target → target resets to normal
- [ ] **Test:** Drop cancelled → source icon pops back with overshoot animation
- [ ] **Test:** Drop on app → folder creation still works

### Phase 2: Re-Drag to Open World

- [ ] Add `onAppDropOnEmptySpace` callback to `ArchivedAppsRecyclerView`
- [ ] Add RecyclerView-level `setOnDragListener` in factory block
- [ ] Wire up in `ArchivedAppsList.kt`
- [ ] Add drag support to `FolderAppsAdapter`
- [ ] **Test:** Drag foldered app → drop on empty grid space → app leaves folder
- [ ] **Test:** Drag from expanded folder dialog → dialog dismisses, app continues drag on main grid
- [ ] **Test:** Last app dragged out of folder → folder auto-removes
  (Note: need to handle single-app folder cleanup in ViewModel)

### Phase 3: Modern Folder Expansion

- [ ] Create `res/layout/dialog_folder_expanded_modern.xml`
- [ ] Create `FolderExpandOverlay.kt`
- [ ] Add new IDs to `res/values/ids.xml`
- [ ] Replace `LaunchedEffect(expandedFolder)` in `ArchivedAppsList.kt`
- [ ] Update `bg_folder_icon.xml` with Material You colors
- [ ] **Test:** Tap folder → overlay expands with spring-scale animation from icon position
- [ ] **Test:** Tap scrim → overlay collapses with bouncy spring-back
- [ ] **Test:** Tap app inside folder → navigates to details, overlay dismisses
- [ ] **Test:** Edit folder title → title updates for all apps in folder
- [ ] **Test:** Blur backdrop visible on API 31+, graceful fallback on older

### Phase 4: Folder Naming UX

- [ ] Replace `LaunchedEffect(newFolderAppPair)` AlertDialog with immediate-create + open flow
- [ ] Add auto-focus title in `FolderExpandOverlay.show()` for new folders
- [ ] **Test:** Drag app onto app → folder created as \"New Folder\" → overlay opens with title selected
- [ ] **Test:** Type new name → press Done → folder renamed
- [ ] **Test:** Dismiss without typing → stays as \"New Folder\"

### Phase 5: Polish & Transitions

- [ ] Add layout animation to RecyclerView (staggered entrance)
- [ ] Add item animator with tuned durations
- [ ] Add folder icon sub-icon entrance animation
- [ ] Add category pill transition (scheduleLayoutAnimation)
- [ ] Create `res/anim/folder_expand_in.xml` and `folder_collapse_out.xml`
- [ ] **Test:** Archive tab loads → items animate in with stagger
- [ ] **Test:** Switch category → grid animates transition
- [ ] **Test:** New folder created → sub-icons fly into folder icon

### Phase 6: Optional Advanced Physics (DragOverlayController)

- [ ] Create `DragOverlayController.kt`
- [ ] Integrate with `ArchivedAppsAdapter` using touch-based approach
- [ ] **Test:** Drag icon → overlay follows finger with spring lag
- [ ] **Test:** Fast flick → overlay overshoots slightly before settling
- [ ] **Test:** Drop → overlay shrinks and fades
- [ ] **Test:** No regressions with standard system drag events

### Post-Flight Regression

- [ ] Full clean build (`./gradlew assembleDebug`) succeeds
- [ ] App launches, both tabs (Discover & Archive) work
- [ ] Search bar works on Archive tab
- [ ] Category pills work on Archive tab
- [ ] Drag app onto app → folder creation works
- [ ] Drag app onto folder → app joins folder
- [ ] Drag app out of folder to empty space → app leaves folder
- [ ] Tap folder → modern overlay with dolly-zoom
- [ ] Edit folder name → persisted
- [ ] Long-press folder → ungroups all apps
- [ ] No invisible/stuck icons after any drag operation
- [ ] Haptic feedback on all drag events
- [ ] No performance regression (check Systrace for jank)
- [ ] Dark mode / light mode both work correctly
- [ ] Material You dynamic colors applied to folder backgrounds

---

## Appendix A: Pixel Launcher Behavior Reference

| Feature | Pixel Launcher (Android 16 QPR3) | Decluttr Target |
|---------|-----------------------------------|-----------------|
| Drag start | Icon lifts (scales ~1.1x), original cell empties | Same |
| Drag shadow | System DragShadowBuilder (1:1 finger tracking) | ScaledDragShadowBuilder (1.1x) or SpringAnimation overlay |
| Drop on icon | Merge into folder with animation | Create folder + open overlay immediately |
| Drop on folder | Icon slides into folder preview | Immediate folder join |
| Drop on empty | Icon returns to cell with overshoot | Same (unfolder if from folder) |
| Folder open | Dolly-zoom: scale from icon + overshoot | SpringAnimation scale from anchor |
| Folder close | Bouncy jiggle: spring back to icon size | SpringAnimation reverse with damping |
| Folder rename | Tap title → inline edit with keyboard | Same (EditText in overlay) |
| Folder rearrange | Drag within folder grid | Not in scope (future) |

## Appendix B: Spring Parameters Cheat Sheet

| Animation | Stiffness | Damping | Effect |
|-----------|-----------|---------|--------|
| Folder open (scale) | `STIFFNESS_LOW` (200f) | `DAMPING_RATIO_LOW_BOUNCY` (0.2f) | Slow expansion with overshoot |
| Folder close (scale) | `STIFFNESS_MEDIUM` (1500f) | `DAMPING_RATIO_MEDIUM_BOUNCY` (0.5f) | Quick snap-back with slight bounce |
| Drag overlay follow | 800f (custom) | `DAMPING_RATIO_NO_BOUNCY` (1.0f) | Smooth lag, no overshoot |
| Icon reappear | N/A (ViewPropertyAnimator) | OvershootInterpolator(1.5f) | Pop-in effect |
| Target scale-up | N/A (ViewPropertyAnimator) | OvershootInterpolator(2-3f) | Bouncy highlight |

## Appendix C: Import Summary

### ArchivedAppsAdapter.kt — Add these imports:

```kotlin
import android.animation.ObjectAnimator
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
```

### ArchivedAppsRecyclerView.kt — Add these imports:

```kotlin
import android.view.DragEvent
import android.view.View
```

### ArchivedAppsList.kt — Add these imports:

```kotlin
import kotlinx.coroutines.delay
```

### build.gradle.kts — Add dependency:

```kotlin
implementation(\"androidx.dynamicanimation:dynamicanimation:1.0.0\")
```
"
