package com.example.decluttr.presentation.screens.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.decluttr.R
import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.presentation.util.AppIconModel

/**
 * Minimal adapter for the expanded-folder dialog grid.
 * Reuses item_archived_app.xml layout.
 */
class FolderAppsAdapter(
    private val apps: List<ArchivedApp>,
    private val onAppClick: (String) -> Unit
) : RecyclerView.Adapter<FolderAppsAdapter.ViewHolder>() {

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
    }

    override fun getItemCount() = apps.size
}
