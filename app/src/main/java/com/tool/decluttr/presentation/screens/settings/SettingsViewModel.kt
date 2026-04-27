package com.tool.decluttr.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.repository.AppRepository
import com.tool.decluttr.domain.repository.AuthRepository
import com.tool.decluttr.domain.repository.WishlistRepository
import com.tool.decluttr.domain.usecase.ExportArchiveUseCase
import com.tool.decluttr.domain.usecase.ImportArchiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportArchiveUseCase: ExportArchiveUseCase,
    private val importArchiveUseCase: ImportArchiveUseCase,
    private val authRepository: AuthRepository,
    private val appRepository: AppRepository,
    private val wishlistRepository: WishlistRepository
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean?> = authRepository.isUserLoggedIn
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val currentUserEmail: StateFlow<String?> = authRepository.currentUserEmail
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

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
            when (val result = importArchiveUseCase(jsonString)) {
                is ImportArchiveUseCase.Result.Success -> {
                    _settingsState.value = SettingsState.ImportSuccess
                }
                is ImportArchiveUseCase.Result.LimitReached -> {
                    _settingsState.value = SettingsState.Error(
                        "Archive limit reached (${result.used}/${result.limit}). Upgrade to import more."
                    )
                }
                ImportArchiveUseCase.Result.InvalidFormat -> {
                    _settingsState.value = SettingsState.Error("Failed to import data. Invalid format.")
                }
            }
        }
    }

    fun resetState() {
        _settingsState.value = SettingsState.Idle
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { appRepository.clearLocalData() }
            runCatching { wishlistRepository.clearLocalData() }
            authRepository.signOut()
        }
    }
}

sealed interface SettingsState {
    object Idle : SettingsState
    object Processing : SettingsState
    data class ExportSuccess(val jsonString: String) : SettingsState
    object ImportSuccess : SettingsState
    data class Error(val message: String) : SettingsState
}
