package com.tool.decluttr.presentation.screens.wishlist

import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.WishlistSortOption
import com.tool.decluttr.presentation.screens.billing.BillingViewModel
import com.tool.decluttr.presentation.screens.billing.PaywallBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WishlistFragment : Fragment(R.layout.fragment_wishlist) {
    private val viewModel: WishlistViewModel by viewModels()
    private val billingViewModel: BillingViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        val btnBack = view.findViewById<ImageButton>(R.id.btn_back)
        val btnSort = view.findViewById<ImageButton>(R.id.btn_sort)
        val chipGroupCategories = view.findViewById<ChipGroup>(R.id.chip_group_categories)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_wishlist)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty)

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            (v as RecyclerView).clipToPadding = false
            insets
        }

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnSort.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            val primaryColor = MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorPrimary,
                0
            )
            popup.menu.apply {
                val selectedId = when (viewModel.sortOption.value) {
                    WishlistSortOption.DATE_ADDED -> 0
                    WishlistSortOption.ALPHABETICAL -> 1
                    WishlistSortOption.CATEGORY -> 2
                }
                fun title(text: String, isSelected: Boolean): CharSequence {
                    return if (!isSelected) text else SpannableString(text).apply {
                        setSpan(ForegroundColorSpan(primaryColor), 0, length, 0)
                    }
                }
                add(0, 0, 0, title("Date Added", selectedId == 0)).isCheckable = false
                add(0, 1, 1, title("Alphabetical", selectedId == 1)).isCheckable = false
                add(0, 2, 2, title("Category", selectedId == 2)).isCheckable = false
            }
            popup.setOnMenuItemClickListener { item ->
                viewModel.setSortOption(
                    when (item.itemId) {
                        1 -> WishlistSortOption.ALPHABETICAL
                        2 -> WishlistSortOption.CATEGORY
                        else -> WishlistSortOption.DATE_ADDED
                    }
                )
                true
            }
            popup.show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { categories ->
                chipGroupCategories.removeAllViews()
                categories.forEach { cat ->
                    val chip = Chip(requireContext()).apply {
                        text = cat
                        isCheckable = true
                        isChecked = cat == viewModel.selectedCategory.value
                        setOnCheckedChangeListener { _, checked ->
                            if (checked) viewModel.setCategory(cat)
                        }
                    }
                    chipGroupCategories.addView(chip)
                }
            }
        }

        val adapter = WishlistAdapter(
            onDeleteClick = { app ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Remove from Wishlist?")
                    .setMessage("Are you sure you want to remove ${app.name} from your wishlist?")
                    .setPositiveButton("Remove") { _, _ ->
                        viewModel.remove(app.packageId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onPlayStoreClick = { app ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(app.playStoreUrl)
                    setPackage("com.android.vending")
                }
                runCatching {
                    startActivity(intent)
                }.onFailure {
                    // Fallback to browser
                    val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(app.playStoreUrl))
                    startActivity(webIntent)
                }
            },
            onNotesClick = { app ->
                val tag = "WishlistNotesSheet"
                if (parentFragmentManager.findFragmentByTag(tag) == null) {
                    WishlistNotesBottomSheet.newInstance(
                        packageId = app.packageId,
                        appName = app.name,
                        iconUrl = app.iconUrl,
                        notes = app.notes
                    ).show(parentFragmentManager, tag)
                }
            },
            onUpgradeClick = {
                val tag = "PaywallBottomSheet"
                if (parentFragmentManager.findFragmentByTag(tag) == null) {
                    PaywallBottomSheet.newInstance(reason = "wishlist_notes")
                        .show(parentFragmentManager, tag)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.wishlist.collect { apps ->
                        adapter.submitList(apps)
                        tvEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    billingViewModel.entitlementState.collect { entitlement ->
                        adapter.setPremium(entitlement.isPremium)
                    }
                }
            }
        }
    }
}
