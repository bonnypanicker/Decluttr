package com.tool.decluttr.presentation.screens.dashboard

import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tool.decluttr.R
import com.tool.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.tool.decluttr.presentation.util.AppIconModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

enum class DiscoveryViewState {
    DASHBOARD,
    RARELY_USED,
    LARGE_APPS,
    ALL_APPS
}

enum class SortOption {
    NAME,
    SIZE,
    LAST_USED
}

sealed class DashboardItem {
    data class StorageMeter(
        val estimatedFreedBytes: Long,
        val totalSizeBytes: Long,
        val impactPercent: Int,
        val candidateAppsCount: Int,
        val usesUsageSignal: Boolean
    ) : DashboardItem()

    data class PermissionWarning(val dummy: Boolean = true) : DashboardItem()

    data class SmartCard(
        val iconRes: Int,
        val title: String,
        val description: String,
        val viewState: DiscoveryViewState
    ) : DashboardItem()

    data class AllAppsHeader(val isSearchActive: Boolean) : DashboardItem()

    data class SearchBar(val query: String) : DashboardItem()

    data class AppItem(
        val info: GetInstalledAppsUseCase.InstalledAppInfo,
        val isSelected: Boolean
    ) : DashboardItem()
}

class DashboardItemDiffCallback : DiffUtil.ItemCallback<DashboardItem>() {
    override fun areItemsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean {
        return when {
            oldItem is DashboardItem.StorageMeter && newItem is DashboardItem.StorageMeter -> true
            oldItem is DashboardItem.PermissionWarning && newItem is DashboardItem.PermissionWarning -> true
            oldItem is DashboardItem.SmartCard && newItem is DashboardItem.SmartCard ->
                oldItem.viewState == newItem.viewState
            oldItem is DashboardItem.AllAppsHeader && newItem is DashboardItem.AllAppsHeader -> true
            oldItem is DashboardItem.SearchBar && newItem is DashboardItem.SearchBar -> true
            oldItem is DashboardItem.AppItem && newItem is DashboardItem.AppItem ->
                oldItem.info.packageId == newItem.info.packageId
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean {
        return oldItem == newItem
    }
}

class DiscoveryDashboardAdapter(
    private val onNavigateToList: (DiscoveryViewState) -> Unit,
    private val onRequestPermission: () -> Unit,
    private val onToggleApp: (String) -> Unit,
    private val onSearchToggle: () -> Unit,
    private val onSearchQueryChange: (String) -> Unit
) : ListAdapter<DashboardItem, RecyclerView.ViewHolder>(DashboardItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_STORAGE_METER = 0
        private const val VIEW_TYPE_PERMISSION_WARNING = 1
        private const val VIEW_TYPE_SMART_CARD = 2
        private const val VIEW_TYPE_ALL_APPS_HEADER = 3
        private const val VIEW_TYPE_SEARCH_BAR = 4
        private const val VIEW_TYPE_APP_ITEM = 5
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DashboardItem.StorageMeter -> VIEW_TYPE_STORAGE_METER
            is DashboardItem.PermissionWarning -> VIEW_TYPE_PERMISSION_WARNING
            is DashboardItem.SmartCard -> VIEW_TYPE_SMART_CARD
            is DashboardItem.AllAppsHeader -> VIEW_TYPE_ALL_APPS_HEADER
            is DashboardItem.SearchBar -> VIEW_TYPE_SEARCH_BAR
            is DashboardItem.AppItem -> VIEW_TYPE_APP_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STORAGE_METER -> StorageMeterViewHolder(
                inflater.inflate(R.layout.item_storage_meter, parent, false)
            )
            VIEW_TYPE_PERMISSION_WARNING -> PermissionWarningViewHolder(
                inflater.inflate(R.layout.item_permission_warning, parent, false)
            )
            VIEW_TYPE_SMART_CARD -> SmartCardViewHolder(
                inflater.inflate(R.layout.item_smart_card, parent, false)
            )
            VIEW_TYPE_ALL_APPS_HEADER -> AllAppsHeaderViewHolder(
                inflater.inflate(R.layout.item_all_apps_header, parent, false)
            )
            VIEW_TYPE_SEARCH_BAR -> SearchBarViewHolder(
                inflater.inflate(R.layout.item_search_bar, parent, false)
            )
            VIEW_TYPE_APP_ITEM -> AppItemViewHolder(
                inflater.inflate(R.layout.item_discovery_app, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DashboardItem.StorageMeter -> (holder as StorageMeterViewHolder).bind(item)
            is DashboardItem.PermissionWarning -> (holder as PermissionWarningViewHolder).bind()
            is DashboardItem.SmartCard -> (holder as SmartCardViewHolder).bind(item)
            is DashboardItem.AllAppsHeader -> (holder as AllAppsHeaderViewHolder).bind(item)
            is DashboardItem.SearchBar -> (holder as SearchBarViewHolder).bind(item)
            is DashboardItem.AppItem -> (holder as AppItemViewHolder).bind(item)
        }
    }

    inner class StorageMeterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val storageSubtitle: TextView = view.findViewById(R.id.storage_subtitle)
        private val storageValue: TextView = view.findViewById(R.id.storage_value)
        private val wasteScore: TextView = view.findViewById(R.id.waste_score)
        private val progressBar: LinearProgressIndicator = view.findViewById(R.id.storage_progress)

        fun bind(item: DashboardItem.StorageMeter) {
            val context = itemView.context
            storageValue.text = "${bytesToMB(item.estimatedFreedBytes)} MB"
            wasteScore.text = context.getString(R.string.discovery_storage_score, item.impactPercent)
            storageSubtitle.text = if (item.candidateAppsCount > 0) {
                if (item.usesUsageSignal) {
                    context.getString(R.string.discovery_storage_subtitle_usage_and_size, item.candidateAppsCount)
                } else {
                    context.getString(R.string.discovery_storage_subtitle_size_only, item.candidateAppsCount)
                }
            } else {
                context.getString(R.string.discovery_storage_subtitle_empty)
            }
            progressBar.progress = item.impactPercent
        }
    }

    inner class PermissionWarningViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val button: MaterialButton = view.findViewById(R.id.grant_permission_button)

        init {
            button.setOnClickListener { onRequestPermission() }
        }

        fun bind() {
            // Nothing to bind
        }
    }

    inner class SmartCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.card_icon)
        private val title: TextView = view.findViewById(R.id.card_title)
        private val description: TextView = view.findViewById(R.id.card_description)
        private val cardButton: ImageView = view.findViewById(R.id.card_button)

        fun bind(item: DashboardItem.SmartCard) {
            icon.setImageResource(item.iconRes)
            title.text = item.title
            description.text = item.description

            itemView.setOnClickListener { onNavigateToList(item.viewState) }
            cardButton.setOnClickListener { onNavigateToList(item.viewState) }
        }
    }

    inner class AllAppsHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val searchButton: ImageView = view.findViewById(R.id.search_icon)

        init {
            searchButton.setOnClickListener { onSearchToggle() }
        }

        fun bind(item: DashboardItem.AllAppsHeader) {
            searchButton.setImageResource(
                if (item.isSearchActive) R.drawable.ic_close
                else R.drawable.ic_search
            )
        }
    }

    inner class SearchBarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val editText: EditText = view.findViewById(R.id.search_edit_text)
        private val clearButton: ImageView = view.findViewById(R.id.clear_button)
        private var textWatcher: TextWatcher? = null

        init {
            clearButton.setOnClickListener {
                editText.setText("")
            }
        }

        fun bind(item: DashboardItem.SearchBar) {
            // Remove old watcher
            textWatcher?.let { editText.removeTextChangedListener(it) }

            // Set text if different
            if (editText.text.toString() != item.query) {
                editText.setText(item.query)
            }

            // Show/hide clear button
            clearButton.visibility = if (item.query.isEmpty()) View.GONE else View.VISIBLE

            // Add new watcher
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString()
                    clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                    onSearchQueryChange(query)
                }
            }
            editText.addTextChangedListener(textWatcher)

            // Request focus when first shown
            if (item.query.isEmpty()) {
                editText.requestFocus()
            }
        }
    }

    inner class AppItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val checkBox: CheckBox = view.findViewById(R.id.app_checkbox)
        private val icon: ImageView = view.findViewById(R.id.app_icon)
        private val name: TextView = view.findViewById(R.id.app_name)
        private val warningIcon: ImageView = view.findViewById(R.id.warning_icon)
        private val details: TextView = view.findViewById(R.id.app_details)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(adapterPosition) as? DashboardItem.AppItem
                    item?.let { onToggleApp(it.info.packageId) }
                }
            }
        }

        fun bind(item: DashboardItem.AppItem) {
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
            } else "Never used"

            details.text = "$sizeLabel • $timeString"
            
            cardView.isChecked = item.isSelected

            // Load icon
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
