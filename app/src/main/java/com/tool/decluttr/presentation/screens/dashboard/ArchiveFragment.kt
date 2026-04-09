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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.screens.billing.BillingViewModel
import com.tool.decluttr.presentation.screens.billing.PaywallBottomSheet
import com.tool.decluttr.presentation.util.AppIconModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.text.DecimalFormat

@AndroidEntryPoint
class ArchiveFragment : Fragment(R.layout.fragment_archive) {
    companion object {
        private const val TAG = "DecluttrDragDbg"
    }

    private val viewModel: DashboardViewModel by activityViewModels()
    private val billingViewModel: BillingViewModel by activityViewModels()

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
    private lateinit var btnPremium: ImageView
    private lateinit var creditsCard: View
    private lateinit var tvArchiveCredits: TextView
    private lateinit var progressArchiveCredits: LinearProgressIndicator
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
    
    private val prefs by lazy { requireContext().getSharedPreferences("archive_info_prefs", android.content.Context.MODE_PRIVATE) }
    private var isDragInfoDismissed = false
    private var isViewSwitchInfoDismissed = false
    
    private lateinit var infoCardsContainer: View
    private lateinit var cardDragInfo: View
    private lateinit var cardViewSwitchInfo: View
    private lateinit var btnDismissDragInfo: ImageView
    private lateinit var btnDismissViewSwitchInfo: ImageView
    private var sizeMap: Map<String, Long?> = emptyMap()
    private var reinstatedApps: List<ArchivedApp> = emptyList()
    private var installedPackagesCache: Set<String> = emptySet()
    private var installedPackagesCacheAt: Long = 0L
    private val installedPackagesCacheTtlMs: Long = 8_000L
    private var savedItemAnimator: RecyclerView.ItemAnimator? = null
    private var isDragInProgress: Boolean = false
    private var pendingAppsDuringDrag: List<ArchivedApp>? = null
    private val singletonCollapseInFlight = mutableSetOf<String>()
    private val pendingFolderCreations = mutableMapOf<String, Long>()
    private val pendingFolderCreationWindowMs = 3_000L

    private enum class ArchiveSortOption(val label: String) {
        UNINSTALLED_DATE("Uninstalled Date"),
        SIZE("Size"),
        CATEGORY("Category"),
        ALPHABETICAL("Alphabetical")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        billingViewModel.refreshBilling()
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
        btnPremium = v.findViewById(R.id.btn_premium)
        creditsCard = v.findViewById(R.id.archive_credits_card)
        tvArchiveCredits = v.findViewById(R.id.tv_archive_credits)
        progressArchiveCredits = v.findViewById(R.id.progress_archive_credits)
        recyclerView = v.findViewById(R.id.archive_recycler_view)
        emptyStateContainer = v.findViewById(R.id.empty_state_container)
        tvEmptyMessage = v.findViewById(R.id.tv_empty_message)
        btnFindApps = v.findViewById(R.id.btn_find_apps)
        reinstalledPageContainer = v.findViewById(R.id.reinstalled_page_container)
        btnReinstalledBack = v.findViewById(R.id.btn_reinstalled_back)
        reinstalledRecyclerView = v.findViewById(R.id.reinstalled_recycler_view)
        tvReinstalledEmpty = v.findViewById(R.id.tv_reinstalled_empty)

        infoCardsContainer = v.findViewById(R.id.info_cards_container)
        cardDragInfo = v.findViewById(R.id.card_drag_info)
        cardViewSwitchInfo = v.findViewById(R.id.card_view_switch_info)
        btnDismissDragInfo = v.findViewById(R.id.btn_dismiss_drag_info)
        btnDismissViewSwitchInfo = v.findViewById(R.id.btn_dismiss_view_switch_info)

        isDragInfoDismissed = prefs.getBoolean("drag_info_dismissed", false)
        isViewSwitchInfoDismissed = prefs.getBoolean("view_switch_info_dismissed", false)

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
            onFolderClick = { folderName, anchorView ->
                expandedFolder = folderName
                showFolderOverlay(folderName, anchorView)
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
        recyclerView.setHasFixedSize(false)

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
            try {
                android.util.Log.v(
                    TAG,
                    "RV_LISTENER action=${dragActionName(event.action)} local=${describeApp(event.localState as? ArchivedApp)} dragInProgress=$isDragInProgress pendingApps=${pendingAppsDuringDrag?.size ?: 0}"
                )
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        val ok = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                        android.util.Log.d(TAG, "RV DRAG_STARTED ok=$ok")
                        if (ok && !isDragInProgress) {
                            isDragInProgress = true
                            pendingAppsDuringDrag = null
                            savedItemAnimator = recyclerView.itemAnimator
                            recyclerView.itemAnimator = null
                            android.util.Log.d(TAG, "RV drag animator disabled")
                        } else if (ok) {
                            android.util.Log.v(TAG, "RV DRAG_STARTED ignored duplicate while drag already in progress")
                        }
                        ok
                    }
                    DragEvent.ACTION_DROP -> {
                        val draggedApp = event.localState as? ArchivedApp
                        val dropX = event.x
                        val dropY = event.y
                        android.util.Log.d(
                            TAG,
                            "RV DROP start dragged=${describeApp(draggedApp)} x=$dropX y=$dropY"
                        )
                        if (draggedApp != null) {
                            (rv as RecyclerView).post {
                                val currentApp = viewModel.archivedApps.value.firstOrNull {
                                    it.packageId == draggedApp.packageId
                                }
                                android.util.Log.d(
                                    TAG,
                                    "RV DROP post current=${describeApp(currentApp)} totalApps=${viewModel.archivedApps.value.size}"
                                )
                                if (currentApp?.folderName != null) {
                                    val childUnder = rv.findChildViewUnder(dropX, dropY)
                                    android.util.Log.d(
                                        TAG,
                                        "RV DROP hitTest childUnderNull=${childUnder == null} child=${describeView(childUnder)}"
                                    )
                                    if (childUnder == null) {
                                        try {
                                            viewModel.updateArchivedApp(currentApp.copy(folderName = null))
                                            android.util.Log.d(TAG, "RV DROP removed app from folder pkg=${currentApp.packageId}")
                                        } catch (t: Throwable) {
                                            android.util.Log.e(TAG, "RV DROP failed remove from folder", t)
                                        }
                                    } else {
                                        android.util.Log.d(TAG, "RV DROP landed on child; skip remove-from-folder")
                                    }
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        if (!isDragInProgress) {
                            android.util.Log.v(TAG, "RV DRAG_ENDED ignored because no drag is in progress")
                            return@setOnDragListener true
                        }
                        recyclerView.post {
                            recyclerView.itemAnimator = savedItemAnimator
                            savedItemAnimator = null
                            isDragInProgress = false
                            val latestApps = pendingAppsDuringDrag ?: viewModel.archivedApps.value
                            pendingAppsDuringDrag = null
                            android.util.Log.d(
                                TAG,
                                "RV DRAG_ENDED restore animator + render apps=${latestApps.size}"
                            )
                            renderArchivedApps(latestApps)
                        }
                        true
                    }
                    else -> true
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "RV_LISTENER exception action=${dragActionName(event.action)}", t)
                throw t
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
        btnPremium.setOnClickListener { showPaywall(reason = "archive_icon_tap") }

        btnDismissDragInfo.setOnClickListener {
            isDragInfoDismissed = true
            prefs.edit().putBoolean("drag_info_dismissed", true).apply()
            updateInfoCardsVisibility()
        }
        btnDismissViewSwitchInfo.setOnClickListener {
            isViewSwitchInfoDismissed = true
            prefs.edit().putBoolean("view_switch_info_dismissed", true).apply()
            updateInfoCardsVisibility()
        }

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
                        android.util.Log.v(
                            TAG,
                            "archivedApps.collect size=${apps.size} dragInProgress=$isDragInProgress pending=${pendingAppsDuringDrag?.size ?: 0}"
                        )
                        if (isDragInProgress) {
                            pendingAppsDuringDrag = apps
                            android.util.Log.v(TAG, "archivedApps.collect queued during drag size=${apps.size}")
                        } else {
                            renderArchivedApps(apps)
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
                launch {
                    billingViewModel.archiveCreditsUi.collect { credits ->
                        creditsCard.visibility = View.VISIBLE
                        tvArchiveCredits.text = credits.label
                        progressArchiveCredits.setProgressCompat(credits.progress, true)
                    }
                }
            }
        }
    }

    private fun showPaywall(reason: String) {
        val tag = "PaywallBottomSheet"
        val fm = parentFragmentManager
        if (fm.findFragmentByTag(tag) != null) return
        PaywallBottomSheet.newInstance(reason = reason).show(fm, tag)
    }

    private fun renderArchivedApps(apps: List<ArchivedApp>) {
        collapseSingletonFoldersIfNeeded(apps)
        android.util.Log.v(TAG, "renderArchivedApps size=${apps.size} expandedFolder=$expandedFolder")
        updateUI(apps)
        if (expandedFolder != null && folderOverlay != null) {
            val folderApps = apps.filter { it.folderName == expandedFolder }
            if (folderApps.isEmpty()) {
                android.util.Log.d(TAG, "renderArchivedApps folder became empty; dismiss overlay")
                expandedFolder = null
                folderOverlay?.dismiss {}
                folderOverlay = null
            } else {
                android.util.Log.v(TAG, "renderArchivedApps updateOverlay folder=$expandedFolder size=${folderApps.size}")
                folderOverlay?.updateApps(folderApps)
            }
        }
    }

    private fun updateInfoCardsVisibility() {
        val showDragCard = !isListMode && !isDragInfoDismissed
        val showSwitchCard = !isViewSwitchInfoDismissed
        
        cardDragInfo.visibility = if (showDragCard) View.VISIBLE else View.GONE
        cardViewSwitchInfo.visibility = if (showSwitchCard) View.VISIBLE else View.GONE
        infoCardsContainer.visibility = if (showDragCard || showSwitchCard) View.VISIBLE else View.GONE
    }

    private fun updateUI(apps: List<ArchivedApp>) {
        updateInfoCardsVisibility()
        android.util.Log.v(
            TAG,
            "updateUI input=${apps.size} query='$searchQuery' category='$selectedCategory' listMode=$isListMode"
        )
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

        if (isListMode) {
            sizeMap = filteredApps.associate { app ->
                app.packageId to (app.archivedSizeBytes ?: getInstalledApkSize(app.packageId))
            }
            val sortedApps = sortApps(filteredApps)
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
                android.util.Log.v(TAG, "updateUI submitList listMode count=${listItems.size}")
                adapter.submitList(listItems)
            }
            return
        }

        // Grid view intentionally keeps source order (no sort), so folder creation
        // does not reshuffle items.
        sizeMap = filteredApps.associate { app ->
            app.packageId to (app.archivedSizeBytes ?: getInstalledApkSize(app.packageId))
        }
        val folderAppsByName = filteredApps
            .filter { it.folderName != null }
            .groupBy { it.folderName!! }
        val emittedFolderNames = mutableSetOf<String>()
        val groupedItems = mutableListOf<ArchivedItem>()
        filteredApps.forEach { app ->
            val folderName = app.folderName
            if (folderName == null) {
                groupedItems += ArchivedItem.App(app)
                return@forEach
            }
            val folderApps = folderAppsByName[folderName].orEmpty()
            if (folderApps.size <= 1) {
                if (emittedFolderNames.add(folderName)) {
                    groupedItems += ArchivedItem.App(app.copy(folderName = null))
                }
                return@forEach
            }
            if (emittedFolderNames.add(folderName)) {
                groupedItems += ArchivedItem.Folder(folderName, folderApps)
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
            android.util.Log.v(TAG, "updateUI submitList gridMode count=${groupedItems.size}")
            adapter.submitList(groupedItems)
        }
    }

    private fun collapseSingletonFoldersIfNeeded(apps: List<ArchivedApp>) {
        val now = System.currentTimeMillis()
        pendingFolderCreations.entries.removeAll { it.value < now }

        val folderCounts = apps
            .mapNotNull { it.folderName }
            .groupingBy { it }
            .eachCount()

        folderCounts
            .filterValues { it >= 2 }
            .keys
            .forEach { pendingFolderCreations.remove(it) }

        val singletonApps = apps.filter { app ->
            val folder = app.folderName
            folder != null &&
                (folderCounts[folder] ?: 0) == 1 &&
                !pendingFolderCreations.containsKey(folder)
        }

        val singletonPkgIds = singletonApps.map { it.packageId }.toSet()
        singletonCollapseInFlight.removeAll { it !in singletonPkgIds }

        singletonApps.forEach { app ->
            if (singletonCollapseInFlight.add(app.packageId)) {
                android.util.Log.d(
                    TAG,
                    "collapseSingletonFoldersIfNeeded pkg=${app.packageId} folder=${app.folderName}"
                )
                viewModel.updateArchivedApp(app.copy(folderName = null))
            }
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
        android.util.Log.d(TAG, "handleAppDropOnApp dragged=${draggedApp.packageId} target=${targetApp.packageId}")
        if (draggedApp.packageId == targetApp.packageId) return
        val apps = viewModel.archivedApps.value
        val latestDragged = apps.firstOrNull { it.packageId == draggedApp.packageId } ?: return
        val latestTarget = apps.firstOrNull { it.packageId == targetApp.packageId } ?: return

        if (latestDragged.folderName != null && latestDragged.folderName == latestTarget.folderName) return

        val defaultName = nextDefaultFolderName(apps)
        pendingFolderCreations[defaultName] = System.currentTimeMillis() + pendingFolderCreationWindowMs
        try {
            viewModel.updateArchivedApp(latestDragged.copy(folderName = defaultName))
            viewModel.updateArchivedApp(latestTarget.copy(folderName = defaultName))
            android.util.Log.d(
                TAG,
                "handleAppDropOnApp created/assigned folder='$defaultName' dragged=${latestDragged.packageId} target=${latestTarget.packageId}"
            )
        } catch (t: Throwable) {
            pendingFolderCreations.remove(defaultName)
            android.util.Log.e(TAG, "handleAppDropOnApp failed assign folder $defaultName", t)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val ready = withTimeoutOrNull(1500L) {
                viewModel.archivedApps.first { list ->
                    list.count { it.folderName == defaultName } >= 2
                }
            }
            if (ready != null) {
                expandedFolder = defaultName
                runCatching { showFolderOverlay(defaultName, null) }
                    .onFailure { android.util.Log.e(TAG, "showFolderOverlay failed", it) }
            } else {
                android.util.Log.w(TAG, "handleAppDropOnApp timed out waiting for folder '$defaultName' to contain 2 apps")
            }
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

    private fun showFolderOverlay(folderName: String, anchorView: View?) {
        android.util.Log.d(TAG, "showFolderOverlay folder=$folderName anchor=${anchorView != null}")
        val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val overlay = FolderExpandOverlay(requireContext(), root)
        folderOverlay = overlay

        val apps = viewModel.archivedApps.value.filter { it.folderName == folderName }
        if (apps.isEmpty()) return

        overlay.show(
            folderName = folderName,
            folderApps = apps,
            anchorView = anchorView,
            onAppClick = { pkg ->
                expandedFolder = null
                openNativeAppDetails(pkg)
            },
            onFolderRenamed = { newName ->
                val normalized = newName.trim()
                if (normalized.isEmpty()) return@show
                val now = System.currentTimeMillis()
                pendingFolderCreations[folderName] = now + pendingFolderCreationWindowMs
                pendingFolderCreations[normalized] = now + pendingFolderCreationWindowMs
                val appsToRename = viewModel.archivedApps.value.filter { it.folderName == folderName }
                if (appsToRename.isEmpty()) return@show
                expandedFolder = normalized
                appsToRename.forEach { app ->
                    viewModel.updateArchivedApp(app.copy(folderName = normalized))
                }
            },
            onDragStartFromFolder = {
                android.util.Log.d(TAG, "showFolderOverlay onDragStartFromFolder folder=$folderName")
                expandedFolder = null
            },
            onDismiss = {
                android.util.Log.d(TAG, "showFolderOverlay onDismiss folder=$folderName expandedFolder=$expandedFolder")
                if (expandedFolder == folderName) expandedFolder = null
                folderOverlay = null
            }
        )
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

    private fun describeApp(app: ArchivedApp?): String {
        if (app == null) return "null"
        return "pkg=${app.packageId},folder=${app.folderName}"
    }

    private fun describeView(view: View?): String {
        if (view == null) return "null"
        return "id=${view.id} hash=${System.identityHashCode(view)} vis=${view.visibility}"
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
