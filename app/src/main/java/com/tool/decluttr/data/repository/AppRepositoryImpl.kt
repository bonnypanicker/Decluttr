package com.tool.decluttr.data.repository

import com.tool.decluttr.data.local.dao.AppDao
import com.tool.decluttr.data.mapper.toAppEntity
import com.tool.decluttr.data.mapper.toArchivedApp
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepositoryImpl(
    private val dao: AppDao
) : AppRepository {
    
    override fun getAllArchivedApps(): Flow<List<ArchivedApp>> {
        return dao.getAllArchivedApps().map { entities ->
            entities.map { it.toArchivedApp() }
        }
    }

    override suspend fun getAppById(packageId: String): ArchivedApp? {
        return dao.getAppById(packageId)?.toArchivedApp()
    }

    override suspend fun insertApp(app: ArchivedApp) {
        dao.insertApp(app.toAppEntity())
    }

    override suspend fun deleteApp(app: ArchivedApp) {
        dao.deleteApp(app.toAppEntity())
    }

    override suspend fun deleteAppById(packageId: String) {
        dao.deleteAppById(packageId)
    }

    override suspend fun updateApp(app: ArchivedApp) {
        dao.updateApp(app.toAppEntity())
    }
}
