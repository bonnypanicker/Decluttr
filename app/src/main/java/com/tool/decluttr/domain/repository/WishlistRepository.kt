package com.tool.decluttr.domain.repository

import com.tool.decluttr.domain.model.WishlistApp
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun getAll(): Flow<List<WishlistApp>>
    suspend fun add(app: WishlistApp)
    suspend fun remove(packageId: String)
    suspend fun exists(packageId: String): Boolean
    suspend fun updateNotes(packageId: String, notes: String)
    suspend fun syncFromFirestore()
    suspend fun clearLocalData()
}
