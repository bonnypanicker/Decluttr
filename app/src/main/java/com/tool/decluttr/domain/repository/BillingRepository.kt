package com.tool.decluttr.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {
    val isPremium: StateFlow<Boolean>
    val premiumPrice: StateFlow<String>

    fun startBillingConnection()
    fun launchBillingFlow(activity: android.app.Activity)
    fun endConnection()
}
