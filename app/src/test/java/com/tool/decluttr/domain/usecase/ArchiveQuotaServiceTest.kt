package com.tool.decluttr.domain.usecase

import android.app.Activity
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.model.EntitlementState
import com.tool.decluttr.domain.model.ArchiveQuotaResult
import com.tool.decluttr.domain.model.ProductUi
import com.tool.decluttr.domain.model.PurchaseState
import com.tool.decluttr.domain.repository.AppRepository
import com.tool.decluttr.domain.repository.BillingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ArchiveQuotaServiceTest {

    @Test
    fun `free user under limit is allowed`() {
        val result = ArchiveQuotaService(
            appRepository = FakeAppRepository(),
            billingRepository = FakeBillingRepository(isPremium = false)
        ).evaluateQuota(
            used = 10,
            requestedCount = 5,
            isPremium = false
        )

        assertTrue(result is ArchiveQuotaResult.Allowed)
        val allowed = result as ArchiveQuotaResult.Allowed
        assertEquals(10, allowed.used)
        assertEquals(50, allowed.limit)
        assertEquals(35, allowed.remaining)
    }

    @Test
    fun `free user over limit is blocked`() {
        val result = ArchiveQuotaService(
            appRepository = FakeAppRepository(),
            billingRepository = FakeBillingRepository(isPremium = false)
        ).evaluateQuota(
            used = 49,
            requestedCount = 2,
            isPremium = false
        )

        assertTrue(result is ArchiveQuotaResult.Blocked)
        val blocked = result as ArchiveQuotaResult.Blocked
        assertEquals(49, blocked.used)
        assertEquals(50, blocked.limit)
        assertEquals(1, blocked.overflow)
    }

    @Test
    fun `premium user bypasses limit`() {
        val result = ArchiveQuotaService(
            appRepository = FakeAppRepository(),
            billingRepository = FakeBillingRepository(isPremium = true)
        ).evaluateQuota(
            used = 200,
            requestedCount = 40,
            isPremium = true
        )

        assertTrue(result is ArchiveQuotaResult.Allowed)
        val allowed = result as ArchiveQuotaResult.Allowed
        assertTrue(allowed.isPremium)
        assertEquals(Int.MAX_VALUE, allowed.limit)
    }
}

private class FakeAppRepository : AppRepository {
    override fun getAllArchivedApps(): Flow<List<ArchivedApp>> = flowOf(emptyList())
    override suspend fun getArchivedAppCount(): Int = 0
    override suspend fun getAppById(packageId: String): ArchivedApp? = null
    override suspend fun insertApp(app: ArchivedApp) {}
    override suspend fun deleteApp(app: ArchivedApp) {}
    override suspend fun deleteAppById(packageId: String) {}
    override suspend fun updateApp(app: ArchivedApp) {}
}

private class FakeBillingRepository(
    private val isPremium: Boolean
) : BillingRepository {
    override fun observeEntitlement(): Flow<EntitlementState> = flowOf(
        EntitlementState(isPremium = isPremium)
    )
    override fun observeProduct(): Flow<ProductUi> = flowOf(
        ProductUi(
            productId = "premium_unlimited_archiving",
            title = "Decluttr Premium",
            description = "Unlock unlimited",
            formattedPrice = "INR 99",
            isAvailable = true
        )
    )
    override fun observePurchaseState(): Flow<PurchaseState> = flowOf(PurchaseState.Idle)
    override fun currentEntitlement(): EntitlementState = EntitlementState(isPremium = isPremium)
    override suspend fun startPurchase(activity: Activity): Result<Unit> = Result.success(Unit)
    override suspend fun restorePurchases(): Result<Unit> = Result.success(Unit)
    override suspend fun refreshEntitlement() {}
    override suspend fun syncEntitlementWithServer(
        purchaseToken: String,
        productId: String?
    ): Result<EntitlementState> = Result.success(EntitlementState(isPremium = isPremium))
}
