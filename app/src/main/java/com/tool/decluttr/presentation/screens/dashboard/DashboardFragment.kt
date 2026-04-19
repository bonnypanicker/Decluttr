package com.tool.decluttr.presentation.screens.dashboard

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
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
import com.tool.decluttr.presentation.screens.billing.PaywallBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by activityViewModels()
    private var selectedTabIndex = 0
    private val onboardingPrefs by lazy {
        requireContext().getSharedPreferences("decluttr_prefs", android.content.Context.MODE_PRIVATE)
    }

    companion object {
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottom_nav)
        val contentContainer = view.findViewById<FrameLayout>(R.id.content_container)
        val appBar = view.findViewById<AppBarLayout>(R.id.app_bar)

        // Toolbar
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                openSettings()
                true
            } else false
        }

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

        // Initial tab
        if (savedInstanceState == null) {
            switchTab(0)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.archivedApps.collect { apps ->
                    val pill = view.findViewById<android.widget.TextView>(R.id.toolbar_reclaimed_pill)
                    val totalBytes = apps.sumOf { it.archivedSizeBytes ?: 0L }
                    val mb = totalBytes / (1024.0 * 1024.0)
                    val formatted = if (mb < 1.0) String.format(java.util.Locale.US, "%.1f MB freed", mb)
                                    else String.format(java.util.Locale.US, "%.0f MB freed", mb)
                    pill.text = formatted

                    if (selectedTabIndex == 1 && totalBytes > 0) {
                        pill.visibility = View.VISIBLE
                    } else {
                        pill.visibility = View.GONE
                    }
                }
            }
        }

        // Observe review events for bulk review dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reviewEvent.collect { data ->
                    showBulkReviewDialog(data)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.paywallEvent.collect { request ->
                    showPaywall(
                        reason = request.reason,
                        used = request.quota?.used,
                        limit = request.quota?.limit
                    )
                }
            }
        }
    }

    private fun switchTab(index: Int) {
        selectedTabIndex = index
        
        val pill = view?.findViewById<android.widget.TextView>(R.id.toolbar_reclaimed_pill)
        val totalBytes = viewModel.archivedApps.value.sumOf { it.archivedSizeBytes ?: 0L }
        if (index == 1 && totalBytes > 0) {
            pill?.visibility = View.VISIBLE
        } else {
            pill?.visibility = View.GONE
        }

        val fragment = when (index) {
            0 -> DiscoveryFragment()
            1 -> ArchiveFragment()
            else -> return
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .commit()
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

}
