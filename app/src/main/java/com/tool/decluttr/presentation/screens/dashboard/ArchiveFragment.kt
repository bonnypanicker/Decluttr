package com.tool.decluttr.presentation.screens.dashboard

import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.PopupMenu
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.util.AppIconModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.DecimalFormat

@AndroidEntryPoint
class ArchiveFragment : Fragment(R.layout.fragment_archive) {

    private val viewModel: DashboardViewModel by activityViewModels()

    private var searchQuery = ""
    private var selectedCategory = "All"
    private var expandedFolder: String? = null
    private var folderOverlay: FolderExpandOverlay? = null

    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var searchClear: ImageView
    private lateinit var categoryBar: View
    private lateinit var chipContainerScrollView: View
    private lateinit var chipContainer: LinearLayout
    private lateinit var btnReinstalledApps: ImageView
    private lateinit var btnSort: ImageView
    private lateinit var btnViewSwitch: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateContainer: View
    private lateinit var tvEmptyMessage: TextView
    private lateinit var btnFindApps: Button
    private lateinit var reinstalledPageContainer: View
    private lateinit var btnReinstalledBack: ImageView
    private lateinit var reinstalledRecyclerView: RecyclerView
    private lateinit var tvReinstalledEmpty: TextView

    private lateinit var adapter: ArchivedAppsAdapter
    private lateinit var reinstalledAdapter: ReinstalledAppsAdapter
    private var isListMode = false
    private var isReinstalledPageVisible = false
    private var sortOption: ArchiveSortOption = ArchiveSortOption.UNINSTALLED_DATE
    private var sizeMap: Map<String, Long?> = emptyMap()
    private var reinstatedApps: List<ArchivedApp> = emptyList()
    private var installedPackagesCache: Set<String> = emptySet()
    private var installedPackagesCacheAt: Long = 0L
    private val installedPackagesCacheTtlMs: Long = 8_000L

    private enum class ArchiveSortOption(val label: String) {
        UNINSTALLED_DATE("Uninstalled Date"),
        SIZE("Size"),
        CATEGORY("Category"),
        ALPHABETICAL("Alphabetical")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun initViews(v: View) {
        searchBar = v.findViewById(R.id.archive_search_bar)
        searchInput = searchBar.findViewById(R.id.search_edit_text)
        searchClear = searchBar.findViewById(R.id.clear_button)
        categoryBar = v.findViewById(R.id.category_bar)
        chipContainerScrollView = v.findViewById(R.id.category_scroll_view)
        chipContainer = v.findViewById(R.id.chip_container)
        btnReinstalledApps = v.findViewById(R.id.btn_reinstalled_apps)
        btnSort = v.findViewById(R.id.btn_sort)
        btnViewSwitch = v.findViewById(R.id.btn_view_switch)
        recyclerView = v.findViewById(R.id.archive_recycler_view)
        emptyStateContainer = v.findViewById(R.id.empty_state_container)
        tvEmptyMessage = v.findViewById(R.id.tv_empty_message)
        btnFindApps = v.findViewById(R.id.btn_find_apps)
        reinstalledPageContainer = v.findViewById(R.id.reinstalled_page_container)
        btnReinstalledBack = v.findViewById(R.id.btn_reinstalled_back)
        reinstalledRecyclerView = v.findViewById(R.id.reinstalled_recycler_view)
        tvReinstalledEmpty = v.findViewById(R.id.tv_reinstalled_empty)

        searchInput.hint = "Search"
    }

    private fun setupRecyclerView() {
        adapter = ArchivedAppsAdapter(
            onAppClick = { pkg -> openNativeAppDetails(pkg) },
            onDeleteClick = { app -> viewModel.deleteArchivedApp(app) },
            onAppStartDrag = { },
            onAppDropOnApp = { draggedApp, targetApp -> handleAppDropOnApp(draggedApp, targetApp) },
            onAppDropOnFolder = { draggedApp, folderName ->
                viewModel.updateArchivedApp(draggedApp.copy(folderName = folderName))
            },
            onRemoveFolder = { folderApps ->
                folderApps.forEach { app -> viewModel.updateArchivedApp(app.copy(folderName = null)) }
            },
            onFolderClick = { folderName ->
                expandedFolder = folderName
                showFolderOverlay(folderName)
            },
            appMetaProvider = { app ->
                AppListMeta(
                    sizeLabel = formatSize(sizeMap[app.packageId]),
                    uninstallDateLabel = formatDate(app.archivedAt)
                )
            }
        )

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        val enterAnim = AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in).apply { duration = 200 }
        val controller = LayoutAnimationController(enterAnim, 0.05f)
        controller.order = LayoutAnimationController.ORDER_NORMAL
        recyclerView.layoutAnimation = controller

        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 250
            removeDuration = 200
            moveDuration = 300
            changeDuration = 200
        }

        reinstalledAdapter = ReinstalledAppsAdapter { app ->
            openPlayStore(app.packageId)
        }
        reinstalledRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        reinstalledRecyclerView.adapter = reinstalledAdapter
        reinstalledRecyclerView.setHasFixedSize(true)

        recyclerView.setOnDragListener { rv, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val ok = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                    android.util.Log.d("ArchiveFragment", "DRAG_STARTED ok=$ok")
                    ok
                }
                DragEvent.ACTION_DROP -> {
                    val draggedApp = event.localState as? ArchivedApp
                    android.util.Log.d("ArchiveFragment", "DROP rv hit test. dragged=${draggedApp?.packageId}")
                    if (draggedApp != null && draggedApp.folderName != null) {
                        (rv as RecyclerView).post {
                            val childUnder = rv.findChildViewUnder(event.x, event.y)
                            if (childUnder == null) {
                                try {
                                    viewModel.updateArchivedApp(draggedApp.copy(folderName = null))
                                } catch (t: Throwable) {
                                    android.util.Log.e("ArchiveFragment", "Failed to remove from folder on DROP", t)
                                }
                            } else {
                                android.util.Log.d("ArchiveFragment", "DROP landed on a child view; ignoring RV handler")
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Restoration is handled in adapter drag listeners.
                    true
                }
                else -> true
            }
        }
    }

    private fun setupListeners() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString()
                searchClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                if (searchQuery != q) {
                    searchQuery = q
                    updateUI(viewModel.archivedApps.value)
                }
            }
        })
        searchClear.setOnClickListener { searchInput.setText("") }

        btnViewSwitch.setOnClickListener {
            isListMode = !isListMode
            adapter.setListMode(isListMode)
            btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE
            recyclerView.layoutManager = if (isListMode) {
                LinearLayoutManager(requireContext())
            } else {
                GridLayoutManager(requireContext(), 4)
            }
            btnViewSwitch.setImageResource(
                if (isListMode) R.drawable.ic_grid_view else R.drawable.ic_list
            )
            updateUI(viewModel.archivedApps.value)
        }

        btnSort.setOnClickListener { showSortMenu() }
        btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE

        btnReinstalledApps.setOnClickListener {
            showReinstalledAppsMenu()
        }
        btnReinstalledBack.setOnClickListener {
            setReinstalledPageVisible(false)
        }

        btnFindApps.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_discover
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (isReinstalledPageVisible) {
                setReinstalledPageVisible(false)
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.archivedApps.collect { apps ->
                        updateUI(apps)

                        if (expandedFolder != null && folderOverlay != null) {
                            val folderApps = apps.filter { it.folderName == expandedFolder }
                            if (folderApps.isEmpty()) {
                                expandedFolder = null
                                folderOverlay?.dismiss {}
                                folderOverlay = null
                            } else {
                                folderOverlay?.updateApps(folderApps)
                            }
                        }
                    }
                }
                launch {
                    viewModel.undoDeleteEvent.collect { deletedApp ->
                        Snackbar.make(requireView(), getString(R.string.archive_undo_message, deletedApp.name), Snackbar.LENGTH_LONG)
                            .setAction(R.string.archive_undo_action) {
                                viewModel.restoreArchivedApp(deletedApp)
                            }
                            .show()
                    }
                }
            }
        }
    }

    private fun updateUI(apps: List<ArchivedApp>) {
        val installedPackages = getInstalledPackageIds()
        reinstatedApps = apps
            .filter { it.packageId in installedPackages }
            .sortedBy { it.name.lowercase() }
        val visibleArchiveApps = apps.filterNot { it.packageId in installedPackages }
        if (isReinstalledPageVisible) {
            reinstalledAdapter.submitArchivedApps(reinstatedApps)
            tvReinstalledEmpty.visibility = if (reinstatedApps.isEmpty()) View.VISIBLE else View.GONE
        }

        val categories = listOf("All") + visibleArchiveApps
            .mapNotNull { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        if (selectedCategory !in categories) {
            selectedCategory = "All"
        }

        categoryBar.visibility = if (categories.size > 1 || reinstatedApps.isNotEmpty()) View.VISIBLE else View.GONE
        btnReinstalledApps.visibility = if (reinstatedApps.isNotEmpty()) View.VISIBLE else View.GONE

        if (categories.size > 1) {
            chipContainerScrollView.visibility = View.VISIBLE
            chipContainer.removeAllViews()
            categories.forEach { category ->
                val chip = Chip(requireContext()).apply {
                    text = category
                    isCheckable = true
                    isChecked = selectedCategory == category
                    setOnClickListener {
                        selectedCategory = category
                        updateUI(viewModel.archivedApps.value)
                    }
                    val margin = (8 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = margin
                    }
                }
                chipContainer.addView(chip)
            }
        } else {
            chipContainerScrollView.visibility = View.GONE
        }

        val filteredApps = visibleArchiveApps.filter { app ->
            val matchesCategory = selectedCategory == "All" || app.category == selectedCategory
            val query = searchQuery.lowercase().trim()
            val matchesQuery = query.isEmpty() ||
                    app.name.lowercase().contains(query) ||
                    (app.category?.lowercase()?.contains(query) == true) ||
                    (app.notes?.lowercase()?.contains(query) == true) ||
                    app.tags.any { it.lowercase().contains(query) } ||
                    (app.folderName?.lowercase()?.contains(query) == true)
            matchesCategory && matchesQuery
        }

        sizeMap = filteredApps.associate { it.packageId to getInstalledApkSize(it.packageId) }
        val sortedApps = sortApps(filteredApps)

        if (isListMode) {
            val listItems = sortedApps.map { ArchivedItem.App(it) }
            if (listItems.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyStateContainer.visibility = View.VISIBLE
                if (visibleArchiveApps.isEmpty()) {
                    tvEmptyMessage.text = getString(R.string.archive_empty_message)
                    btnFindApps.visibility = View.VISIBLE
                } else {
                    tvEmptyMessage.text = getString(R.string.archive_empty_filtered)
                    btnFindApps.visibility = View.GONE
                }
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyStateContainer.visibility = View.GONE
                adapter.submitList(listItems)
            }
            return
        }

        val standalones = sortedApps.filter { it.folderName == null }.map { ArchivedItem.App(it) }
        val folders = sortedApps.filter { it.folderName != null }
            .groupBy { it.folderName!! }
            .map { (name, fapps) -> ArchivedItem.Folder(name, fapps) }
        val groupedItems = (standalones + folders).sortedBy {
            when (it) {
                is ArchivedItem.App -> it.app.name.lowercase()
                is ArchivedItem.Folder -> it.name.lowercase()
            }
        }

        if (groupedItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateContainer.visibility = View.VISIBLE
            if (visibleArchiveApps.isEmpty()) {
                tvEmptyMessage.text = getString(R.string.archive_empty_message)
                btnFindApps.visibility = View.VISIBLE
            } else {
                tvEmptyMessage.text = getString(R.string.archive_empty_filtered)
                btnFindApps.visibility = View.GONE
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.GONE
            adapter.submitList(groupedItems)
        }
    }

    private fun showReinstalledAppsMenu() {
        val popup = PopupMenu(requireContext(), btnReinstalledApps)
        MenuInflater(requireContext()).inflate(R.menu.archive_overflow_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_reinstalled_apps -> {
                    setReinstalledPageVisible(true)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setReinstalledPageVisible(visible: Boolean) {
        isReinstalledPageVisible = visible
        reinstalledPageContainer.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            reinstalledAdapter.submitArchivedApps(reinstatedApps)
            tvReinstalledEmpty.visibility = if (reinstatedApps.isEmpty()) View.VISIBLE else View.GONE
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

    private fun getInstalledPackageIds(): Set<String> {
        val now = System.currentTimeMillis()
        if (installedPackagesCache.isNotEmpty() && now - installedPackagesCacheAt < installedPackagesCacheTtlMs) {
            return installedPackagesCache
        }
        installedPackagesCache = try {
            requireContext().packageManager
                .getInstalledApplications(0)
                .map { it.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
        installedPackagesCacheAt = now
        return installedPackagesCache
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), btnSort)
        MenuInflater(requireContext()).inflate(R.menu.archive_sort_menu, popup.menu)
        val selectedItemId = when (sortOption) {
            ArchiveSortOption.UNINSTALLED_DATE -> R.id.sort_uninstalled_date
            ArchiveSortOption.SIZE -> R.id.sort_size
            ArchiveSortOption.CATEGORY -> R.id.sort_category
            ArchiveSortOption.ALPHABETICAL -> R.id.sort_alphabetic
        }
        popup.menu.findItem(selectedItemId)?.isChecked = true
        popup.setOnMenuItemClickListener { item ->
            sortOption = when (item.itemId) {
                R.id.sort_size -> ArchiveSortOption.SIZE
                R.id.sort_category -> ArchiveSortOption.CATEGORY
                R.id.sort_alphabetic -> ArchiveSortOption.ALPHABETICAL
                else -> ArchiveSortOption.UNINSTALLED_DATE
            }
            updateUI(viewModel.archivedApps.value)
            true
        }
        popup.show()
    }

    private fun sortApps(apps: List<ArchivedApp>): List<ArchivedApp> {
        return when (sortOption) {
            ArchiveSortOption.UNINSTALLED_DATE -> apps.sortedByDescending { it.archivedAt }
            ArchiveSortOption.SIZE -> apps.sortedWith(
                compareByDescending<ArchivedApp> { sizeMap[it.packageId] ?: -1L }
                    .thenBy { it.name.lowercase() }
            )
            ArchiveSortOption.CATEGORY -> apps.sortedWith(
                compareBy<ArchivedApp> { (it.category ?: "zzz").lowercase() }
                    .thenBy { it.name.lowercase() }
            )
            ArchiveSortOption.ALPHABETICAL -> apps.sortedBy { it.name.lowercase() }
        }
    }

    private fun getInstalledApkSize(packageId: String): Long? {
        return try {
            val appInfo = requireContext().packageManager.getApplicationInfo(packageId, 0)
            java.io.File(appInfo.sourceDir).length()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatSize(sizeBytes: Long?): String {
        if (sizeBytes == null || sizeBytes <= 0L) return "Size: N/A"
        val mb = sizeBytes / (1024.0 * 1024.0)
        return "Size: ${DecimalFormat("#,##0.#").format(mb)} MB"
    }

    private fun formatDate(timestamp: Long): String {
        return "Uninstalled: ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(java.util.Date(timestamp))}"
    }

    private fun handleAppDropOnApp(draggedApp: ArchivedApp, targetApp: ArchivedApp) {
        android.util.Log.d("ArchiveFragment", "handleAppDropOnApp dragged=${draggedApp.packageId} -> target=${targetApp.packageId}")
        if (draggedApp.packageId == targetApp.packageId) return
        val apps = viewModel.archivedApps.value
        val latestDragged = apps.firstOrNull { it.packageId == draggedApp.packageId } ?: return
        val latestTarget = apps.firstOrNull { it.packageId == targetApp.packageId } ?: return

        if (latestDragged.folderName != null && latestDragged.folderName == latestTarget.folderName) return

        val defaultName = nextDefaultFolderName(apps)
        try {
            viewModel.updateArchivedApp(latestDragged.copy(folderName = defaultName))
            viewModel.updateArchivedApp(latestTarget.copy(folderName = defaultName))
        } catch (t: Throwable) {
            android.util.Log.e("ArchiveFragment", "Failed to assign folder $defaultName", t)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            expandedFolder = defaultName
            runCatching { showFolderOverlay(defaultName) }
                .onFailure { android.util.Log.e("ArchiveFragment", "showFolderOverlay failed", it) }
        }
    }

    private fun nextDefaultFolderName(existingApps: List<ArchivedApp>): String {
        val usedNames = existingApps.mapNotNull { it.folderName }.toSet()
        val base = "New Folder"
        if (base !in usedNames) return base
        var index = 2
        while (true) {
            val candidate = "$base $index"
            if (candidate !in usedNames) return candidate
            index++
        }
    }

    private fun showFolderOverlay(folderName: String) {
        val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val overlay = FolderExpandOverlay(requireContext(), root)
        folderOverlay = overlay

        val apps = viewModel.archivedApps.value.filter { it.folderName == folderName }
        if (apps.isEmpty()) return

        overlay.show(
            folderName = folderName,
            folderApps = apps,
            anchorView = null,
            onAppClick = { pkg ->
                expandedFolder = null
                openNativeAppDetails(pkg)
            },
            onFolderRenamed = { newName ->
                viewModel.archivedApps.value.filter { it.folderName == folderName }.forEach { app ->
                    viewModel.updateArchivedApp(app.copy(folderName = newName))
                }
            },
            onDragStartFromFolder = {
                expandedFolder = null
            },
            onDismiss = {
                if (expandedFolder == folderName) expandedFolder = null
                folderOverlay = null
            }
        )
    }

    private fun openNativeAppDetails(packageId: String) {
        val app = viewModel.archivedApps.value.find { it.packageId == packageId } ?: return
        NativeAppDetailsDialog(
            context = requireContext(),
            app = app,
            onNotesUpdated = { notes ->
                viewModel.updateArchivedApp(app.copy(notes = notes))
            },
            onDelete = {
                viewModel.deleteArchivedApp(app)
            },
            onDismissRequest = {}
        ).show()
    }

    private data class ReinstalledItem(
        val packageId: String,
        val name: String
    )

    private class ReinstalledDiff : DiffUtil.ItemCallback<ReinstalledItem>() {
        override fun areItemsTheSame(oldItem: ReinstalledItem, newItem: ReinstalledItem): Boolean {
            return oldItem.packageId == newItem.packageId
        }

        override fun areContentsTheSame(oldItem: ReinstalledItem, newItem: ReinstalledItem): Boolean {
            return oldItem == newItem
        }
    }

    private inner class ReinstalledAppsAdapter(
        private val onOpenPlayStore: (ReinstalledItem) -> Unit
    ) : ListAdapter<ReinstalledItem, ReinstalledAppsAdapter.ReinstalledVH>(ReinstalledDiff()) {

        inner class ReinstalledVH(view: View) : RecyclerView.ViewHolder(view) {
            private val appIcon = view.findViewById<ImageView>(R.id.app_icon)
            private val appName = view.findViewById<TextView>(R.id.app_name)
            private val appMeta = view.findViewById<TextView>(R.id.app_meta)

            fun bind(item: ReinstalledItem) {
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
            val view = layoutInflater.inflate(R.layout.item_archived_app_list, parent, false)
            return ReinstalledVH(view)
        }

        override fun onBindViewHolder(holder: ReinstalledVH, position: Int) {
            holder.bind(getItem(position))
        }

        fun submitArchivedApps(apps: List<ArchivedApp>) {
            submitList(apps.map { ReinstalledItem(it.packageId, it.name) })
        }
    }
}
