package com.tool.decluttr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tool.decluttr.data.local.entity.AppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM archived_apps ORDER BY archivedAt DESC")
    fun getAllArchivedApps(): Flow<List<AppEntity>>

    @Query("SELECT * FROM archived_apps WHERE packageId = :packageId")
    suspend fun getAppById(packageId: String): AppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppEntity)

    @Delete
    suspend fun deleteApp(app: AppEntity)
    
    @Query("DELETE FROM archived_apps")
    suspend fun deleteAllApps()

    @Query("DELETE FROM archived_apps WHERE packageId = :packageId")
    suspend fun deleteAppById(packageId: String)
    
    @androidx.room.Update
    suspend fun updateApp(app: AppEntity)
}
