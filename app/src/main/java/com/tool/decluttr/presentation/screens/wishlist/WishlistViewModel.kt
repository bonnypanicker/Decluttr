package com.tool.decluttr.presentation.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistRepository,
) : ViewModel() {

    val wishlist: StateFlow<List<WishlistApp>> = repository
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(app: WishlistApp) = viewModelScope.launch {
        repository.add(app)
    }

    fun remove(packageId: String) = viewModelScope.launch {
        repository.remove(packageId)
    }

    suspend fun exists(packageId: String): Boolean =
        repository.exists(packageId)

    fun updateNotes(packageId: String, notes: String) = viewModelScope.launch {
        repository.updateNotes(packageId, notes)
    }
}
