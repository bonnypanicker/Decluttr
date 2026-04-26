package com.tool.decluttr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wishlist")
data class WishlistEntity(
    @PrimaryKey val packageId: String,
    val name: String,
    val iconUrl: String,
    val description: String,
    val playStoreUrl: String,
    val addedAt: Long,
    val notes: String,
)
