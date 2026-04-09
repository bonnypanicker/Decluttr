package com.tool.decluttr.domain.repository

import android.app.Activity
import com.tool.decluttr.domain.model.EntitlementState
import com.tool.decluttr.domain.model.ProductUi
import com.tool.decluttr.domain.model.PurchaseState
import kotlinx.coroutines.flow.Flow

interface BillingRepository {
    fun observeEntitlement(): Flow<EntitlementState>
    fun observeProduct(): Flow<ProductUi>
    fun observePurchaseState(): Flow<PurchaseState>

    fun currentEntitlement(): EntitlementState

    suspend fun startPurchase(activity: Activity): Result<Unit>
    suspend fun restorePurchases(): Result<Unit>
    suspend fun refreshEntitlement()
    suspend fun syncEntitlementWithServer(
        purchaseToken: String,
        productId: String?
    ): Result<EntitlementState>
}
