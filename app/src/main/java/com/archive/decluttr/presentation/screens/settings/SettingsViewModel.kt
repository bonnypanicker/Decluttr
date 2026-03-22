package com.archive.decluttr.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archive.decluttr.domain.usecase.ExportArchiveUseCase
import com.archive.decluttr.domain.usecase.ImportArchiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportArchiveUseCase: ExportArchiveUseCase,
    private val importArchiveUseCase: ImportArchiveUseCase
) : ViewModel() {

    private val _settingsState = MutableStateFlow<SettingsState>(SettingsState.Idle)
    val settingsState = _settingsState.asStateFlow()

    fun exportData() {
        viewModelScope.launch {
            _settingsState.value = SettingsState.Processing
            try {
                val json = exportArchiveUseCase()
                _settingsState.value = SettingsState.ExportSuccess(json)
            } catch (e: Exception) {
                _settingsState.value = SettingsState.Error(e.message ?: "Unknown error during export")
            }
        }
    }

    fun importData(jsonString: String) {
        viewModelScope.launch {
            _settingsState.value = SettingsState.Processing
            val success = importArchiveUseCase(jsonString)
            if (success) {
                _settingsState.value = SettingsState.ImportSuccess
            } else {
                _settingsState.value = SettingsState.Error("Failed to import data. Invalid format.")
            }
        }
    }

    fun resetState() {
        _settingsState.value = SettingsState.Idle
    }
}

sealed interface SettingsState {
    object Idle : SettingsState
    object Processing : SettingsState
    data class ExportSuccess(val jsonString: String) : SettingsState
    object ImportSuccess : SettingsState
    data class Error(val message: String) : SettingsState
}
