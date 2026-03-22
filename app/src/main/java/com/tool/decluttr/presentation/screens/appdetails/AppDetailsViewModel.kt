package com.tool.decluttr.presentation.screens.appdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private var packageId: String? = null

    private val _appState = MutableStateFlow<ArchivedApp?>(null)
    val appState = _appState.asStateFlow()

    private var saveJob: Job? = null
    private val SAVE_DEBOUNCE_MS = 500L

    fun loadAppDetails(packageId: String) {
        if (this.packageId == packageId && _appState.value != null) return
        this.packageId = packageId
        viewModelScope.launch {
            _appState.value = repository.getAppById(packageId)
        }
    }

    fun updateCategory(newCategory: String) {
        val currentApp = _appState.value ?: return
        val updatedApp = currentApp.copy(category = newCategory.takeIf { it.isNotBlank() })
        _appState.value = updatedApp
        debounceSave(updatedApp)
    }

    fun updateNotes(newNotes: String) {
        val currentApp = _appState.value ?: return
        val updatedApp = currentApp.copy(notes = newNotes.takeIf { it.isNotBlank() })
        _appState.value = updatedApp
        debounceSave(updatedApp)
    }

    fun updateTags(newTagsString: String) {
        val currentApp = _appState.value ?: return
        val tagsList = newTagsString.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val updatedApp = currentApp.copy(tags = tagsList)
        _appState.value = updatedApp
        debounceSave(updatedApp)
    }

    private fun debounceSave(app: ArchivedApp) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            repository.insertApp(app)
        }
    }
}

