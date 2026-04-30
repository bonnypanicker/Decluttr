package com.tool.decluttr.presentation.screens.reinstalled

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.screens.dashboard.DashboardViewModel
import com.tool.decluttr.presentation.util.AppIconModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReinstalledFragment : Fragment(R.layout.fragment_reinstalled) {

    private val viewModel: DashboardViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }

        val btnBack = view.findViewById<ImageButton>(R.id.btn_back)
        val recycler = view.findViewById<RecyclerView>(R.id.reinstalled_recycler_view)
        val empty = view.findViewById<TextView>(R.id.tv_reinstalled_empty)

        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            (v as RecyclerView).clipToPadding = false
            insets
        }

        btnBack.setOnClickListener { findNavController().navigateUp() }

        val adapter = ReinstalledAppsAdapter { app -> openPlayStore(app.packageId) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.archivedApps.collect { apps ->
                    val installedPackages = getInstalledPackages()
                    val reinstalled = apps
                        .filter { it.packageId in installedPackages }
                        .sortedBy { it.name.lowercase() }
                    adapter.submitList(reinstalled)
                    empty.visibility = if (reinstalled.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun getInstalledPackages(): Set<String> {
        return try {
            requireContext().packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun openPlayStore(packageId: String) {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageId")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageId")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(marketIntent)
        } catch (_: Exception) {
            startActivity(webIntent)
        }
    }

    private class ReinstalledDiff : DiffUtil.ItemCallback<ArchivedApp>() {
        override fun areItemsTheSame(oldItem: ArchivedApp, newItem: ArchivedApp): Boolean {
            return oldItem.packageId == newItem.packageId
        }

        override fun areContentsTheSame(oldItem: ArchivedApp, newItem: ArchivedApp): Boolean {
            return oldItem == newItem
        }
    }

    private class ReinstalledAppsAdapter(
        private val onOpenPlayStore: (ArchivedApp) -> Unit
    ) : ListAdapter<ArchivedApp, ReinstalledAppsAdapter.ReinstalledVH>(ReinstalledDiff()) {

        class ReinstalledVH(view: View) : RecyclerView.ViewHolder(view) {
            private val appIcon: ImageView = view.findViewById(R.id.app_icon)
            private val appName: TextView = view.findViewById(R.id.app_name)
            private val appMeta: TextView = view.findViewById(R.id.app_meta)

            fun bind(item: ArchivedApp, onOpenPlayStore: (ArchivedApp) -> Unit) {
                appName.text = item.name
                appMeta.text = "Tap to open in Play Store"
                appIcon.load(AppIconModel(item.packageId)) {
                    memoryCacheKey(item.packageId)
                    crossfade(false)
                }
                itemView.setOnClickListener { onOpenPlayStore(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReinstalledVH {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_archived_app_list, parent, false)
            return ReinstalledVH(view)
        }

        override fun onBindViewHolder(holder: ReinstalledVH, position: Int) {
            holder.bind(getItem(position), onOpenPlayStore)
        }
    }
}
