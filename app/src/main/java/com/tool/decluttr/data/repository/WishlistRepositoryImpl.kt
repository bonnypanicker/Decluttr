package com.tool.decluttr.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.tool.decluttr.data.local.dao.WishlistDao
import com.tool.decluttr.data.local.entity.WishlistEntity
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.domain.repository.WishlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepositoryImpl @Inject constructor(
    private val dao: WishlistDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : WishlistRepository {

    // \u2500\u2500 Local reads \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    override fun getAll(): Flow<List<WishlistApp>> =
        dao.getAll().map { it.map { e -> e.toDomain() } }

    override suspend fun exists(packageId: String) = dao.exists(packageId)

    // \u2500\u2500 Writes: local first, then cloud \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    override suspend fun add(app: WishlistApp) {
        dao.insert(app.toEntity())
        syncToFirestore(app)
    }

    override suspend fun remove(packageId: String) {
        dao.delete(packageId)
        deleteFromFirestore(packageId)
    }

    override suspend fun updateNotes(packageId: String, notes: String) {
        dao.getById(packageId)?.let { existing ->
            val updated = existing.copy(notes = notes, lastModified = System.currentTimeMillis())
            dao.insert(updated)
            syncToFirestore(updated.toDomain())
        }
    }

    // \u2500\u2500 Firestore write \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private suspend fun syncToFirestore(app: WishlistApp) =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                android.util.Log.e("WishlistRepo", "syncToFirestore: FAILED - User ID is null")
                return@withContext
            }
            android.util.Log.d("WishlistRepo", "syncToFirestore: Starting upload for ${app.packageId} under user $uid")
            
            runCatching {
                val dataMap = app.toFirestoreMap()
                android.util.Log.d("WishlistRepo", "syncToFirestore: Data map prepared: $dataMap")
                
                firestore
                    .collection("users").document(uid)
                    .collection("wishlist").document(app.packageId)
                    .set(dataMap)
                    .await()
                android.util.Log.d("WishlistRepo", "syncToFirestore: SUCCESS - Document written")
            }.onFailure { 
                android.util.Log.e("WishlistRepo", "syncToFirestore: ERROR writing to Firestore", it)
            }
        }

    private suspend fun deleteFromFirestore(packageId: String) =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid ?: return@withContext
            runCatching {
                firestore
                    .collection("users").document(uid)
                    .collection("wishlist").document(packageId)
                    .delete()
                    .await()
            }.onFailure { it.printStackTrace() }
        }

    // \u2500\u2500 One-shot sync on login \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    override suspend fun syncFromFirestore() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        runCatching {
            val snapshot = firestore
                .collection("users").document(uid)
                .collection("wishlist")
                .get()
                .await()

            val remoteApps = snapshot.documents.mapNotNull { it.toWishlistAppOrNull() }
            val localApps = dao.getAllSnapshot().associateBy { it.packageId }

            remoteApps.forEach { remote ->
                val local = localApps[remote.packageId]
                if (local == null || remote.lastModified >= local.lastModified) {
                    dao.insert(remote.toEntity())
                }
            }
        }.onFailure { it.printStackTrace() }
    }

    // \u2500\u2500 Mappers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun WishlistApp.toFirestoreMap() = mapOf(
        "packageId"    to packageId,
        "name"         to name,
        "iconUrl"      to iconUrl,       // store URL, not bytes \u2014 avoids 1MB doc limit
        "description"  to description,
        "playStoreUrl" to playStoreUrl,
        "category"     to (category ?: ""),
        "notes"        to notes,
        "addedAt"      to addedAt,
        "lastModified" to (lastModified),
    )

    private fun DocumentSnapshot.toWishlistAppOrNull(): WishlistApp? {
        val pkg = getString("packageId") ?: id.takeIf { it.isNotBlank() } ?: return null
        return WishlistApp(
            packageId    = pkg,
            name         = getString("name") ?: pkg,
            iconUrl      = getString("iconUrl") ?: "",
            description  = getString("description") ?: "",
            playStoreUrl = getString("playStoreUrl")
                           ?: "https://play.google.com/store/apps/details?id=$pkg",
            category     = getString("category")?.ifBlank { null },
            notes        = getString("notes") ?: "",
            addedAt      = getLong("addedAt") ?: System.currentTimeMillis(),
            lastModified = getLong("lastModified") ?: System.currentTimeMillis(),
        )
    }

    private fun WishlistEntity.toDomain() = WishlistApp(
        packageId    = packageId, name = name, iconUrl = iconUrl,
        description  = description, playStoreUrl = playStoreUrl,
        category     = category, notes = notes,
        addedAt      = addedAt, lastModified = lastModified,
    )

    private fun WishlistApp.toEntity() = WishlistEntity(
        packageId    = packageId, name = name, iconUrl = iconUrl,
        description  = description, playStoreUrl = playStoreUrl,
        category     = category, notes = notes,
        addedAt      = addedAt, lastModified = lastModified,
    )
}
