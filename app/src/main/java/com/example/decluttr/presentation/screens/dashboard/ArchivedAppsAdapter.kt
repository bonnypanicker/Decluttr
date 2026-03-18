package com.example.decluttr.presentation.screens.dashboard

import android.content.ClipData
import android.os.Build
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.decluttr.R
import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.presentation.util.AppIconModel

class ArchiveDiffCallback : DiffUtil.ItemCallback<ArchivedItem>() {
    override fun areItemsTheSame(oldItem: ArchivedItem, newItem: ArchivedItem): Boolean {
        return when {
            oldItem is ArchivedItem.App && newItem is ArchivedItem.App -> {
                oldItem.app.packageId == newItem.app.packageId
            }
            oldItem is ArchivedItem.Folder && newItem is ArchivedItem.Folder -> {
                oldItem.name == newItem.name
            }
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ArchivedItem, newItem: ArchivedItem): Boolean {
        return oldItem == newItem
    }
}

class ArchivedAppsAdapter(
    private val onAppClick: (String) -> Unit,
    private val onDeleteClick: (ArchivedApp) -> Unit,
    private val onAppStartDrag: (ArchivedApp) -> Unit,
    private val onAppDropOnApp: (ArchivedApp, ArchivedApp) -> Unit,
    private val onAppDropOnFolder: (ArchivedApp, String) -> Unit,
    private val onRemoveFolder: (List<ArchivedApp>) -> Unit,
    private val onFolderClick: (String) -> Unit
) : ListAdapter<ArchivedItem, RecyclerView.ViewHolder>(ArchiveDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when(getItem(position)) {
            is ArchivedItem.App -> 0
            is ArchivedItem.Folder -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val view = inflater.inflate(R.layout.item_archived_app, parent, false)
            AppViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_archived_folder, parent, false)
            FolderViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is AppViewHolder && item is ArchivedItem.App) {
            holder.bind(item)
        } else if (holder is FolderViewHolder && item is ArchivedItem.Folder) {
            holder.bind(item)
        }
    }

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.app_icon)
        private val name = view.findViewById<TextView>(R.id.app_name)

        fun bind(appItem: ArchivedItem.App) {
            val app = appItem.app
            itemView.tag = appItem
            name.text = app.name
            icon.load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
            }

            itemView.setOnClickListener { onAppClick(app.packageId) }
            itemView.setOnLongClickListener {
                onAppStartDrag(app)
                val clipData = ClipData.newPlainText("packageId", app.packageId)
                val shadowBuilder = View.DragShadowBuilder(itemView)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    itemView.startDragAndDrop(clipData, shadowBuilder, app, 0)
                } else {
                    @Suppress("DEPRECATION")
                    itemView.startDrag(clipData, shadowBuilder, app, 0)
                }
                true
            }
            itemView.setOnDragListener(DragListener())
        }
    }

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val folderName = view.findViewById<TextView>(R.id.folder_name)
        private val icon1 = view.findViewById<ImageView>(R.id.folder_icon_1)
        private val icon2 = view.findViewById<ImageView>(R.id.folder_icon_2)
        private val icon3 = view.findViewById<ImageView>(R.id.folder_icon_3)
        private val icon4 = view.findViewById<ImageView>(R.id.folder_icon_4)

        fun bind(folderItem: ArchivedItem.Folder) {
            itemView.tag = folderItem
            folderName.text = folderItem.name
            
            val apps = folderItem.apps.take(4)
            val icons = listOf(icon1, icon2, icon3, icon4)
            
            icons.forEach { it.setImageDrawable(null) } // Clear previous
            apps.forEachIndexed { index, app ->
                icons[index].load(AppIconModel(app.packageId)) {
                    memoryCacheKey(app.packageId)
                    crossfade(false)
                }
            }

            itemView.setOnClickListener { onFolderClick(folderItem.name) }
            itemView.setOnLongClickListener {
                // Allows Long Click to delete Folder since we dropped DropdownMenu earlier
                onRemoveFolder(folderItem.apps)
                true
            }
            itemView.setOnDragListener(DragListener())
        }
    }

    private inner class DragListener : View.OnDragListener {
        override fun onDrag(view: View, event: DragEvent): Boolean {
            val targetItem = view.tag as? ArchivedItem

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    return event.clipDescription.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    return true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
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
                    return true
                }
                else -> return false
            }
        }
    }
}
