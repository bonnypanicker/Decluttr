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
    private val onDragStartFromFolder: (() -> Unit)? = null  // NEW: dismiss dialog on drag out
) : RecyclerView.Adapter<FolderAppsAdapter.ViewHolder>() {

    fun updateData(newApps: List<ArchivedApp>) {
        apps = newApps
        notifyDataSetChanged()
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
        holder.name.text = app.name
        holder.icon.load(AppIconModel(app.packageId)) {
            memoryCacheKey(app.packageId)
            crossfade(false)
        }
        holder.itemView.setOnClickListener { onAppClick(app.packageId) }
        
        // Add long-press drag support
        holder.itemView.setOnLongClickListener { view ->
            val clipData = ClipData.newPlainText("packageId", app.packageId)
            val shadowBuilder = ScaledDragShadowBuilder(view, 1.1f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.startDragAndDrop(clipData, shadowBuilder, app, 0)
            } else {
                @Suppress("DEPRECATION")
                view.startDrag(clipData, shadowBuilder, app, 0)
            }
            view.visibility = View.INVISIBLE
            
            // Haptic feedback
            view.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            
            // Dismiss the folder dialog — the drag continues on the main grid
            onDragStartFromFolder?.invoke()
            true
        }
    }

    override fun getItemCount() = apps.size
}
