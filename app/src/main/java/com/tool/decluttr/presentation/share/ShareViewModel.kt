package com.tool.decluttr.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.model.ArchiveLimitExceededException
import com.tool.decluttr.domain.usecase.CaptureAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val captureAppUseCase: CaptureAppUseCase
) : ViewModel() {

    private val _shareStatus = MutableStateFlow<ShareStatus>(ShareStatus.Idle)
    val shareStatus = _shareStatus.asStateFlow()

    fun handleSharedText(text: String?) {
        if (text.isNullOrBlank()) {
            _shareStatus.value = ShareStatus.Error("No text provided")
            return
        }

        _shareStatus.value = ShareStatus.Processing
        viewModelScope.launch {
            runCatching { captureAppUseCase(text) }
                .onSuccess { packageId ->
                    if (packageId != null) {
                        _shareStatus.value = ShareStatus.Success(packageId)
                    } else {
                        _shareStatus.value = ShareStatus.Error("Could not find a valid Play Store link")
                    }
                }
                .onFailure { error ->
                    if (error is ArchiveLimitExceededException) {
                        _shareStatus.value = ShareStatus.Error(
                            "Archive limit reached (${error.used}/${error.limit}). You can still access existing archived apps. Upgrade to archive more."
                        )
                    } else {
                        _shareStatus.value = ShareStatus.Error(
                            error.message ?: "Could not archive this app right now."
                        )
                    }
                }
        }
    }
    
    fun resetStatus() {
        _shareStatus.value = ShareStatus.Idle
    }
}

sealed interface ShareStatus {
    object Idle : ShareStatus
    object Processing : ShareStatus
    data class Success(val packageId: String) : ShareStatus
    data class Error(val message: String) : ShareStatus
}
