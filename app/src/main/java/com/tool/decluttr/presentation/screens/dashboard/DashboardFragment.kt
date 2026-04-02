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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tool.decluttr.R
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
        private const val KEY_ONBOARDING_SHOWN = "onboarding_dashboard_shown"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottom_nav)
        val contentContainer = view.findViewById<FrameLayout>(R.id.content_container)

        // Toolbar
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                findNavController().navigate(R.id.action_dashboard_to_settings)
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
        bottomNav.post {
            contentContainer.setPadding(
                contentContainer.paddingLeft,
                contentContainer.paddingTop,
                contentContainer.paddingRight,
                bottomNav.height
            )
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
            showOnboardingIfNeeded()
        }

        // Observe review events for bulk review dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reviewEvent.collect { data ->
                    showBulkReviewDialog(data)
                }
            }
        }
    }

    private fun switchTab(index: Int) {
        selectedTabIndex = index
        val fragment = when (index) {
            0 -> DiscoveryFragment()
            1 -> ArchiveFragment()
            else -> return
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .commit()
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

    private fun showOnboardingIfNeeded() {
        if (onboardingPrefs.getBoolean(KEY_ONBOARDING_SHOWN, false)) return
        onboardingPrefs.edit().putBoolean(KEY_ONBOARDING_SHOWN, true).apply()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.onboarding_title)
            .setMessage(R.string.onboarding_message)
            .setPositiveButton(R.string.onboarding_cta, null)
            .show()
    }
}
