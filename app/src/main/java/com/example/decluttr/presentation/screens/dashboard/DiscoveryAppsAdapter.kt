package com.example.decluttr.presentation.screens.dashboard

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.decluttr.R
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.example.decluttr.presentation.util.AppIconModel
import java.util.Locale
import android.R as AndroidR
import com.google.android.material.R as MaterialR

data class AppListItem(
    val info: GetInstalledAppsUseCase.InstalledAppInfo,
    val isSelected: Boolean,
    val contextLabel: String?
)

class DiscoveryAppDiffCallback : DiffUtil.ItemCallback<AppListItem>() {
    override fun areItemsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean {
        return oldItem.info.packageId == newItem.info.packageId
    }

    override fun areContentsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean {
        return oldItem == newItem
    }
}

class DiscoveryAppsAdapter(
    private val onToggle: (String) -> Unit
) : ListAdapter<AppListItem, DiscoveryAppsAdapter.AppViewHolder>(DiscoveryAppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_discovery_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkBox: CheckBox = view.findViewById(R.id.app_checkbox)
        private val icon: ImageView = view.findViewById(R.id.app_icon)
        private val name: TextView = view.findViewById(R.id.app_name)
        private val warningIcon: ImageView = view.findViewById(R.id.warning_icon)
        private val details: TextView = view.findViewById(R.id.app_details)
        private val contextLabel: TextView = view.findViewById(R.id.app_context_label)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onToggle(getItem(adapterPosition).info.packageId)
                }
            }
        }

        fun bind(item: AppListItem) {
            val app = item.info
            name.text = app.name
            checkBox.isChecked = item.isSelected

            warningIcon.visibility = if (app.isPlayStoreInstalled) View.GONE else View.VISIBLE

            val sizeLabel = "${bytesToMB(app.apkSizeBytes)} MB"
            val now = System.currentTimeMillis()
            val timeString = if (app.lastTimeUsed > 0) {
                val daysAgo = ((now - app.lastTimeUsed) / DateUtils.DAY_IN_MILLIS).toInt()
                when {
                    daysAgo <= 0 -> "Today"
                    daysAgo == 1 -> "1 day ago"
                    else -> "$daysAgo days ago"
                }
            } else {
                "Never used"
            }
            details.text = "$sizeLabel • $timeString"

            if (item.contextLabel != null) {
                contextLabel.text = item.contextLabel
                contextLabel.visibility = View.VISIBLE
            } else {
                contextLabel.visibility = View.GONE
            }

            // Set background color based on selection
            val context = itemView.context
            val typedArray = context.obtainStyledAttributes(
                intArrayOf(
                    if (item.isSelected) MaterialR.attr.colorPrimaryContainer
                    else MaterialR.attr.colorSurface
                )
            )
            val backgroundColor = typedArray.getColor(0, 0)
            typedArray.recycle()
            itemView.setBackgroundColor(backgroundColor)
            
            // Re-apply standard ripple after background color overrides it
            val rippleTypedArray = context.obtainStyledAttributes(intArrayOf(AndroidR.attr.selectableItemBackground))
            val rippleDrawable = rippleTypedArray.getDrawable(0)
            rippleTypedArray.recycle()
            itemView.foreground = rippleDrawable


            icon.load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
                size(96)
            }
        }
    }

    private fun bytesToMB(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb < 1.0) {
            String.format(Locale.US, "%.1f", mb)
        } else {
            String.format(Locale.US, "%.0f", mb)
        }
    }
}
