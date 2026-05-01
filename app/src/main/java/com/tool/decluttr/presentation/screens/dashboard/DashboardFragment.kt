package com.tool.decluttr.presentation.screens.dashboard

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tool.decluttr.R
import com.tool.decluttr.presentation.screens.billing.BillingViewModel
import com.tool.decluttr.presentation.screens.billing.PaywallBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by activityViewModels()
    private val billingViewModel: BillingViewModel by activityViewModels()
    private var selectedTabIndex = 0
    private var lastArchivedBytes: Long = 0L
    private var lastCredits: BillingViewModel.ArchiveCreditsUi? = null
    private val onboardingPrefs by lazy {
        requireContext().getSharedPreferences("decluttr_prefs", android.content.Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "DashboardFragment"
        private const val KEY_SELECTED_TAB = "selected_tab_index"
        private const val TAG_DISCOVER = "tab_discover"
        private const val TAG_ARCHIVE = "tab_archive"
    }

    override fun onResume() {
        super.onResume()
        syncTabContentWithBottomNav()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottom_nav)
        val contentContainer = view.findViewById<FrameLayout>(R.id.content_container)
        val appBar = view.findViewById<AppBarLayout>(R.id.app_bar)

        // Toolbar
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    openSettings()
                    true
                }
                R.id.action_get_pro -> {
                    val credits = lastCredits ?: billingViewModel.archiveCreditsUi.value
                    showPaywall(reason = "toolbar_get_pro", used = credits.used, limit = credits.limit)
                    true
                }
                else -> false
            }
        }
        toolbar.post { updateToolbarActions(toolbar) }

        // Apply our custom unique production-ready Navigation Bar styling natively
        bottomNav.isItemActiveIndicatorEnabled = false
        val colorStateList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.bottom_nav_item_selector)
        bottomNav.itemIconTintList = colorStateList
        bottomNav.itemTextColor = colorStateList
        bottomNav.setBackgroundColor(MaterialColors.getColor(bottomNav, com.google.android.material.R.attr.colorSurface))
        bottomNav.elevation = 8f

        val appBarStart = appBar.paddingStart
        val appBarTop = appBar.paddingTop
        val appBarEnd = appBar.paddingEnd
        val bottomNavStart = bottomNav.paddingStart
        val bottomNavBottom = bottomNav.paddingBottom
        val bottomNavEnd = bottomNav.paddingEnd
        val contentStart = contentContainer.paddingStart
        val contentEnd = contentContainer.paddingEnd

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(
                left = appBarStart + systemBars.left,
                top = appBarTop + systemBars.top,
                right = appBarEnd + systemBars.right
            )
            bottomNav.updatePadding(
                left = bottomNavStart + systemBars.left,
                right = bottomNavEnd + systemBars.right,
                bottom = bottomNavBottom + systemBars.bottom
            )
            contentContainer.updatePadding(
                left = contentStart + systemBars.left,
                right = contentEnd + systemBars.right,
                bottom = bottomNav.measuredHeight + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
        bottomNav.post {
            val systemBars = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = systemBars?.bottom ?: 0
            contentContainer.updatePadding(bottom = bottomNav.height + bottomInset)
        }

        // Bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_discover -> { switchTab(0); true }
                R.id.nav_archive -> { switchTab(1); true }
                else -> false
            }
        }

        // Fresh app open should always land on Discover.
        // Keep only in-memory/saved-instance restoration for transient recreation.
        selectedTabIndex = if (savedInstanceState == null) {
            0 // Fresh app entry should always default to Discover.
        } else {
            savedInstanceState.getInt(KEY_SELECTED_TAB, 0)
        }

        val targetNavItem = if (selectedTabIndex == 1) R.id.nav_archive else R.id.nav_discover
        if (bottomNav.selectedItemId != targetNavItem) {
            bottomNav.selectedItemId = targetNavItem
        } else {
            switchTab(selectedTabIndex)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.reviewEvent.collect { data ->
                        showBulkReviewDialog(data)
                    }
                }
                launch {
                    viewModel.paywallEvent.collect { request ->
                        showPaywall(
                            reason = request.reason,
                            used = request.quota?.used,
                            limit = request.quota?.limit
                        )
                    }
                }
                launch {
                    billingViewModel.entitlementState.collect {
                        updateToolbarActions(toolbar)
                    }
                }
                launch {
                    viewModel.archivedApps.collect { apps ->
                        val totalBytes = apps.sumOf { it.archivedSizeBytes ?: 0L }
                        lastArchivedBytes = totalBytes
                        updateToolbarActions(toolbar)
                    }
                }
                launch {
                    billingViewModel.archiveCreditsUi.collect { credits ->
                        lastCredits = credits
                        updateToolbarActions(toolbar)
                    }
                }
            }
        }
    }

    private fun switchTab(index: Int) {
        if (index !in 0..1) return
        selectedTabIndex = index

        val discover = childFragmentManager.findFragmentByTag(TAG_DISCOVER)
        val archive = childFragmentManager.findFragmentByTag(TAG_ARCHIVE)
        val tx = childFragmentManager.beginTransaction()
        discover?.let { tx.hide(it) }
        archive?.let { tx.hide(it) }

        val targetTag = if (index == 0) TAG_DISCOVER else TAG_ARCHIVE
        val existing = childFragmentManager.findFragmentByTag(targetTag)
        if (existing != null) {
            tx.show(existing)
        } else {
            val fragment = if (index == 0) DiscoveryFragment() else ArchiveFragment()
            tx.add(R.id.content_container, fragment, targetTag)
        }
        tx.commit()
        view?.findViewById<MaterialToolbar>(R.id.toolbar)?.let { toolbar -> updateToolbarActions(toolbar) }
    }

    private fun syncTabContentWithBottomNav() {
        val root = view ?: return
        val bottomNav = root.findViewById<BottomNavigationView>(R.id.bottom_nav) ?: return
        val expectedIndex = if (bottomNav.selectedItemId == R.id.nav_archive) 1 else 0
        val visibleIndex = when {
            childFragmentManager.findFragmentByTag(TAG_ARCHIVE)?.isVisible == true -> 1
            childFragmentManager.findFragmentByTag(TAG_DISCOVER)?.isVisible == true -> 0
            else -> -1
        }
        if (visibleIndex != expectedIndex) {
            switchTab(expectedIndex)
        } else {
            selectedTabIndex = expectedIndex
        }
    }

    private fun updateToolbarActions(toolbar: MaterialToolbar) {
        val isArchiveTab = selectedTabIndex == 1
        val credits = lastCredits ?: billingViewModel.archiveCreditsUi.value
        val isPremium = billingViewModel.entitlementState.value.isPremium

        val reclaimedItem = toolbar.menu.findItem(R.id.action_reclaimed)
        val reclaimedText = reclaimedItem?.actionView?.findViewById<TextView>(R.id.toolbar_reclaimed_action)
        if (reclaimedItem != null && reclaimedText != null) {
            val totalBytes = lastArchivedBytes.coerceAtLeast(0L)
            val mb = totalBytes / (1024.0 * 1024.0)
            val compact = if (mb < 10.0) {
                String.format(java.util.Locale.US, "~%.1fMB", mb)
            } else {
                String.format(java.util.Locale.US, "~%.0fMB", mb)
            }
            reclaimedText.text = "$compact freed"
            reclaimedItem.isVisible = isArchiveTab && totalBytes > 0L
            reclaimedText.visibility = if (reclaimedItem.isVisible) View.VISIBLE else View.GONE
        }

        val getProItem = toolbar.menu.findItem(R.id.action_get_pro)
        val getProText = getProItem?.actionView?.findViewById<TextView>(R.id.toolbar_get_pro_action)
        if (getProItem != null && getProText != null) {
            val canShow = isArchiveTab && !isPremium && credits.isVisible
            getProItem.isVisible = canShow
            if (canShow) {
                getProText.text = "Unlock Premium - ${credits.used}/${credits.limit}"
                getProText.visibility = View.VISIBLE
                getProItem.actionView?.setOnClickListener {
                    showPaywall(reason = "toolbar_get_pro", used = credits.used, limit = credits.limit)
                }
            } else {
                getProText.visibility = View.GONE
                getProItem.actionView?.setOnClickListener(null)
            }
        }
    }

    private fun openSettings() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.settingsFragment) {
            navController.navigate(R.id.settingsFragment)
        }
    }

    private fun showBulkReviewDialog(data: DashboardViewModel.ReviewData) {
        if (data.archivedApps.isEmpty()) return
        val activity = activity ?: return

        NativeBulkReviewDialog(
            context = activity,
            archivedApps = data.archivedApps,
            onComplete = { notesMap ->
                viewModel.saveReviewNotes(notesMap, data.celebration)
                // Switch to archive tab natively
                view?.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_archive
            },
            onCancel = {
                viewModel.saveReviewNotes(emptyMap(), data.celebration)
                view?.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_archive
            }
        ).show()
    }

    private fun showPaywall(
        reason: String,
        used: Int?,
        limit: Int?
    ) {
        val tag = "PaywallBottomSheet"
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        PaywallBottomSheet.newInstance(
            reason = reason,
            used = used,
            limit = limit
        ).show(childFragmentManager, tag)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTabIndex)
    }

}
