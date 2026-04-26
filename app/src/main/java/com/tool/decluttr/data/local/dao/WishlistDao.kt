package com.tool.decluttr.data.local.dao

import androidx.room.*
import com.tool.decluttr.data.local.entity.WishlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {

    @Query("SELECT * FROM wishlist ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WishlistEntity>>

    @Query("SELECT * FROM wishlist")
    suspend fun getAllSnapshot(): List<WishlistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM wishlist WHERE packageId = :packageId LIMIT 1)")
    suspend fun exists(packageId: String): Boolean

    @Query("SELECT * FROM wishlist WHERE packageId = :packageId LIMIT 1")
    suspend fun getById(packageId: String): WishlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WishlistEntity)

    @Query("DELETE FROM wishlist WHERE packageId = :packageId")
    suspend fun delete(packageId: String)
}
