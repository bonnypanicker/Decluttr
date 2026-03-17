package com.example.decluttr.presentation.screens.dashboard

import android.content.ClipData
import android.os.Build
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.RecyclerView
import com.example.decluttr.domain.model.ArchivedApp

class ArchivedAppsAdapter(
    private var items: List<ArchivedItem>,
    private val onAppClick: (String) -> Unit,
    private val onDeleteClick: (ArchivedApp) -> Unit,
    private val onAppStartDrag: (ArchivedApp) -> Unit,
    private val onAppDropOnApp: (ArchivedApp, ArchivedApp) -> Unit,
    private val onAppDropOnFolder: (ArchivedApp, String) -> Unit,
    private val onRemoveFolder: (List<ArchivedApp>) -> Unit
) : RecyclerView.Adapter<ArchivedAppsAdapter.ComposeViewHolder>() {

    fun updateItems(newItems: List<ArchivedItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComposeViewHolder {
        val composeView = ComposeView(parent.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Enable drag listening on the root ComposeView
            setOnDragListener(DragListener())
        }
        return ComposeViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ComposeViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ComposeViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        fun bind(item: ArchivedItem) {
            // Attach data to the view tag for the DragListener to identify drop targets
            composeView.tag = item

            if (item is ArchivedItem.App) {
                composeView.setOnLongClickListener { view ->
                    onAppStartDrag(item.app)
                    val clipData = ClipData.newPlainText("packageId", item.app.packageId)
                    val shadowBuilder = View.DragShadowBuilder(view)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        view.startDragAndDrop(clipData, shadowBuilder, item.app, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        view.startDrag(clipData, shadowBuilder, item.app, 0)
                    }
                    true
                }
            } else {
                // Folders aren't natively draggable in our current spec
                composeView.setOnLongClickListener(null)
            }

            composeView.setContent {
                when (item) {
                    is ArchivedItem.App -> {
                        // Render standard App item (without Compose drag gesture detectors)
                        AppDrawerItemDraggable(
                            app = item.app,
                            isDragging = false, // Flow handles native shadow, item remains visible
                            onClick = { onAppClick(item.app.packageId) },
                            onDeleteClick = { onDeleteClick(item.app) },
                            onDragStart = { /* Handled natively by setOnLongClickListener */ },
                            onDrag = { /* Native */ },
                            onDragEnd = { /* Native */ }
                        )
                    }
                    is ArchivedItem.Folder -> {
                        FolderDrawerItem(
                            folderName = item.name,
                            apps = item.apps,
                            onClick = { /* Expand folder if needed */ },
                            onDeleteClick = { onRemoveFolder(item.apps) }
                        )
                    }
                }
            }
        }
    }

    private inner class DragListener : View.OnDragListener {
        override fun onDrag(view: View, event: DragEvent): Boolean {
            val targetItem = view.tag as? ArchivedItem

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Only accept if the dragged item provides our expected data
                    return event.clipDescription.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    // Optional: highlight view via Compose state if it's a valid target
                    if (targetItem != null && event.localState != targetItem) {
                        // For a seamless look, we could pass a `isDropTarget = true` state down via a map or SharedFlow
                    }
                    return true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    // Remove highlight
                    return true
                }
                DragEvent.ACTION_DROP -> {
                    val draggedApp = event.localState as? ArchivedApp
                    if (draggedApp != null && targetItem != null) {
                        if (targetItem is ArchivedItem.App && draggedApp.packageId != targetItem.app.packageId) {
                            onAppDropOnApp(draggedApp, targetItem.app)
                        } else if (targetItem is ArchivedItem.Folder) {
                            onAppDropOnFolder(draggedApp, targetItem.name)
                        }
                        return true
                    }
                    return false
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Cleanup any highlight states
                    return true
                }
                else -> return false
            }
        }
    }
}
