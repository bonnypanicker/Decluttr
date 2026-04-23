package com.tool.decluttr.presentation.screens.dashboard

import android.content.Context
import android.os.Build
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
        grid.layoutManager = GridLayoutManager(context, 4)
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
                
                // Force the RecyclerView to re-layout immediately
                val rv = parentView.findViewWithTag<RecyclerView>("archive_recycler")
                rv?.requestLayout()

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

        animateAnchorFolderIcons(visible = false, animate = true)

        // ── OPEN ANIMATION ──

        // Calculate anchor position for animation origin
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

        // Scrim fade in
        scrim.alpha = 0f
        scrim.animate()
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()

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
     * Updates the apps in the grid without recreating the overlay.
     */
    fun updateApps(newApps: List<ArchivedApp>) {
        android.util.Log.v(TAG, "FOLDER_OVERLAY updateApps size=${newApps.size} isExpanded=$isExpanded hasView=${overlayView != null}")
        val grid = overlayView?.findViewById<RecyclerView>(R.id.folder_grid)
        val adapter = grid?.adapter as? FolderAppsAdapter
        adapter?.updateData(newApps)
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
        animateAnchorFolderIcons(visible = true, animate = true)

        // Scrim fade out
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
}
