package com.tool.decluttr.presentation.screens.dashboard

import android.content.Context
import android.os.Build
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.google.android.material.card.MaterialCardView
import java.lang.ref.WeakReference

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
    companion object {
        private const val TAG = "DecluttrDragDbg"
    }

    private var overlayView: View? = null
    private var isExpanded = false
    private var anchorViewRef: WeakReference<View>? = null
    private var folderAppCount: Int = 0

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
        android.util.Log.d(
            TAG,
            "FOLDER_OVERLAY show folder=$folderName appCount=${folderApps.size} isExpanded=$isExpanded anchor=${anchorView != null}"
        )
        if (isExpanded) return
        isExpanded = true
        folderAppCount = folderApps.size
        anchorViewRef = WeakReference(anchorView)

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

        // Keep keyboard closed on open. Folder title can still be edited manually.

        // Setup grid
        val gridLayoutManager = GridLayoutManager(context, spanCountFor(folderApps.size))
        grid.layoutManager = gridLayoutManager
        grid.adapter = FolderAppsAdapter(
            apps = folderApps,
            onAppClick = { packageId ->
                dismiss(onDismiss)
                onAppClick(packageId)
            },
            onDragStartFromFolder = {
                android.util.Log.d(TAG, "FOLDER_OVERLAY drag start from folder=$folderName gridChildren=${grid.childCount}")
                // Restore any hidden source view before tearing down overlay.
                for (i in 0 until grid.childCount) {
                    val child = grid.getChildAt(i)
                    if (child.visibility == View.INVISIBLE) {
                        child.visibility = View.VISIBLE
                        child.alpha = 1f
                        child.scaleX = 1f
                        child.scaleY = 1f
                    }
                }
                // Instantly remove the overlay without animation during drag
                parentView.removeView(overlayView)
                overlayView = null
                isExpanded = false
                animateAnchorFolderIcons(visible = true, animate = false)
                android.util.Log.d(TAG, "FOLDER_OVERLAY removed instantly for drag folder=$folderName")
                onDismiss()
                onDragStartFromFolder?.invoke()
            }
        )

        // Tap scrim to close
        scrim.setOnClickListener { dismiss(onDismiss) }

        // Add overlay to parent
        parentView.addView(overlayView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        applyAdaptiveCardLayout(card, grid, folderApps.size)

        animateAnchorFolderIcons(visible = false, animate = true)

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
            // Note: I will NOT re-add setRenderEffect to parentView here as we removed it
            // previously to fix the blurry overlay bug. I'll leave this empty or use the fixed version
            // wait, we removed the blur entirely earlier.
            // Let's not add the blur back.
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
            applyCardAnchorPosition(card)
            val cardLocation = IntArray(2)
            card.getLocationInWindow(cardLocation)

            if (hasAnchor) {
                // Compute pivot relative to card's bounds
                val anchorWindowX = anchorLocation[0] + (anchorView?.width ?: 0) / 2
                val anchorWindowY = anchorLocation[1] + (anchorView?.height ?: 0) / 2
                setPivotFromAnchor(card, anchorWindowX, anchorWindowY, cardLocation)
            } else {
                // No anchor (drag-created folder) — expand from center of card
                card.pivotX = card.width / 2f
                card.pivotY = card.height / 2f
            }

            // Phase 1: Card scale-up with spring (Pixel Launcher "dolly zoom" feel)
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
            // Delay slightly so the card "arrives" first, then content materializes
            contentLayout?.animate()
                ?.alpha(1f)
                ?.setStartDelay(100)
                ?.setDuration(200)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.start()
        }
    }

    /**
     * Updates the apps in the grid without recreating the overlay.
     */
    fun updateApps(newApps: List<ArchivedApp>) {
        android.util.Log.v(TAG, "FOLDER_OVERLAY updateApps size=${newApps.size} isExpanded=$isExpanded hasView=${overlayView != null}")
        val grid = overlayView?.findViewById<RecyclerView>(R.id.folder_grid)
        val card = overlayView?.findViewById<MaterialCardView>(R.id.folder_card)
        val adapter = grid?.adapter as? FolderAppsAdapter
        adapter?.updateData(newApps)
        if (grid != null && card != null && folderAppCount != newApps.size) {
            folderAppCount = newApps.size
            applyAdaptiveCardLayout(card, grid, newApps.size)
            card.post { applyCardAnchorPosition(card) }
        }
    }

    /**
     * Dismiss with bouncy spring-back animation (Pixel Launcher QPR3 style).
     */
    fun dismiss(onDismiss: () -> Unit) {
        android.util.Log.d(TAG, "FOLDER_OVERLAY dismiss requested isExpanded=$isExpanded hasView=${overlayView != null}")
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

            setPivotFromAnchor(card, anchorWindowX, anchorWindowY, cardLocation)
        } else {
            card.pivotX = card.width / 2f
            card.pivotY = card.height / 2f
        }
        // If no anchor, pivot stays where it was set during open (center or anchor)

        // Phase 1: Content fades out quickly
        contentLayout?.animate()
            ?.alpha(0f)
            ?.setDuration(80)
            ?.start()

        // Phase 2: Card springs back to small scale (Pixel QPR3 "bouncy jiggle" close)
        val closeStiffness = 1200f   // faster than open for a "snap back" feel
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
                android.util.Log.d(TAG, "FOLDER_OVERLAY dismiss completed")
                onDismiss()
            }
            .start()
    }

    private fun animateAnchorFolderIcons(visible: Boolean, animate: Boolean) {
        val anchor = anchorViewRef?.get() ?: return
        if (!anchor.isAttachedToWindow) return
        val ids = intArrayOf(
            R.id.folder_icon_1,
            R.id.folder_icon_2,
            R.id.folder_icon_3,
            R.id.folder_icon_4
        )
        ids.forEachIndexed { index, id ->
            val iconView = anchor.findViewById<View>(id) ?: return@forEachIndexed
            iconView.animate().cancel()
            if (animate) {
                if (visible) {
                    iconView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay(index * 15L)
                        .setDuration(140L)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                } else {
                    iconView.animate()
                        .alpha(0f)
                        .scaleX(0.7f)
                        .scaleY(0.7f)
                        .setStartDelay(index * 15L)
                        .setDuration(120L)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .start()
                }
            } else {
                iconView.alpha = if (visible) 1f else 0f
                iconView.scaleX = if (visible) 1f else 0.7f
                iconView.scaleY = if (visible) 1f else 0.7f
            }
        }
    }

    private fun spanCountFor(count: Int): Int {
        return when {
            count <= 2 -> 2
            count <= 4 -> 2
            else -> 3
        }
    }

    private fun applyAdaptiveCardLayout(
        card: MaterialCardView,
        grid: RecyclerView,
        appCount: Int
    ) {
        val span = spanCountFor(appCount)
        val layout = grid.layoutManager as? GridLayoutManager
        if (layout == null) {
            grid.layoutManager = GridLayoutManager(context, span)
        } else {
            layout.spanCount = span
        }

        val widthPercent = when {
            appCount <= 2 -> 0.48f
            appCount <= 4 -> 0.60f
            else -> 0.72f
        }
        val minWidth = dpToPx(220)
        val maxWidth = dpToPx(380)
        val target = (parentView.width * widthPercent).toInt().coerceIn(minWidth, maxWidth)
        card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply {
            width = target
        }
        grid.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
    }

    private fun applyCardAnchorPosition(card: MaterialCardView) {
        val lp = card.layoutParams as FrameLayout.LayoutParams
        val anchor = anchorViewRef?.get()
        if (anchor == null || !anchor.isAttachedToWindow) {
            lp.gravity = Gravity.CENTER
            lp.topMargin = 0
            lp.leftMargin = 0
            card.layoutParams = lp
            return
        }

        val anchorLoc = IntArray(2)
        val parentLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        parentView.getLocationInWindow(parentLoc)

        val anchorRect = Rect(
            anchorLoc[0] - parentLoc[0],
            anchorLoc[1] - parentLoc[1],
            anchorLoc[0] - parentLoc[0] + anchor.width,
            anchorLoc[1] - parentLoc[1] + anchor.height
        )

        val verticalGap = dpToPx(10)
        val safeTop = findGridTopBoundary().coerceAtLeast(dpToPx(16))
        val safeHorizontal = dpToPx(12)
        val targetTop = (anchorRect.top - card.height - verticalGap).coerceAtLeast(safeTop)
        val desiredLeft = anchorRect.centerX() - card.width / 2
        val maxLeft = (parentView.width - card.width - safeHorizontal).coerceAtLeast(safeHorizontal)
        val targetLeft = desiredLeft.coerceIn(safeHorizontal, maxLeft)

        lp.gravity = Gravity.TOP or Gravity.START
        lp.topMargin = targetTop
        lp.leftMargin = targetLeft
        card.layoutParams = lp
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun findGridTopBoundary(): Int {
        val anchor = anchorViewRef?.get() ?: return dpToPx(16)
        val rv = findParentRecyclerView(anchor) ?: return dpToPx(16)
        val rvLoc = IntArray(2)
        val parentLoc = IntArray(2)
        rv.getLocationInWindow(rvLoc)
        parentView.getLocationInWindow(parentLoc)
        return (rvLoc[1] - parentLoc[1]) + dpToPx(8)
    }

    private fun findParentRecyclerView(view: View): RecyclerView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is RecyclerView) return parent
            parent = parent.parent
        }
        return null
    }

    private fun setPivotFromAnchor(
        card: MaterialCardView,
        anchorWindowX: Int,
        anchorWindowY: Int,
        cardLocation: IntArray
    ) {
        val rawPivotX = (anchorWindowX - cardLocation[0]).toFloat()
        val rawPivotY = (anchorWindowY - cardLocation[1]).toFloat()
        val insideX = rawPivotX in 0f..card.width.toFloat()
        val insideY = rawPivotY in 0f..card.height.toFloat()
        if (insideX && insideY) {
            card.pivotX = rawPivotX
            card.pivotY = rawPivotY
        } else {
            // When constrained by screen edges, fallback to center to avoid corner-origin expansion.
            card.pivotX = card.width / 2f
            card.pivotY = card.height / 2f
        }
    }
}
