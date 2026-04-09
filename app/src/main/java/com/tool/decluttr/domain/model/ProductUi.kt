package com.tool.decluttr.domain.model

data class ProductUi(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val isAvailable: Boolean
)
