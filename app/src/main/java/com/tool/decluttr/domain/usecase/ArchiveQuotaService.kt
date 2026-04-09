package com.tool.decluttr.domain.usecase

import com.tool.decluttr.domain.model.ArchiveQuotaResult
import com.tool.decluttr.domain.repository.AppRepository
import com.tool.decluttr.domain.repository.BillingRepository
import javax.inject.Inject

class ArchiveQuotaService @Inject constructor(
    private val appRepository: AppRepository,
    private val billingRepository: BillingRepository
) {
    companion object {
        const val FREE_ARCHIVE_LIMIT = 50
    }

    suspend fun checkQuota(requestedCount: Int): ArchiveQuotaResult {
        val used = appRepository.getArchivedAppCount().coerceAtLeast(0)
        val isPremium = billingRepository.currentEntitlement().isPremium
        return evaluateQuota(used = used, requestedCount = requestedCount, isPremium = isPremium)
    }

    fun evaluateQuota(
        used: Int,
        requestedCount: Int,
        isPremium: Boolean
    ): ArchiveQuotaResult {
        val safeUsed = used.coerceAtLeast(0)
        val safeRequested = requestedCount.coerceAtLeast(0)
        if (isPremium) {
            return ArchiveQuotaResult.Allowed(
                used = safeUsed,
                limit = Int.MAX_VALUE,
                requested = safeRequested,
                remaining = Int.MAX_VALUE,
                isPremium = true
            )
        }

        val projected = safeUsed + safeRequested
        return if (projected <= FREE_ARCHIVE_LIMIT) {
            ArchiveQuotaResult.Allowed(
                used = safeUsed,
                limit = FREE_ARCHIVE_LIMIT,
                requested = safeRequested,
                remaining = (FREE_ARCHIVE_LIMIT - projected).coerceAtLeast(0),
                isPremium = false
            )
        } else {
            ArchiveQuotaResult.Blocked(
                used = safeUsed,
                limit = FREE_ARCHIVE_LIMIT,
                requested = safeRequested,
                overflow = (projected - FREE_ARCHIVE_LIMIT).coerceAtLeast(0),
                isPremium = false
            )
        }
    }
}
