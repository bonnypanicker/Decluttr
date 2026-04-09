package com.tool.decluttr.domain.model

data class EntitlementState(
    val isPremium: Boolean = false,
    val source: String = "NONE",
    val lastVerifiedAt: Long = 0L,
    val productId: String? = null,
    val purchaseTokenHash: String? = null
) {
    fun hasFreshVerification(nowMs: Long, graceWindowMs: Long): Boolean {
        if (!isPremium || lastVerifiedAt <= 0L) return false
        return nowMs - lastVerifiedAt <= graceWindowMs
    }
}
