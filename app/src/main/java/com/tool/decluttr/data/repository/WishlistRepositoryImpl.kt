package com.tool.decluttr.data.repository

import com.tool.decluttr.data.local.dao.WishlistDao
import com.tool.decluttr.data.local.entity.WishlistEntity
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistDao,
) : WishlistRepository {

    override fun getAll(): Flow<List<WishlistApp>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(app: WishlistApp) =
        dao.insert(app.toEntity())

    override suspend fun remove(packageId: String) =
        dao.delete(packageId)

    override suspend fun exists(packageId: String): Boolean =
        dao.exists(packageId)

    override suspend fun updateNotes(packageId: String, notes: String) {
        dao.getById(packageId)?.let { existing ->
            dao.insert(existing.copy(notes = notes))
        }
    }

    private fun WishlistEntity.toDomain() = WishlistApp(
        packageId   = packageId,
        name        = name,
        iconUrl     = iconUrl,
        description = description,
        playStoreUrl = playStoreUrl,
        addedAt     = addedAt,
        notes       = notes,
    )

    private fun WishlistApp.toEntity() = WishlistEntity(
        packageId   = packageId,
        name        = name,
        iconUrl     = iconUrl,
        description = description,
        playStoreUrl = playStoreUrl,
        addedAt     = addedAt,
        notes       = notes,
    )
}
