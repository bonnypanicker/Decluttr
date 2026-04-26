package com.tool.decluttr.presentation.screens.wishlist

import android.os.Bundle
import android.os.Bundle
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.WishlistSortOption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WishlistFragment : Fragment(R.layout.fragment_wishlist) {
    private val viewModel: WishlistViewModel by viewModels()

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
            v.clipToPadding = false
            insets
        }

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnSort.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.apply {
                add(0, 0, 0, "Date Added").isChecked = viewModel.sortOption.value == WishlistSortOption.DATE_ADDED
                add(0, 1, 1, "Alphabetical").isChecked = viewModel.sortOption.value == WishlistSortOption.ALPHABETICAL
                add(0, 2, 2, "Category").isChecked = viewModel.sortOption.value == WishlistSortOption.CATEGORY
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
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.wishlist.collect { apps ->
                    adapter.submitList(apps)
                    tvEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
