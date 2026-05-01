package com.tool.decluttr.presentation.screens.dashboard

import android.content.ClipDescription
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.DragEvent
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.EntitlementState
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.screens.auth.AuthViewModel
import com.tool.decluttr.presentation.screens.billing.BillingViewModel
import com.tool.decluttr.presentation.screens.billing.PaywallBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.text.DecimalFormat

@AndroidEntryPoint
class ArchiveFragment : Fragment(R.layout.fragment_archive) {
    companion object {
        private const val TAG = "DecluttrDragDbg"
        private const val PREF_KEY_PREMIUM_NOTICE_SHOWN = "premium_notice_shown"
        private const val PREF_KEY_ARCHIVE_LIST_MODE = "archive_list_mode"
        private const val PREF_KEY_TIP_DRAG_SHOWN = "archive_tip_drag_shown"
        private const val PREF_KEY_TIP_VIEW_SHOWN = "archive_tip_view_shown"
        private const val PREF_KEY_TIP_LAST_SHOWN_AT = "archive_tip_last_shown_at"
        private const val TIP_MIN_GAP_MS = 8_000L
        private const val TIP_INITIAL_DELAY_MS = 700L
        private const val TIP_SECONDARY_DELAY_MS = 1_300L
    }

    private val viewModel: DashboardViewModel by activityViewModels()
    private val billingViewModel: BillingViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by viewModels()

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
    private lateinit var btnArchiveLogin: Button
    private lateinit var tipOverlayContainer: View
    private lateinit var tipOverlayIcon: ImageView
    private lateinit var tipOverlayTitle: TextView
    private lateinit var tipOverlayText: TextView
    private lateinit var btnTipOverlayClose: ImageView

    private lateinit var adapter: ArchivedAppsAdapter
    private var isListMode = false
    private var sortOption: ArchiveSortOption = ArchiveSortOption.UNINSTALLED_DATE
    
    private val prefs by lazy { requireContext().getSharedPreferences("archive_info_prefs", android.content.Context.MODE_PRIVATE) }
    private var activeTipKey: String? = null
    private var tipSessionStarted = false
    private var tipScheduleJob: Job? = null
    private var sizeMap: Map<String, Long?> = emptyMap()
    private var installedPackagesCache: Set<String> = emptySet()
    private var installedPackagesCacheAt: Long = 0L
    private val installedPackagesCacheTtlMs: Long = 8_000L
    private var savedItemAnimator: RecyclerView.ItemAnimator? = null
    private var isDragInProgress = false
    private var pendingAppsDuringDrag: List<ArchivedApp>? = null
    private var isGoogleSignInLoading = false
    private var snapToTopAfterNextSubmit = false
    private val singletonCollapseInFlight = mutableSetOf<String>()
    private val pendingFolderCreations = mutableMapOf<String, Long>()
    private val pendingFolderCreationWindowMs = 3_000L
    private var isViewModeTransitionPending = false
    private var savedAnimatorDuringViewModeSwitch: RecyclerView.ItemAnimator? = null
    /** In-memory guard: once true for this fragment instance, the notice will never re-show
     *  even if the entitlement flow re-emits (which it does on every tab switch). */
    private var premiumNoticeShownInMemory = false
    private val premiumNoticePrefs by lazy {
        requireContext().getSharedPreferences("billing_notice_prefs", android.content.Context.MODE_PRIVATE)
    }

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
        applyViewMode()
        snapToTopAfterNextSubmit = true
        setupListeners()
        observeViewModel()
        scheduleTipsIfNeeded()
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
        btnArchiveLogin = v.findViewById(R.id.btn_archive_login)
        tipOverlayContainer = v.findViewById(R.id.tip_overlay_container)
        tipOverlayIcon = v.findViewById(R.id.iv_tip_overlay_icon)
        tipOverlayTitle = v.findViewById(R.id.tv_tip_overlay_title)
        tipOverlayText = v.findViewById(R.id.tv_tip_overlay_text)
        btnTipOverlayClose = v.findViewById(R.id.btn_tip_overlay_close)
        isListMode = prefs.getBoolean(PREF_KEY_ARCHIVE_LIST_MODE, false)

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
            supportsChangeAnimations = false
        }

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
                            val latestApps = pendingAppsDuringDrag
                            pendingAppsDuringDrag = null
                            android.util.Log.d(
                                TAG,
                                "RV DRAG_ENDED restore animator pendingRender=${latestApps?.size ?: 0}"
                            )
                            if (latestApps != null) {
                                renderArchivedApps(latestApps)
                            }
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
        searchClear.setOnClickListener {
            searchInput.setText("")
        }

        btnViewSwitch.setOnClickListener {
            val wasAtTop = !recyclerView.canScrollVertically(-1)
            isListMode = !isListMode
            prefs.edit().putBoolean(PREF_KEY_ARCHIVE_LIST_MODE, isListMode).apply()
            beginViewModeTransition()
            recyclerView.layoutManager = if (isListMode) {
                LinearLayoutManager(requireContext())
            } else {
                GridLayoutManager(requireContext(), 4)
            }
            btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE
            btnViewSwitch.setImageResource(
                if (isListMode) R.drawable.ic_grid_view else R.drawable.ic_list
            )
            snapToTopAfterNextSubmit = wasAtTop
            updateUI(viewModel.archivedApps.value)
            scheduleTipsIfNeeded(forceImmediate = true)
        }

        btnSort.setOnClickListener { showSortMenu() }
        btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE
        btnTipOverlayClose.setOnClickListener { dismissActiveTip(markShown = true) }
        tipOverlayContainer.setOnClickListener {
            // Intentionally consume touches so underlying archive items are never tapped
            // while the tip overlay is visible.
        }

        btnReinstalledApps.setOnClickListener {
            showReinstalledAppsMenu()
        }

        btnFindApps.setOnClickListener {
            viewModel.startDiscoveryInRarelyUsed.value = true
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_discover
        }
        btnArchiveLogin.setOnClickListener { startGoogleSignIn() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoggedIn.collect {
                        updateUI(viewModel.archivedApps.value)
                    }
                }
                launch {
                    authViewModel.isLoading.collect { isLoading ->
                        updateUI(viewModel.archivedApps.value)
                    }
                }
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
                    billingViewModel.entitlementState.collect { entitlement ->
                        if (shouldShowPremiumNotice(entitlement)) {
                            Snackbar.make(
                                requireView(),
                                "Premium active. Unlimited archive unlocked.",
                                Snackbar.LENGTH_LONG
                            ).show()
                            markPremiumNoticeShown(entitlement)
                        }
                    }
                }
            }
        }
    }

    private fun shouldShowPremiumNotice(entitlement: EntitlementState): Boolean {
        if (!entitlement.isPremium) return false
        // In-memory guard: prevents re-showing within the same fragment lifecycle
        // even if the StateFlow re-emits on tab switch.
        if (premiumNoticeShownInMemory) return false
        // Persisted guard: prevents re-showing across app restarts.
        return !premiumNoticePrefs.getBoolean(PREF_KEY_PREMIUM_NOTICE_SHOWN, false)
    }

    private fun markPremiumNoticeShown(entitlement: EntitlementState) {
        premiumNoticeShownInMemory = true
        // Use commit() for synchronous write to prevent any race with subsequent flow emissions.
        premiumNoticePrefs.edit()
            .putBoolean(PREF_KEY_PREMIUM_NOTICE_SHOWN, true)
            .commit()
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

    private fun applyViewMode() {
        recyclerView.layoutManager = if (isListMode) {
            LinearLayoutManager(requireContext())
        } else {
            GridLayoutManager(requireContext(), 4)
        }
        btnSort.visibility = if (isListMode) View.VISIBLE else View.GONE
        btnViewSwitch.setImageResource(if (isListMode) R.drawable.ic_grid_view else R.drawable.ic_list)
    }

    private fun beginViewModeTransition() {
        if (isViewModeTransitionPending) return
        isViewModeTransitionPending = true
        recyclerView.suppressLayout(true)
        savedAnimatorDuringViewModeSwitch = recyclerView.itemAnimator
        recyclerView.itemAnimator = null
    }

    private fun completeViewModeTransitionIfNeeded() {
        if (!isViewModeTransitionPending) return
        isViewModeTransitionPending = false
        recyclerView.suppressLayout(false)
        recyclerView.itemAnimator = savedAnimatorDuringViewModeSwitch
        savedAnimatorDuringViewModeSwitch = null
    }

    private fun submitArchiveItems(items: List<ArchivedItem>) {
        adapter.submitList(items) {
            completeViewModeTransitionIfNeeded()
            if (snapToTopAfterNextSubmit) {
                snapToTopAfterNextSubmit = false
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
            }
        }
    }

    private fun scheduleTipsIfNeeded(forceImmediate: Boolean = false) {
        if (!isAdded) return
        if (activeTipKey != null) return
        val dragShown = prefs.getBoolean(PREF_KEY_TIP_DRAG_SHOWN, false)
        val viewShown = prefs.getBoolean(PREF_KEY_TIP_VIEW_SHOWN, false)
        val nextTip = when {
            !dragShown && !isListMode -> PREF_KEY_TIP_DRAG_SHOWN to "Tip: Long press and drag one app onto another to create a folder."
            !viewShown -> PREF_KEY_TIP_VIEW_SHOWN to "Tip: Switch to list view to sort by size, date, category, or name."
            else -> null
        } ?: return

        val now = System.currentTimeMillis()
        val lastShownAt = prefs.getLong(PREF_KEY_TIP_LAST_SHOWN_AT, 0L)
        val minGapRemaining = (lastShownAt + TIP_MIN_GAP_MS - now).coerceAtLeast(0L)
        val baseDelay = if (!tipSessionStarted) TIP_INITIAL_DELAY_MS else TIP_SECONDARY_DELAY_MS
        tipSessionStarted = true
        val delayMs = if (forceImmediate) 150L else maxOf(baseDelay, minGapRemaining)

        tipScheduleJob?.cancel()
        tipScheduleJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(delayMs)
            if (!isAdded || view == null || activeTipKey != null) return@launch
            showTip(nextTip.first, nextTip.second)
        }
    }

    private fun showTip(tipKey: String, message: String) {
        activeTipKey = tipKey
        when (tipKey) {
            PREF_KEY_TIP_DRAG_SHOWN -> {
                tipOverlayTitle.text = "Create folders faster"
                tipOverlayIcon.setImageResource(R.drawable.ic_archive_outlined)
            }
            PREF_KEY_TIP_VIEW_SHOWN -> {
                tipOverlayTitle.text = "Sort smarter"
                tipOverlayIcon.setImageResource(R.drawable.ic_sort)
            }
            else -> {
                tipOverlayTitle.text = "Quick tip"
                tipOverlayIcon.setImageResource(R.drawable.ic_broom)
            }
        }
        tipOverlayText.text = message
        tipOverlayContainer.alpha = 0f
        tipOverlayContainer.visibility = View.VISIBLE
        tipOverlayContainer.animate().alpha(1f).setDuration(180L).start()
        prefs.edit()
            .putLong(PREF_KEY_TIP_LAST_SHOWN_AT, System.currentTimeMillis())
            .putBoolean(tipKey, true)
            .apply()
        android.util.Log.v("ArchiveTips", "show key=$tipKey message=$message")
    }

    private fun dismissActiveTip(markShown: Boolean) {
        val key = activeTipKey ?: return
        activeTipKey = null
        tipOverlayContainer.animate().alpha(0f).setDuration(140L).withEndAction {
            tipOverlayContainer.visibility = View.GONE
            tipOverlayContainer.alpha = 1f
        }.start()
        if (markShown) {
            prefs.edit().putBoolean(key, true).apply()
        }
        android.util.Log.v("ArchiveTips", "dismiss key=$key marked=$markShown")
        scheduleTipsIfNeeded()
    }

    override fun onDestroyView() {
        completeViewModeTransitionIfNeeded()
        tipScheduleJob?.cancel()
        tipScheduleJob = null
        activeTipKey = null
        super.onDestroyView()
    }

    private fun updateUI(apps: List<ArchivedApp>) {
        android.util.Log.v(
            TAG,
            "updateUI input=${apps.size} query='$searchQuery' category='$selectedCategory' listMode=$isListMode"
        )
        val installedPackages = getInstalledPackageIds()
        val visibleArchiveApps = apps.filterNot { it.packageId in installedPackages }

        val categories = listOf("All") + visibleArchiveApps
            .mapNotNull { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        if (selectedCategory !in categories) {
            selectedCategory = "All"
        }

        categoryBar.visibility = View.VISIBLE
        btnReinstalledApps.visibility = View.VISIBLE

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
            val listItems = sortedApps.map { ArchivedItem.App(it, inListMode = true) }
            if (listItems.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyStateContainer.visibility = View.VISIBLE
                completeViewModeTransitionIfNeeded()
                if (visibleArchiveApps.isEmpty()) {
                    val loggedIn = viewModel.isLoggedIn.value
                    val isAuthLoading = isGoogleSignInLoading || authViewModel.isLoading.value

                    if (loggedIn == null || isAuthLoading) {
                        tvEmptyMessage.text = ""
                        btnFindApps.visibility = View.GONE
                        btnArchiveLogin.visibility = View.GONE
                    } else if (!loggedIn) {
                        tvEmptyMessage.text = getString(R.string.archive_empty_message_logged_out)
                        btnFindApps.visibility = View.GONE
                        btnArchiveLogin.visibility = View.VISIBLE
                    } else {
                        tvEmptyMessage.text = getString(R.string.archive_empty_message)
                        btnFindApps.visibility = View.VISIBLE
                        btnArchiveLogin.visibility = View.GONE
                    }
                } else {
                    tvEmptyMessage.text = getString(R.string.archive_empty_filtered)
                    btnFindApps.visibility = View.GONE
                    btnArchiveLogin.visibility = View.GONE
                }
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyStateContainer.visibility = View.GONE
                android.util.Log.v(TAG, "updateUI submitList listMode count=${listItems.size}")
                submitArchiveItems(listItems)
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
                groupedItems += ArchivedItem.App(app, inListMode = false)
                return@forEach
            }
            val folderApps = folderAppsByName[folderName].orEmpty()
            if (folderApps.size <= 1) {
                if (emittedFolderNames.add(folderName)) {
                    groupedItems += ArchivedItem.App(
                        app.copy(folderName = null),
                        inListMode = false
                    )
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
                completeViewModeTransitionIfNeeded()
                if (visibleArchiveApps.isEmpty()) {
                    val loggedIn = viewModel.isLoggedIn.value
                    val isAuthLoading = isGoogleSignInLoading || authViewModel.isLoading.value

                    if (loggedIn == null || isAuthLoading) {
                        tvEmptyMessage.text = ""
                        btnFindApps.visibility = View.GONE
                        btnArchiveLogin.visibility = View.GONE
                    } else if (!loggedIn) {
                        tvEmptyMessage.text = getString(R.string.archive_empty_message_logged_out)
                        btnFindApps.visibility = View.GONE
                        btnArchiveLogin.visibility = View.VISIBLE
                    } else {
                        tvEmptyMessage.text = getString(R.string.archive_empty_message)
                        btnFindApps.visibility = View.VISIBLE
                        btnArchiveLogin.visibility = View.GONE
                    }
                } else {
                    tvEmptyMessage.text = getString(R.string.archive_empty_filtered)
                    btnFindApps.visibility = View.GONE
                    btnArchiveLogin.visibility = View.GONE
                }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.GONE
            // Pre-warm icon cache so folder previews can synchronously bind any iconBytes-backed icons.
            filteredApps
                .filter { it.folderName != null && it.iconBytes != null }
                .forEach { IconBitmapCache.getOrDecode(it) }
            android.util.Log.v(TAG, "updateUI submitList gridMode count=${groupedItems.size}")
            submitArchiveItems(groupedItems)
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
                !pendingFolderCreations.containsKey(folder) &&
                isDefaultFolderName(folder)
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
                    findNavController().navigate(R.id.action_archive_to_reinstalled)
                    true
                }
                R.id.menu_wishlist -> {
                    findNavController().navigate(R.id.action_archive_to_wishlist)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun isDefaultFolderName(name: String): Boolean {
        if (name == "New Folder") return true
        return name.matches(Regex("New Folder \\d+"))
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
        val primaryColor = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorPrimary,
            0
        )
        val selectedItemId = when (sortOption) {
            ArchiveSortOption.UNINSTALLED_DATE -> R.id.sort_uninstalled_date
            ArchiveSortOption.SIZE -> R.id.sort_size
            ArchiveSortOption.CATEGORY -> R.id.sort_category
            ArchiveSortOption.ALPHABETICAL -> R.id.sort_alphabetic
        }
        for (i in 0 until popup.menu.size()) {
            val menuItem = popup.menu.getItem(i)
            val title = menuItem.title?.toString().orEmpty()
            menuItem.isCheckable = false
            menuItem.title = if (menuItem.itemId == selectedItemId) {
                SpannableString(title).apply {
                    setSpan(ForegroundColorSpan(primaryColor), 0, length, 0)
                }
            } else {
                title
            }
        }
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
        val latestDragged = apps.firstOrNull { it.packageId == draggedApp.packageId }
        val latestTarget = apps.firstOrNull { it.packageId == targetApp.packageId }

        if (latestDragged == null || latestTarget == null) {
            // App was deleted/modified during drag
            com.google.android.material.snackbar.Snackbar.make(requireView(), "App was modified during drag. Try again.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            return
        }

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
                // First-create edge case: DB state can update before RecyclerView has laid out
                // the new folder tile. Wait briefly so overlay can anchor to the real folder view.
                val folderAnchor = awaitFolderAnchorView(defaultName)
                runCatching { showFolderOverlay(defaultName, folderAnchor) }
                    .onFailure { android.util.Log.e(TAG, "showFolderOverlay failed", it) }
            } else {
                android.util.Log.w(TAG, "handleAppDropOnApp timed out waiting for folder '$defaultName' to contain 2 apps")
            }
        }
    }

    /**
     * Scans the RecyclerView to find the folder item view for the given folder name.
     * Returns null if the folder hasn't been laid out yet (animation will use center fallback).
     */
    private fun findFolderViewByName(folderName: String): View? {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val item = child?.tag
            if (item is ArchivedItem.Folder && item.name == folderName) {
                return child
            }
        }
        return null
    }

    private suspend fun awaitFolderAnchorView(
        folderName: String,
        timeoutMs: Long = 450L
    ): View? {
        val start = System.currentTimeMillis()
        while (isAdded && System.currentTimeMillis() - start < timeoutMs) {
            val anchor = findFolderViewByName(folderName)
            if (
                anchor != null &&
                anchor.isAttachedToWindow &&
                anchor.isLaidOut &&
                anchor.width > 0 &&
                anchor.height > 0
            ) {
                return anchor
            }
            delay(16L)
        }
        return findFolderViewByName(folderName)
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

    private fun startGoogleSignIn() {
        val credentialManager = androidx.credentials.CredentialManager.create(requireContext())
        val serverClientId = runCatching { getString(R.string.default_web_client_id) }.getOrNull()
        if (serverClientId.isNullOrBlank()) {
            android.widget.Toast.makeText(requireContext(), "Google Sign-In is not configured for this build.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            isGoogleSignInLoading = true
            updateUI(viewModel.archivedApps.value)
            try {
                val rawNonce = java.util.UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setNonce(hashedNonce)
                    .build()

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = requireActivity(),
                    request = request
                )

                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    authViewModel.authenticateWithGoogleIdToken(googleCredential.idToken, rawNonce)
                } else {
                    android.widget.Toast.makeText(requireContext(), "Unable to read Google credential.", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    android.widget.Toast.makeText(requireContext(), "Google Sign-In failed.", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                isGoogleSignInLoading = false
                updateUI(viewModel.archivedApps.value)
            }
        }
    }
}
