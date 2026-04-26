package com.tool.decluttr.domain.model

data class WishlistApp(
    val packageId: String,
    val name: String,
    val iconUrl: String,
    val description: String,
    val playStoreUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
)
