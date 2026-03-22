package com.tool.decluttr.presentation.screens.dashboard

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tool.decluttr.domain.model.ArchivedApp

@Composable
fun ArchivedAppsRecyclerView(
    items: List<ArchivedItem>,
    onAppClick: (String) -> Unit,
    onDeleteClick: (ArchivedApp) -> Unit,
    onAppStartDrag: (ArchivedApp) -> Unit,
    onAppDropOnApp: (ArchivedApp, ArchivedApp) -> Unit,
    onAppDropOnFolder: (ArchivedApp, String) -> Unit,
    onAppDropOnEmptySpace: (ArchivedApp) -> Unit,  // NEW: unfolder callback
    onRemoveFolder: (List<ArchivedApp>) -> Unit,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val adapter = remember {
        ArchivedAppsAdapter(
            onAppClick = onAppClick,
            onDeleteClick = onDeleteClick,
            onAppStartDrag = onAppStartDrag,
            onAppDropOnApp = onAppDropOnApp,
            onAppDropOnFolder = onAppDropOnFolder,
            onRemoveFolder = onRemoveFolder,
            onFolderClick = onFolderClick
        )
    }

    // Update adapter whenever items change
    adapter.submitList(items)

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

                // Add layout animation for staggered entrance
                val enterAnim = android.view.animation.AnimationUtils.loadAnimation(
                    ctx, android.R.anim.fade_in
                ).apply {
                    duration = 200
                }
                val controller = android.view.animation.LayoutAnimationController(enterAnim, 0.05f)
                controller.order = android.view.animation.LayoutAnimationController.ORDER_NORMAL
                layoutAnimation = controller

                // Enhanced item animator with physics-like timing
                itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                    addDuration = 250
                    removeDuration = 200
                    moveDuration = 300
                    changeDuration = 200
                }

                // RecyclerView-level drag listener for "drop on empty space"
                setOnDragListener { recyclerView, event ->
                    when (event.action) {
                        android.view.DragEvent.ACTION_DRAG_STARTED -> {
                            event.clipDescription.hasMimeType(
                                android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
                            )
                        }
                        android.view.DragEvent.ACTION_DROP -> {
                            val draggedApp = event.localState as? ArchivedApp
                            if (draggedApp != null && draggedApp.folderName != null) {
                                // Dropped on empty space — check if we're NOT over a child view
                                val rv = recyclerView as RecyclerView
                                val childUnder = rv.findChildViewUnder(event.x, event.y)
                                if (childUnder == null) {
                                    // Drop on empty space = "unfolder" this app
                                    onAppDropOnEmptySpace(draggedApp)
                                    true
                                } else {
                                    false // Let the child's DragListener handle it
                                }
                            } else {
                                false
                            }
                        }
                        android.view.DragEvent.ACTION_DRAG_ENDED -> {
                            // Restore all invisible children (drag source)
                            for (i in 0 until (recyclerView as RecyclerView).childCount) {
                                val child = (recyclerView as RecyclerView).getChildAt(i)
                                if (child.visibility == android.view.View.INVISIBLE) {
                                    child.visibility = android.view.View.VISIBLE
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
        update = { recyclerView ->
            recyclerView.adapter = adapter
        }
    )
}
