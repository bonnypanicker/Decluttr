package com.tool.decluttr.presentation.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.domain.model.WishlistSortOption
import com.tool.decluttr.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistRepository,
) : ViewModel() {

    val sortOption = MutableStateFlow(WishlistSortOption.DATE_ADDED)
    val selectedCategory = MutableStateFlow("All")

    // All distinct categories derived from live list
    val categories: StateFlow<List<String>> = repository.getAll()
        .map { apps ->
            val cats = apps.mapNotNull { it.category }.distinct().sorted()
            listOf("All") + cats
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("All"))

    // Filtered + sorted list
    val wishlist: StateFlow<List<WishlistApp>> = combine(
        repository.getAll(),
        sortOption,
        selectedCategory,
    ) { apps, sort, category ->
        val filtered = if (category == "All") apps
                       else apps.filter { it.category == category }
        when (sort) {
            WishlistSortOption.DATE_ADDED    -> filtered.sortedByDescending { it.addedAt }
            WishlistSortOption.ALPHABETICAL  -> filtered.sortedBy { it.name.lowercase() }
            WishlistSortOption.CATEGORY      -> filtered.sortedWith(
                compareBy({ it.category ?: "Zzz" }, { it.name.lowercase() })
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSortOption(option: WishlistSortOption) { sortOption.value = option }
    fun setCategory(category: String) { selectedCategory.value = category }

    fun add(app: WishlistApp) = viewModelScope.launch { repository.add(app) }
    fun remove(packageId: String) = viewModelScope.launch { repository.remove(packageId) }
    suspend fun exists(packageId: String) = repository.exists(packageId)
    fun updateNotes(packageId: String, notes: String) =
        viewModelScope.launch { repository.updateNotes(packageId, notes) }
}
