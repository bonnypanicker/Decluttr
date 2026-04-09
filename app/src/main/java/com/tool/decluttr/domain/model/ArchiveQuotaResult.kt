package com.tool.decluttr.domain.model

sealed interface ArchiveQuotaResult {
    data class Allowed(
        val used: Int,
        val limit: Int,
        val requested: Int,
        val remaining: Int,
        val isPremium: Boolean
    ) : ArchiveQuotaResult

    data class Blocked(
        val used: Int,
        val limit: Int,
        val requested: Int,
        val overflow: Int,
        val isPremium: Boolean
    ) : ArchiveQuotaResult
}
