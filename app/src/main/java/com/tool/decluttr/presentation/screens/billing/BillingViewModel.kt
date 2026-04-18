package com.tool.decluttr.presentation.screens.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.model.EntitlementState
import com.tool.decluttr.domain.model.ProductUi
import com.tool.decluttr.domain.model.PurchaseState
import com.tool.decluttr.domain.repository.AppRepository
import com.tool.decluttr.domain.repository.AuthRepository
import com.tool.decluttr.domain.repository.BillingRepository
import com.tool.decluttr.domain.usecase.ArchiveQuotaService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillingViewModel @Inject constructor(
    appRepository: AppRepository,
    authRepository: AuthRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

    data class ArchiveCreditsUi(
        val isPremium: Boolean,
        val isVisible: Boolean,
        val used: Int,
        val limit: Int,
        val remaining: Int,
        val label: String,
        val progress: Int
    )

    val entitlementState: StateFlow<EntitlementState> = billingRepository.observeEntitlement()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), billingRepository.currentEntitlement())

    val productUi: StateFlow<ProductUi> = billingRepository.observeProduct()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ProductUi(
                productId = "decluttr_premium_upgrade",
                title = "Decluttr Premium",
                description = "Unlock unlimited cloud archive",
                formattedPrice = "INR 99",
                isAvailable = false
            )
        )

    val purchaseState: StateFlow<PurchaseState> = billingRepository.observePurchaseState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PurchaseState.Idle)

    val isLoggedIn: StateFlow<Boolean> = authRepository.isUserLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isBillingLoading = MutableStateFlow(false)

    val archiveCreditsUi: StateFlow<ArchiveCreditsUi> = combine(
        appRepository.getAllArchivedApps().map { it.size },
        entitlementState,
        authRepository.isUserLoggedIn,
        _isBillingLoading
    ) { archivedCount, entitlement, loggedInObj, isLoading ->
        val loggedIn = loggedInObj ?: false
        val used = archivedCount.coerceAtLeast(0)
        val isVisible = loggedIn && !isLoading

        if (entitlement.isPremium) {
            ArchiveCreditsUi(
                isPremium = true,
                isVisible = isVisible,
                used = used,
                limit = Int.MAX_VALUE,
                remaining = Int.MAX_VALUE,
                label = "Unlimited archive credits",
                progress = 100
            )
        } else {
            val limit = ArchiveQuotaService.FREE_ARCHIVE_LIMIT
            val remaining = (limit - used).coerceAtLeast(0)
            val progress = ((used.toDouble() / limit.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
            ArchiveCreditsUi(
                isPremium = false,
                isVisible = isVisible,
                used = used,
                limit = limit,
                remaining = remaining,
                label = "$used/$limit archive credits used",
                progress = progress
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ArchiveCreditsUi(
            isPremium = billingRepository.currentEntitlement().isPremium,
            isVisible = false,
            used = 0,
            limit = ArchiveQuotaService.FREE_ARCHIVE_LIMIT,
            remaining = ArchiveQuotaService.FREE_ARCHIVE_LIMIT,
            label = "0/${ArchiveQuotaService.FREE_ARCHIVE_LIMIT} archive credits used",
            progress = 0
        )
    )

    fun refreshBilling() {
        viewModelScope.launch {
            _isBillingLoading.value = true
            billingRepository.refreshEntitlement()
            billingRepository.restorePurchases()
            _isBillingLoading.value = false
        }
    }

    fun startPremiumPurchase(activity: Activity) {
        viewModelScope.launch {
            billingRepository.startPurchase(activity)
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            billingRepository.restorePurchases()
        }
    }
}
