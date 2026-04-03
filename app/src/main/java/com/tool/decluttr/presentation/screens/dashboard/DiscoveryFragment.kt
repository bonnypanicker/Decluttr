package com.tool.decluttr.presentation.screens.dashboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tool.decluttr.R
import com.tool.decluttr.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class DiscoveryFragment : Fragment(R.layout.fragment_discovery) {

    private val viewModel: DashboardViewModel by activityViewModels()
    private val disclosurePrefs by lazy {
        requireContext().getSharedPreferences("decluttr_prefs", android.content.Context.MODE_PRIVATE)
    }

    private var viewState = DiscoveryViewState.DASHBOARD
    private var isSearchActive = false
    private var searchQuery = ""
    private var selectedApps = emptySet<String>()
    
    private var sortOption = SortOption.NAME

    private lateinit var viewDashboard: View
    private lateinit var viewSpecificList: View
    private lateinit var progressLoading: View
    private lateinit var overlayUninstalling: View
    private lateinit var tvUninstallProgress: TextView

    private lateinit var dashRecyclerView: RecyclerView
    private lateinit var dashSearchBar: View
    private lateinit var dashSearchInput: EditText
    private lateinit var dashSearchClear: ImageView
    private lateinit var dashSelectionBar: View
    private lateinit var dashSelectionTitle: TextView
    private lateinit var dashBtnUninstall: Button
    private lateinit var dashBtnArchiveUninstall: Button
    
    private lateinit var specTitle: TextView
    private lateinit var specBtnBack: ImageView
    private lateinit var specRecyclerView: RecyclerView
    private lateinit var specSortChips: ChipGroup
    private lateinit var specSelectAllCheckbox: CheckBox
    private lateinit var specSelectAllLabel: TextView
    private lateinit var specSelectionInfo: TextView
    private lateinit var specActionContainer: View
    private lateinit var specBtnUninstallOnly: Button
    private lateinit var specBtnArchive: Button
    private lateinit var specEmptyState: View
    private lateinit var specSearchBar: View
    private lateinit var specSearchInput: EditText
    private lateinit var specSearchClear: ImageView

    private lateinit var dashboardAdapter: DiscoveryDashboardAdapter
    private lateinit var specificAdapter: DiscoveryAppsAdapter

    companion object {
        private const val KEY_USAGE_DISCLOSURE_ACCEPTED = "usage_disclosure_accepted"
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupAdapters()
        setupListeners()
        setupBackHandling()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkUsagePermission()
        viewModel.loadDiscoveryDataIfStale()
    }

    private fun initViews(v: View) {
        viewDashboard = v.findViewById(R.id.view_dashboard)
        viewSpecificList = v.findViewById(R.id.view_specific_list)
        progressLoading = v.findViewById(R.id.progress_loading)
        overlayUninstalling = v.findViewById(R.id.overlay_uninstalling)
        tvUninstallProgress = v.findViewById(R.id.tv_uninstall_progress)

        dashRecyclerView = viewDashboard.findViewById(R.id.recycler_view)
        dashSearchBar = viewDashboard.findViewById(R.id.search_bar)
        dashSearchInput = dashSearchBar.findViewById(R.id.search_edit_text)
        dashSearchClear = dashSearchBar.findViewById(R.id.clear_button)
        dashSelectionBar = viewDashboard.findViewById(R.id.selection_bar)
        dashSelectionTitle = dashSelectionBar.findViewById(R.id.selection_info_title)
        dashBtnUninstall = dashSelectionBar.findViewById(R.id.btn_uninstall_only)
        dashBtnArchiveUninstall = dashSelectionBar.findViewById(R.id.btn_archive_uninstall)

        specTitle = viewSpecificList.findViewById(R.id.tv_title)
        specBtnBack = viewSpecificList.findViewById(R.id.btn_back)
        specRecyclerView = viewSpecificList.findViewById(R.id.specific_recycler_view)
        specSortChips = viewSpecificList.findViewById(R.id.sort_chip_group)
        specSelectAllCheckbox = viewSpecificList.findViewById(R.id.select_all_checkbox)
        specSelectAllLabel = viewSpecificList.findViewById(R.id.select_all_label)
        specSelectionInfo = viewSpecificList.findViewById(R.id.selection_info_title)
        specActionContainer = viewSpecificList.findViewById(R.id.action_buttons_container)
        specBtnUninstallOnly = viewSpecificList.findViewById(R.id.btn_uninstall_only)
        specBtnArchive = viewSpecificList.findViewById(R.id.btn_archive_uninstall)
        specEmptyState = viewSpecificList.findViewById(R.id.empty_state_container)
        specSearchBar = viewSpecificList.findViewById(R.id.specific_search_bar)
        specSearchInput = specSearchBar.findViewById(R.id.search_edit_text)
        specSearchClear = specSearchBar.findViewById(R.id.clear_button)
    }

    private fun setupAdapters() {
        dashboardAdapter = DiscoveryDashboardAdapter(
            onNavigateToList = { setViewState(it) },
            onRequestPermission = {
                showUsageDisclosureIfNeeded()
            },
            onToggleApp = { toggleAppSelection(it) },
            onSearchToggle = { 
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
                updateUI()
            },
            onSearchQueryChange = { searchQuery = it; updateUI() }
        )
        dashRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dashRecyclerView.adapter = dashboardAdapter
        dashRecyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
        dashRecyclerView.setHasFixedSize(true)

        specificAdapter = DiscoveryAppsAdapter(
            onToggle = { toggleAppSelection(it) }
        )
        specRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        specRecyclerView.adapter = specificAdapter
        specRecyclerView.setHasFixedSize(true)
    }

    private fun showUsageDisclosureIfNeeded() {
        if (disclosurePrefs.getBoolean(KEY_USAGE_DISCLOSURE_ACCEPTED, false)) {
            openUsageAccessSettings()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.usage_disclosure_title)
            .setMessage(R.string.usage_disclosure_message)
            .setNegativeButton(R.string.usage_disclosure_cancel, null)
            .setPositiveButton(R.string.usage_disclosure_continue) { _, _ ->
                disclosurePrefs.edit().putBoolean(KEY_USAGE_DISCLOSURE_ACCEPTED, true).apply()
                openUsageAccessSettings()
            }
            .show()
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun setupListeners() {
        dashSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString()
                dashSearchClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                if (searchQuery != q) { searchQuery = q; updateUI() }
            }
        })
        dashSearchClear.setOnClickListener {
            if (dashSearchInput.text.isNotEmpty()) dashSearchInput.setText("")
            else { isSearchActive = false; searchQuery = ""; updateUI() }
        }

        dashBtnUninstall.setOnClickListener { executeBatchUninstallOnly() }
        dashBtnArchiveUninstall.setOnClickListener { executeBatchArchive() }

        specBtnBack.setOnClickListener { setViewState(DiscoveryViewState.DASHBOARD) }
        
        specSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString()
                specSearchClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                if (searchQuery != q) { searchQuery = q; updateUI() }
            }
        })
        specSearchClear.setOnClickListener { specSearchInput.setText("") }

        specSortChips.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            sortOption = when (checkedIds.first()) {
                R.id.chip_sort_size -> SortOption.SIZE
                R.id.chip_sort_last_used -> SortOption.LAST_USED
                else -> SortOption.NAME
            }
            updateUI()
        }

        specBtnUninstallOnly.setOnClickListener { executeBatchUninstallOnly() }
        specBtnArchive.setOnClickListener { executeBatchArchive() }
    }

    private fun setupBackHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            when {
                viewState != DiscoveryViewState.DASHBOARD -> setViewState(DiscoveryViewState.DASHBOARD)
                isSearchActive -> {
                    if (searchQuery.isNotEmpty()) dashSearchInput.setText("")
                    else { isSearchActive = false; searchQuery = ""; updateUI() }
                }
                else -> isEnabled = false 
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.allInstalledApps.collect { updateUI() } }
                launch { viewModel.unusedApps.collect { updateUI() } }
                launch { viewModel.largeApps.collect { updateUI() } }
                launch { viewModel.hasUsagePermission.collect { updateUI() } }
                launch {
                    viewModel.isLoadingDiscovery.collect { loading ->
                        progressLoading.visibility = if (loading || viewModel.isPreparingAllApps.value) View.VISIBLE else View.GONE
                        if (loading) { viewDashboard.visibility = View.GONE; viewSpecificList.visibility = View.GONE }
                        else updateUI()
                    }
                }
                launch {
                    viewModel.isPreparingAllApps.collect { prep ->
                        progressLoading.visibility = if (prep || viewModel.isLoadingDiscovery.value) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.uninstallProgress.collect { prog ->
                        if (prog.isUninstalling) {
                            overlayUninstalling.visibility = View.VISIBLE
                            tvUninstallProgress.text = "Uninstalling ${prog.current}/${prog.total}"
                        } else {
                            overlayUninstalling.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun setViewState(newState: DiscoveryViewState) {
        viewState = newState
        searchQuery = ""
        isSearchActive = false
        selectedApps = emptySet()
        dashSearchInput.setText("")
        specSearchInput.setText("")
        
        if (newState == DiscoveryViewState.ALL_APPS && viewModel.allInstalledApps.value.isEmpty()) {
            viewModel.prepareAllAppsForDisplay()
        }
        
        updateUI()
    }

    private fun toggleAppSelection(packageId: String) {
        selectedApps = if (packageId in selectedApps) selectedApps - packageId else selectedApps + packageId
        updateUI()
    }

    private fun updateUI() {
        if (viewModel.isLoadingDiscovery.value) return

        if (viewState == DiscoveryViewState.DASHBOARD) {
            viewDashboard.visibility = View.VISIBLE
            viewSpecificList.visibility = View.GONE
            updateDashboardUI()
        } else {
            viewDashboard.visibility = View.GONE
            viewSpecificList.visibility = View.VISIBLE
            updateSpecificListUI()
        }
    }

    private fun updateDashboardUI() {
        val allApps = viewModel.allInstalledApps.value
        val unusedApps = viewModel.unusedApps.value
        val largeApps = viewModel.largeApps.value
        val hasUsagePerm = viewModel.hasUsagePermission.value

        val filteredApps = if (searchQuery.isBlank()) allApps else allApps.filter { it.name.contains(searchQuery, true) }

        val items = mutableListOf<DashboardItem>()
        if (!isSearchActive) {
            if (allApps.isNotEmpty()) {
                val totalSize = allApps.sumOf { it.apkSizeBytes }
                val wasteSize = unusedApps.sumOf { it.apkSizeBytes }
                val percentage = if (totalSize > 0) ((wasteSize.toFloat() / totalSize.toFloat()) * 100).roundToInt() else 0
                items.add(DashboardItem.StorageMeter(wasteSize, totalSize, percentage))
            }
            if (!hasUsagePerm) items.add(DashboardItem.PermissionWarning())
            else items.add(DashboardItem.SmartCard(R.drawable.ic_archive_outlined, "Rarely Used Apps", "${unusedApps.size} apps • ${bytesToMB(unusedApps.sumOf { it.apkSizeBytes })} MB", DiscoveryViewState.RARELY_USED))
            items.add(DashboardItem.SmartCard(R.drawable.ic_storage_outlined, "Large Apps", "${largeApps.size} apps • ${bytesToMB(largeApps.sumOf { it.apkSizeBytes })} MB", DiscoveryViewState.LARGE_APPS))
        }

        items.add(DashboardItem.AllAppsHeader(isSearchActive))
        filteredApps.forEach { app -> items.add(DashboardItem.AppItem(app, app.packageId in selectedApps)) }

        dashboardAdapter.submitList(items)

        // Selection Bar
        if (selectedApps.isNotEmpty()) {
            val totalBytes = selectedApps.sumOf { pkg -> allApps.find { it.packageId == pkg }?.apkSizeBytes ?: 0L }
            dashSelectionTitle.text = "${selectedApps.size} selected • ${bytesToMB(totalBytes)} MB"
            if (dashSelectionBar.visibility == View.GONE) dashSelectionBar.visibility = View.VISIBLE
        } else {
            dashSelectionBar.visibility = View.GONE
        }

        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isSearchActive && dashSearchBar.visibility == View.GONE) {
            dashSearchBar.visibility = View.VISIBLE
            dashSearchInput.requestFocus()
            imm.showSoftInput(dashSearchInput, InputMethodManager.SHOW_IMPLICIT)
        } else if (!isSearchActive && dashSearchBar.visibility == View.VISIBLE) {
            dashSearchBar.visibility = View.GONE
            imm.hideSoftInputFromWindow(dashSearchInput.windowToken, 0)
        }
    }

    private fun updateSpecificListUI() {
        val allApps = viewModel.allInstalledApps.value
        val unusedApps = viewModel.unusedApps.value
        val largeApps = viewModel.largeApps.value

        val appList = when (viewState) {
            DiscoveryViewState.RARELY_USED -> unusedApps
            DiscoveryViewState.LARGE_APPS -> largeApps
            else -> allApps
        }

        specTitle.text = when (viewState) {
            DiscoveryViewState.RARELY_USED -> "Rarely Used Apps"
            DiscoveryViewState.LARGE_APPS -> "Large Apps (>100MB)"
            else -> "All Installed Apps"
        }

        val filteredList = if (searchQuery.isBlank()) appList else appList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val sortedList = when (sortOption) {
            SortOption.NAME -> filteredList.sortedBy { it.name.lowercase() }
            SortOption.SIZE -> filteredList.sortedByDescending { it.apkSizeBytes }
            SortOption.LAST_USED -> filteredList.sortedBy { it.lastTimeUsed }
        }

        specSelectAllCheckbox.setOnCheckedChangeListener(null)
        val allFilteredSelected = sortedList.isNotEmpty() && sortedList.all { it.packageId in selectedApps }
        specSelectAllCheckbox.isChecked = allFilteredSelected
        specSelectAllLabel.text = if (allFilteredSelected) "Deselect All" else "Select All"

        specSelectAllCheckbox.setOnCheckedChangeListener { _, checked ->
            selectedApps = if (checked) selectedApps + sortedList.map { it.packageId }
            else selectedApps - sortedList.map { it.packageId }.toSet()
            updateUI()
        }

        if (selectedApps.isNotEmpty()) {
            val selectedSize = selectedApps.sumOf { pkg -> appList.find { it.packageId == pkg }?.apkSizeBytes ?: 0L }
            specSelectionInfo.visibility = View.VISIBLE
            specSelectionInfo.text = "${selectedApps.size} of ${appList.size} • ${bytesToMB(selectedSize)} MB"
            specActionContainer.visibility = View.VISIBLE
        } else {
            specSelectionInfo.visibility = View.GONE
            specActionContainer.visibility = View.GONE
        }

        if (sortedList.isEmpty()) {
            specEmptyState.visibility = View.VISIBLE
            specRecyclerView.visibility = View.GONE
            specSelectAllCheckbox.isEnabled = false
        } else {
            specEmptyState.visibility = View.GONE
            specRecyclerView.visibility = View.VISIBLE
            specSelectAllCheckbox.isEnabled = true
            
            val mappedItems = sortedList.map { app ->
                AppListItem(app, app.packageId in selectedApps, null)
            }
            specificAdapter.submitList(mappedItems)
        }
    }

    private fun executeBatchUninstallOnly() {
        val ids = selectedApps.toSet()
        selectedApps = emptySet()
        setViewState(DiscoveryViewState.DASHBOARD)
        viewModel.uninstallSelectedOnly(ids)
    }

    private fun executeBatchArchive() {
        val ids = selectedApps.toSet()
        if (ids.isEmpty()) return

        val selectedAppsInfo = viewModel.allInstalledApps.value.filter { it.packageId in ids }
        val sideloadedIds = selectedAppsInfo
            .filter { !it.isPlayStoreInstalled }
            .map { it.packageId }
            .toSet()

        val continueArchiveFlow = {
            selectedApps = emptySet()
            setViewState(DiscoveryViewState.DASHBOARD)
            viewModel.archiveAndUninstallSelected(
                packageIds = ids,
                archiveEligiblePackageIds = ids - sideloadedIds
            )
        }

        if (sideloadedIds.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sideloaded Apps Detected")
                .setMessage(
                    "Some selected apps are sideloaded and cannot be archived. " +
                        "They will be uninstalled only. Continue?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ -> continueArchiveFlow() }
                .show()
        } else {
            continueArchiveFlow()
        }
    }

    private fun bytesToMB(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb < 1.0) String.format(java.util.Locale.US, "%.1f", mb)
        else String.format(java.util.Locale.US, "%.0f", mb)
    }
}
