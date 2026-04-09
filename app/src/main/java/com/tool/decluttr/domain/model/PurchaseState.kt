package com.tool.decluttr.domain.model

sealed interface PurchaseState {
    data object Idle : PurchaseState
    data object Loading : PurchaseState
    data class Success(val message: String) : PurchaseState
    data class Error(
        val code: Int,
        val message: String
    ) : PurchaseState

    data object Canceled : PurchaseState
}
