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
import java.lang.ref.WeakReference

sealed class ArchivedItem {
    data class App(val app: ArchivedApp) : ArchivedItem()

    data class Folder(
        val name: String,
        val apps: List<ArchivedApp>
    ) : ArchivedItem()
}

data class AppListMeta(
    val sizeLabel: String,
    val uninstallDateLabel: String
)

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
    private val onFolderClick: (String, View) -> Unit,
    private val appMetaProvider: (ArchivedApp) -> AppListMeta
) : ListAdapter<ArchivedItem, RecyclerView.ViewHolder>(ArchiveDiffCallback()) {
    companion object {
        private const val TAG = "DecluttrDragDbg"
    }

    private var pendingDropAction: (() -> Unit)? = null
    private var draggingPackageId: String? = null
    private var draggingViewRef: WeakReference<View>? = null
    private var dragInFlight: Boolean = false
    private var dragEndScheduled: Boolean = false
    private var dragSessionCounter: Long = 0L
    private var activeDragSessionId: Long = -1L
    private val pulseAnimators = mutableMapOf<View, ObjectAnimator>()
    private var isListMode: Boolean = false

    fun setListMode(enabled: Boolean) {
        if (isListMode != enabled) {
            isListMode = enabled
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when(getItem(position)) {
            is ArchivedItem.App -> if (isListMode) 2 else 0
            is ArchivedItem.Folder -> if (isListMode) 3 else 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> {
                val view = inflater.inflate(R.layout.item_archived_app, parent, false)
                AppViewHolder(view)
            }
            1 -> {
                val view = inflater.inflate(R.layout.item_archived_folder, parent, false)
                FolderViewHolder(view)
            }
            2 -> {
                val view = inflater.inflate(R.layout.item_archived_app_list, parent, false)
                AppListViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_archived_folder_list, parent, false)
                FolderListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is AppViewHolder && item is ArchivedItem.App) {
            holder.bind(item)
        } else if (holder is FolderViewHolder && item is ArchivedItem.Folder) {
            holder.bind(item)
        } else if (holder is AppListViewHolder && item is ArchivedItem.App) {
            holder.bind(item)
        } else if (holder is FolderListViewHolder && item is ArchivedItem.Folder) {
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
            name.text = toDisplayName(app.name, app.packageId)
            icon.load(iconDataFor(app)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
                placeholder(R.drawable.ic_launcher)
                error(R.drawable.ic_launcher)
            }

            itemView.setOnClickListener { onAppClick(app.packageId) }
            itemView.setOnLongClickListener { view ->
                onAppStartDrag(app)

                // 1. Create clip data for the drag
                val clipData = ClipData.newPlainText("packageId", app.packageId)

                // 2. Build a scaled-up shadow (Pixel Launcher uses ~1.1x scale)
                val shadowBuilder = ScaledDragShadowBuilder(icon, 1.1f)

                // 3. Start drag
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    view.startDragAndDrop(clipData, shadowBuilder, app, 0)
                } else {
                    @Suppress("DEPRECATION")
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
                        "session=$activeDragSessionId START_DRAG pkg=${app.packageId} pos=${bindingAdapterPosition} view=${describeView(view)}"
                    )
                    // 4. CRITICAL: Hide the source view so it doesn't duplicate
                    //    Pixel Launcher hides the icon from its cell during drag.
                    view.visibility = View.INVISIBLE

                    // Haptic feedback like Pixel Launcher
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                }

                if (!started) {
                    android.util.Log.w(
                        TAG,
                        "session=$activeDragSessionId START_DRAG_FAILED pkg=${app.packageId} pos=${bindingAdapterPosition} view=${describeView(view)}"
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
            
            icons.forEach {
                it.animate().cancel()
                it.alpha = 1f
                it.scaleX = 1f
                it.scaleY = 1f
                it.setImageDrawable(null)
            } // Clear previous and reset icon state
            apps.forEachIndexed { index, app ->
                icons[index].load(iconDataFor(app)) {
                    memoryCacheKey(app.packageId)
                    crossfade(false)
                    placeholder(R.drawable.ic_launcher)
                    error(R.drawable.ic_launcher)
                }
            }

            itemView.setOnClickListener { onFolderClick(folderItem.name, itemView) }
            itemView.setOnLongClickListener {
                // Allows Long Click to delete Folder since we dropped DropdownMenu earlier
                onRemoveFolder(folderItem.apps)
                true
            }
            itemView.setOnDragListener(DragListener())
        }
    }

    inner class AppListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.app_icon)
        private val name = view.findViewById<TextView>(R.id.app_name)
        private val meta = view.findViewById<TextView>(R.id.app_meta)

        fun bind(appItem: ArchivedItem.App) {
            val app = appItem.app
            itemView.tag = appItem
            name.text = toDisplayName(app.name, app.packageId)
            val m = appMetaProvider(app)
            val category = app.category ?: "Uncategorized"
            meta.text = "${m.sizeLabel} • ${m.uninstallDateLabel} • $category"
            icon.load(iconDataFor(app)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
                placeholder(R.drawable.ic_launcher)
                error(R.drawable.ic_launcher)
            }
            itemView.setOnClickListener { onAppClick(app.packageId) }
        }
    }

    inner class FolderListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val folderName = view.findViewById<TextView>(R.id.folder_name)
        private val folderMeta = view.findViewById<TextView>(R.id.folder_meta)

        fun bind(folderItem: ArchivedItem.Folder) {
            itemView.tag = folderItem
            folderName.text = folderItem.name
            folderMeta.text = "${folderItem.apps.size} apps"
            itemView.setOnClickListener { onFolderClick(folderItem.name, itemView) }
        }
    }

    private inner class DragListener : View.OnDragListener {
        private var originalBackground: android.graphics.drawable.Drawable? = null

        override fun onDrag(view: View, event: DragEvent): Boolean {
            try {
                val rv = findParentRecyclerView(view)
                val pos = rv?.getChildAdapterPosition(view) ?: -1
                val targetItem = view.tag as? ArchivedItem
                val draggedApp = event.localState as? ArchivedApp

                android.util.Log.v(
                    TAG,
                    "session=$activeDragSessionId action=${dragActionName(event.action)} pos=$pos target=${describeTarget(targetItem)} dragged=${describeApp(draggedApp)} view=${describeView(view)}"
                )

                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        val ok = event.clipDescription?.hasMimeType(
                            android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
                        ) == true
                        if (ok && !dragInFlight) {
                            dragSessionCounter += 1L
                            activeDragSessionId = dragSessionCounter
                            dragInFlight = true
                            dragEndScheduled = false
                            pendingDropAction = null
                            draggingPackageId = draggedApp?.packageId
                            draggingViewRef = null
                            android.util.Log.d(
                                TAG,
                                "session=$activeDragSessionId START_FROM_EXTERNAL dragged=${describeApp(draggedApp)}"
                            )
                        }
                        android.util.Log.d(
                            TAG,
                            "session=$activeDragSessionId DRAG_STARTED ok=$ok hasLocal=${draggedApp != null} pos=$pos"
                        )
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
                                        android.util.Log.d(
                                            TAG,
                                            "session=$activeDragSessionId DRAG_ENTERED_APP targetPkg=${targetItem.app.packageId} draggedPkg=${draggedApp.packageId}"
                                        )
                                    }
                                }
                                is ArchivedItem.Folder -> {
                                    // Folder "breathing" pulse animation
                                    originalBackground = view.background
                                    view.setBackgroundResource(R.drawable.bg_drag_target_highlight)

                                    val pulseAnimator = ObjectAnimator.ofFloat(
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
                                    pulseAnimators[view] = pulseAnimator
                                    android.util.Log.d(
                                        TAG,
                                        "session=$activeDragSessionId DRAG_ENTERED_FOLDER targetFolder=${targetItem.name} draggedPkg=${draggedApp.packageId}"
                                    )
                                }
                            }
                        }
                        return true
                    }

                    DragEvent.ACTION_DRAG_EXITED -> {
                        // Reset visual feedback
                        pulseAnimators.remove(view)?.cancel()
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
                        android.util.Log.d(
                            TAG,
                            "session=$activeDragSessionId DROP target=${describeTarget(targetItem)} dragged=${describeApp(draggedApp)} pos=$pos"
                        )
                        // Reset target visuals
                        pulseAnimators.remove(view)?.cancel()
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
                                android.util.Log.d(
                                    TAG,
                                    "session=$activeDragSessionId DROP_QUEUED actionPresent=${pendingDropAction != null}"
                                )
                            } catch (t: Throwable) {
                                android.util.Log.e(TAG, "session=$activeDragSessionId DROP failed", t)
                                return false
                            }
                            return true
                        }
                        return false
                    }

                    DragEvent.ACTION_DRAG_ENDED -> {
                        if (!dragInFlight || dragEndScheduled) {
                            android.util.Log.v(
                                TAG,
                                "session=$activeDragSessionId DRAG_ENDED skipped duplicate/inactive pos=$pos"
                            )
                            return true
                        }
                        dragEndScheduled = true
                        val recyclerView = findParentRecyclerView(view)
                        val dropAction = pendingDropAction
                        pendingDropAction = null
                        val targetView = recyclerView ?: view
                        targetView.post {
                            finalizeDragSession(
                                recyclerView = recyclerView,
                                endingView = view,
                                endingViewOriginalBackground = originalBackground,
                                dropAction = dropAction
                            )
                        }
                        return true
                    }

                    else -> return false
                }
            } catch (t: Throwable) {
                android.util.Log.e(
                    TAG,
                    "session=$activeDragSessionId onDrag exception action=${dragActionName(event.action)} view=${describeView(view)}",
                    t
                )
                throw t
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

    private fun restoreDraggedSourceView(view: View) {
        android.util.Log.v(TAG, "session=$activeDragSessionId restoreDraggedSourceView view=${describeView(view)}")
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.scaleX = 0.5f
        view.scaleY = 0.5f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    private fun finalizeDragSession(
        recyclerView: RecyclerView?,
        endingView: View,
        endingViewOriginalBackground: android.graphics.drawable.Drawable?,
        dropAction: (() -> Unit)?
    ) {
        try {
            pulseAnimators.remove(endingView)?.cancel()
            endingView.background = endingViewOriginalBackground
            endingView.scaleX = 1f
            endingView.scaleY = 1f

            val dragSource = draggingViewRef?.get()
            if (dragSource != null && dragSource.visibility != View.VISIBLE) {
                android.util.Log.d(
                    TAG,
                    "session=$activeDragSessionId FINALIZE restore source=weakRef view=${describeView(dragSource)}"
                )
                restoreDraggedSourceView(dragSource)
            } else {
                val draggingPkg = draggingPackageId
                if (draggingPkg != null && recyclerView != null) {
                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)
                        val tagged = child.tag as? ArchivedItem.App
                        if (tagged?.app?.packageId == draggingPkg && child.visibility != View.VISIBLE) {
                            android.util.Log.d(
                                TAG,
                                "session=$activeDragSessionId FINALIZE restore source=scan pkg=$draggingPkg child=${describeView(child)}"
                            )
                            restoreDraggedSourceView(child)
                            break
                        }
                    }
                }
            }

            if (dropAction != null) {
                android.util.Log.d(TAG, "session=$activeDragSessionId FINALIZE execute drop action")
                runCatching { dropAction.invoke() }
                    .onFailure {
                        android.util.Log.e(
                            TAG,
                            "session=$activeDragSessionId FINALIZE drop action failed",
                            it
                        )
                    }
            } else {
                android.util.Log.d(TAG, "session=$activeDragSessionId FINALIZE no drop action")
            }
        } finally {
            draggingViewRef = null
            draggingPackageId = null
            dragInFlight = false
            dragEndScheduled = false
            pendingDropAction = null
            android.util.Log.d(TAG, "session=$activeDragSessionId FINALIZE cleanup complete")
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        android.util.Log.v(TAG, "session=$activeDragSessionId onViewRecycled view=${describeView(holder.itemView)}")
        holder.itemView.animate().cancel()
        pulseAnimators.remove(holder.itemView)?.cancel()
        holder.itemView.visibility = View.VISIBLE
        holder.itemView.alpha = 1f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
    }

    private fun dragActionName(action: Int): String = when (action) {
        DragEvent.ACTION_DRAG_STARTED -> "ACTION_DRAG_STARTED"
        DragEvent.ACTION_DRAG_LOCATION -> "ACTION_DRAG_LOCATION"
        DragEvent.ACTION_DROP -> "ACTION_DROP"
        DragEvent.ACTION_DRAG_ENDED -> "ACTION_DRAG_ENDED"
        DragEvent.ACTION_DRAG_ENTERED -> "ACTION_DRAG_ENTERED"
        DragEvent.ACTION_DRAG_EXITED -> "ACTION_DRAG_EXITED"
        else -> "ACTION_UNKNOWN_$action"
    }

    private fun describeView(view: View?): String {
        if (view == null) return "null"
        return "id=${view.id} hash=${System.identityHashCode(view)} vis=${view.visibility} alpha=${view.alpha} sx=${view.scaleX} sy=${view.scaleY}"
    }

    private fun describeApp(app: ArchivedApp?): String {
        if (app == null) return "null"
        return "pkg=${app.packageId},folder=${app.folderName}"
    }

    private fun describeTarget(item: ArchivedItem?): String = when (item) {
        is ArchivedItem.App -> "App(pkg=${item.app.packageId},folder=${item.app.folderName})"
        is ArchivedItem.Folder -> "Folder(name=${item.name},size=${item.apps.size})"
        null -> "null"
    }

    private fun iconDataFor(app: ArchivedApp): Any {
        return app.iconBytes ?: AppIconModel(app.packageId)
    }

    private fun toDisplayName(name: String?, packageId: String): String {
        val raw = name?.trim().orEmpty()
        if (raw.isNotBlank() && !isLikelyPackageId(raw)) return raw
        return humanizePackageId(packageId)
    }

    private fun isLikelyPackageId(value: String): Boolean {
        return value.contains('.') && value == value.lowercase()
    }

    private fun humanizePackageId(packageId: String): String {
        val segments = packageId.split('.').filter { it.isNotBlank() }
        var token = segments.lastOrNull().orEmpty()
        if (token.length <= 2 && segments.size > 1) {
            token = segments[segments.lastIndex - 1]
        }
        val cleaned = token.replace('_', ' ').replace('-', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .trim()
        if (cleaned.isBlank()) return packageId
        return cleaned.split(Regex("\\s+"))
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}
