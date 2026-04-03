package com.tool.decluttr.presentation.screens.dashboard

import android.animation.ObjectAnimator
import android.content.ClipData
import android.os.Build
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.util.AppIconModel

sealed class ArchivedItem {
    data class App(val app: ArchivedApp) : ArchivedItem()

    data class Folder(
        val name: String,
        val apps: List<ArchivedApp>
    ) : ArchivedItem()
}

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
    private var pendingDropAction: (() -> Unit)? = null
    private var draggingPackageId: String? = null
    private val pulseAnimators = mutableMapOf<View, ObjectAnimator>()

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
            itemView.animate().cancel()
            pulseAnimators.remove(itemView)?.cancel()
            itemView.visibility = View.VISIBLE
            itemView.alpha = 1f
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            name.text = app.name
            icon.load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
            }

            itemView.setOnClickListener { onAppClick(app.packageId) }
            itemView.setOnLongClickListener { view ->
                onAppStartDrag(app)

                // 1. Create clip data for the drag
                val clipData = ClipData.newPlainText("packageId", app.packageId)

                // 2. Build a scaled-up shadow (Pixel Launcher uses ~1.1x scale)
                val shadowBuilder = ScaledDragShadowBuilder(view, 1.1f)

                // 3. Start drag
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    view.startDragAndDrop(clipData, shadowBuilder, app, 0)
                } else {
                    @Suppress("DEPRECATION")
                    view.startDrag(clipData, shadowBuilder, app, 0)
                }

                if (started) {
                    draggingPackageId = app.packageId
                    // 4. CRITICAL: Hide the source view so it doesn't duplicate
                    //    Pixel Launcher hides the icon from its cell during drag.
                    view.visibility = View.INVISIBLE

                    // Haptic feedback like Pixel Launcher
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                }

                started
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
            itemView.animate().cancel()
            pulseAnimators.remove(itemView)?.cancel()
            itemView.visibility = View.VISIBLE
            itemView.alpha = 1f
            itemView.scaleX = 1f
            itemView.scaleY = 1f
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
        private var originalBackground: android.graphics.drawable.Drawable? = null
        private var pulseAnimator: ObjectAnimator? = null

        override fun onDrag(view: View, event: DragEvent): Boolean {
            val rv = findParentRecyclerView(view)
            val pos = rv?.getChildAdapterPosition(view) ?: -1
            android.util.Log.d("ArchivedAppsAdapter", "onDrag action=${event.action} adapterPos=$pos")
            val targetItem = view.tag as? ArchivedItem
            val draggedApp = event.localState as? ArchivedApp

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    pendingDropAction = null
                    val ok = event.clipDescription?.hasMimeType(
                        android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
                    ) == true
                    android.util.Log.d("ArchivedAppsAdapter", "DRAG_STARTED ok=$ok hasLocal=${draggedApp!=null}")
                    return ok
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    // Visual feedback: scale up + highlight the drop target
                    if (targetItem != null && draggedApp != null) {
                        when (targetItem) {
                            is ArchivedItem.App -> {
                                if (draggedApp.packageId != targetItem.app.packageId) {
                                    // Scale up + dashed border = "will create folder"
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
                                // Folder "breathing" pulse animation
                                originalBackground = view.background
                                view.setBackgroundResource(R.drawable.bg_drag_target_highlight)

                                pulseAnimator = ObjectAnimator.ofFloat(
                                    view, "scaleX", 1.0f, 1.08f
                                ).apply {
                                    duration = 600
                                    repeatMode = android.animation.ValueAnimator.REVERSE
                                    repeatCount = android.animation.ValueAnimator.INFINITE
                                    interpolator = AccelerateDecelerateInterpolator()
                                    addUpdateListener { anim ->
                                        view.scaleY = anim.animatedValue as Float
                                    }
                                    start()
                                }
                                pulseAnimators[view] = pulseAnimator!!
                            }
                        }
                    }
                    return true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    // Reset visual feedback
                    pulseAnimator?.cancel()
                    pulseAnimator = null
                    pulseAnimators.remove(view)
                    view.background = originalBackground
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    return true
                }

                DragEvent.ACTION_DROP -> {
                    android.util.Log.d("ArchivedAppsAdapter", "DROP target=$targetItem dragged=${draggedApp?.packageId}")
                    // Reset target visuals
                    pulseAnimator?.cancel()
                    pulseAnimator = null
                    pulseAnimators.remove(view)
                    view.background = originalBackground
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                    if (draggedApp != null && targetItem != null) {
                        // Haptic on successful drop
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CONFIRM
                        )

                        try {
                            pendingDropAction = when {
                                targetItem is ArchivedItem.App &&
                                    draggedApp.packageId != targetItem.app.packageId -> {
                                    {
                                        onAppDropOnApp(draggedApp, targetItem.app)
                                    }
                                }
                                targetItem is ArchivedItem.Folder -> {
                                    {
                                        onAppDropOnFolder(draggedApp, targetItem.name)
                                    }
                                }
                                else -> null
                            }
                        } catch (t: Throwable) {
                            android.util.Log.e("ArchivedAppsAdapter", "DROP failed", t)
                            return false
                        }
                        return true
                    }
                    return false
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    // CRITICAL: Restore visibility of the source view
                    pulseAnimator?.cancel()
                    pulseAnimator = null
                    pulseAnimators.remove(view)
                    view.background = originalBackground
                    view.scaleX = 1f
                    view.scaleY = 1f

                    // Find the source view in the RecyclerView and make it visible again
                    val recyclerView = findParentRecyclerView(view)
                    recyclerView?.let { rv ->
                        for (i in 0 until rv.childCount) {
                            val child = rv.getChildAt(i)
                            if (child.visibility == View.INVISIBLE) {
                                // Animate back in with a spring-like pop
                                child.animate().cancel()
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
                        val dropAction = pendingDropAction
                        pendingDropAction = null
                        if (dropAction != null) {
                            rv.postDelayed({
                                runCatching { dropAction.invoke() }
                                    .onFailure {
                                        android.util.Log.e("ArchivedAppsAdapter", "Executing pending drop action failed", it)
                                    }
                            }, 260L)
                        }
                    }
                    draggingPackageId = null
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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.animate().cancel()
        pulseAnimators.remove(holder.itemView)?.cancel()
        holder.itemView.visibility = View.VISIBLE
        holder.itemView.alpha = 1f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
    }
}
