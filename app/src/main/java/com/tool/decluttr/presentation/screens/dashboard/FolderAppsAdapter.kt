package com.tool.decluttr.presentation.screens.dashboard

import android.content.ClipData
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.util.AppIconModel

/**
 * Minimal adapter for the expanded-folder dialog grid.
 * Reuses item_archived_app.xml layout.
 */
class FolderAppsAdapter(
    private var apps: List<ArchivedApp>,
    private val onAppClick: (String) -> Unit,
    private val onDragStartFromFolder: (() -> Unit)? = null
) : RecyclerView.Adapter<FolderAppsAdapter.ViewHolder>() {

    private fun loadIcon(imageView: ImageView, app: ArchivedApp) {
        val bmp = IconBitmapCache.getOrDecode(app)
        if (bmp != null) {
            imageView.setImageBitmap(bmp)
        } else {
            imageView.load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                size(coil.size.Size.ORIGINAL)
                crossfade(false)
                placeholder(R.drawable.ic_launcher)
                error(R.drawable.ic_launcher)
            }
        }
    }

    companion object {
        private const val TAG = "DecluttrDragDbg"
    }

    fun updateData(newApps: List<ArchivedApp>) {
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = apps.size
            override fun getNewListSize() = newApps.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                apps[oldPos].packageId == newApps[newPos].packageId
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                apps[oldPos] == newApps[newPos]
        })
        apps = newApps
        diffResult.dispatchUpdatesTo(this)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archived_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.itemView.tag = app
        holder.name.text = toDisplayName(app.name, app.packageId)
        loadIcon(holder.icon, app)

        holder.itemView.setOnClickListener {
            onAppClick(app.packageId)
        }

        holder.itemView.setOnLongClickListener { view ->
            try {
                android.util.Log.d(
                    TAG,
                    "FOLDER longPress start pkg=${app.packageId} pos=${holder.bindingAdapterPosition} viewHash=${System.identityHashCode(view)}"
                )
                val clipData = ClipData.newPlainText("packageId", app.packageId)
                val shadowBuilder = ScaledDragShadowBuilder(holder.icon, 1.1f)
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    view.startDragAndDrop(clipData, shadowBuilder, app, 0)
                } else {
                    @Suppress("DEPRECATION")
                    view.startDrag(clipData, shadowBuilder, app, 0)
                }
                if (started) {
                    // Hide AFTER drag started so shadow builder already captured the view
                    view.visibility = View.INVISIBLE
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )

                    android.util.Log.d(TAG, "FOLDER drag started pkg=${app.packageId}; dismiss overlay")
                    // Use post to ensure the drag shadow is already attached before removing overlay
                    view.post { onDragStartFromFolder?.invoke() }
                } else {
                    android.util.Log.w(TAG, "FOLDER drag failed to start pkg=${app.packageId}")
                }
                started
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "FOLDER longPress exception pkg=${app.packageId}", t)
                throw t
            }
        }
    }

    override fun getItemCount() = apps.size

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
