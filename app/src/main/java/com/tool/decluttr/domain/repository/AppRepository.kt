package com.tool.decluttr.domain.repository

import com.tool.decluttr.domain.model.ArchivedApp
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getAllArchivedApps(): Flow<List<ArchivedApp>>

    suspend fun getArchivedAppCount(): Int
    
    suspend fun getAppById(packageId: String): ArchivedApp?
    
    suspend fun insertApp(app: ArchivedApp)
    
    suspend fun deleteApp(app: ArchivedApp)
    
    suspend fun deleteAppById(packageId: String)
    
    suspend fun updateApp(app: ArchivedApp)
}
