package com.example.decluttr.presentation.screens.appdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.domain.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    private val repository: AppRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val packageId: String = checkNotNull(savedStateHandle["packageId"])

    private val _appState = MutableStateFlow<ArchivedApp?>(null)
    val appState = _appState.asStateFlow()

    init {
        loadApp()
    }

    private fun loadApp() {
        viewModelScope.launch {
            _appState.value = repository.getAppById(packageId)
        }
    }

    fun updateCategory(newCategory: String) {
        val currentApp = _appState.value ?: return
        val updatedApp = currentApp.copy(category = newCategory.takeIf { it.isNotBlank() })
        saveApp(updatedApp)
    }

    fun updateNotes(newNotes: String) {
        val currentApp = _appState.value ?: return
        val updatedApp = currentApp.copy(notes = newNotes.takeIf { it.isNotBlank() })
        saveApp(updatedApp)
    }

    fun updateTags(newTagsString: String) {
        val currentApp = _appState.value ?: return
        val tagsList = newTagsString.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val updatedApp = currentApp.copy(tags = tagsList)
        saveApp(updatedApp)
    }

    private fun saveApp(app: ArchivedApp) {
        viewModelScope.launch {
            repository.insertApp(app)
            _appState.value = app
        }
    }
}
