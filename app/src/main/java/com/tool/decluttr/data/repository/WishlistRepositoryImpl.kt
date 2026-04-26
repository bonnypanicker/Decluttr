package com.tool.decluttr.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tool.decluttr.data.local.dao.WishlistDao
import com.tool.decluttr.data.local.entity.WishlistEntity
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.domain.repository.WishlistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: WishlistDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : WishlistRepository {

    companion object {
        private const val MAX_SYNC_RETRIES = 3
        private const val SYNC_RETRY_BASE_DELAY_MS = 1500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val syncStateLock = Any()
    private val pendingUpserts = LinkedHashMap<String, WishlistApp>()
    private val pendingDeletes = LinkedHashSet<String>()
    
    @Volatile
    private var pendingSyncJob: Job? = null
    
    @Volatile
    private var currentUserId: String? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            scope.launch {
                val uid = firebaseAuth.currentUser?.uid
                if (uid != null) {
                    if (currentUserId != uid) {
                        clearPendingSyncState()
                        currentUserId = uid
                    }
                    syncFromFirestore()
                    schedulePendingSync()
                } else {
                    clearPendingSyncState()
                    currentUserId?.let {
                        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .remove("last_sync_time_wishlist_$it")
                            .apply()
                    }
                    currentUserId = null
                    dao.deleteAll()
                }
            }
        }
    }

    // ── Local reads ──────────────────────────────────────────────────────────

    override fun getAll(): Flow<List<WishlistApp>> =
        dao.getAll().map { it.map { e -> e.toDomain() } }

    override suspend fun exists(packageId: String) = dao.exists(packageId)

    // ── Writes: local first, then queue ─────────────────────────────────────

    override suspend fun add(app: WishlistApp) {
        val updatedApp = app.copy(lastModified = System.currentTimeMillis())
        dao.insert(updatedApp.toEntity())
        enqueueUpsert(updatedApp)
    }

    override suspend fun remove(packageId: String) {
        dao.delete(packageId)
        enqueueDelete(packageId)
    }

    override suspend fun updateNotes(packageId: String, notes: String) {
        dao.getById(packageId)?.let { existing ->
            val updated = existing.copy(notes = notes, lastModified = System.currentTimeMillis())
            dao.insert(updated)
            enqueueUpsert(updated.toDomain())
        }
    }

    // ── Sync Queueing ────────────────────────────────────────────────────────

    private fun enqueueUpsert(app: WishlistApp) {
        synchronized(syncStateLock) {
            pendingUpserts[app.packageId] = app
            pendingDeletes.remove(app.packageId)
        }
        schedulePendingSync()
    }

    private fun enqueueDelete(packageId: String) {
        synchronized(syncStateLock) {
            pendingUpserts.remove(packageId)
            pendingDeletes.add(packageId)
        }
        schedulePendingSync()
    }

    private fun schedulePendingSync(delayMs: Long = 0L) {
        synchronized(syncStateLock) {
            val existing = pendingSyncJob
            if (existing?.isActive == true) return
            pendingSyncJob = scope.launch {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                flushPendingSyncs()
            }
        }
    }

    private fun clearPendingSyncState() {
        synchronized(syncStateLock) {
            pendingUpserts.clear()
            pendingDeletes.clear()
        }
    }

    private suspend fun flushPendingSyncs() {
        syncMutex.withLock {
            while (true) {
                val nextDelete = synchronized(syncStateLock) { pendingDeletes.firstOrNull() }
                if (nextDelete != null) {
                    if (performDeleteWithRetry(nextDelete)) {
                        synchronized(syncStateLock) { pendingDeletes.remove(nextDelete) }
                        continue
                    }
                    synchronized(syncStateLock) {
                        pendingSyncJob = scope.launch {
                            delay(SYNC_RETRY_BASE_DELAY_MS * (MAX_SYNC_RETRIES + 1))
                            flushPendingSyncs()
                        }
                    }
                    return
                }

                val nextUpsert = synchronized(syncStateLock) {
                    val entry = pendingUpserts.entries.firstOrNull()
                    if (entry == null) {
                        pendingSyncJob = null
                        null
                    } else {
                        entry.toPair()
                    }
                } ?: return

                val packageId = nextUpsert.first
                val app = nextUpsert.second
                if (performUpsertWithRetry(app)) {
                    synchronized(syncStateLock) {
                        pendingUpserts.remove(packageId)
                    }
                    continue
                }
                synchronized(syncStateLock) {
                    pendingSyncJob = scope.launch {
                        delay(SYNC_RETRY_BASE_DELAY_MS * (MAX_SYNC_RETRIES + 1))
                        flushPendingSyncs()
                    }
                }
                return
            }
        }
    }

    private suspend fun performUpsertWithRetry(app: WishlistApp): Boolean {
        repeat(MAX_SYNC_RETRIES) { attempt ->
            val ok = runCatching { performUpsert(app) }
                .onFailure { android.util.Log.e("WishlistRepo", "Upsert fail", it) }
                .isSuccess
            if (ok) return true
            if (attempt < MAX_SYNC_RETRIES - 1) delay(SYNC_RETRY_BASE_DELAY_MS * (attempt + 1))
        }
        return false
    }

    private suspend fun performDeleteWithRetry(packageId: String): Boolean {
        repeat(MAX_SYNC_RETRIES) { attempt ->
            val ok = runCatching { performDelete(packageId) }
                .onFailure { android.util.Log.e("WishlistRepo", "Delete fail", it) }
                .isSuccess
            if (ok) return true
            if (attempt < MAX_SYNC_RETRIES - 1) delay(SYNC_RETRY_BASE_DELAY_MS * (attempt + 1))
        }
        return false
    }

    private suspend fun performUpsert(app: WishlistApp) {
        val user = auth.currentUser ?: error("No signed-in user")
        val dataMap = app.toFirestoreMap()
        firestore.collection("users").document(user.uid)
            .collection("wishlist").document(app.packageId)
            .set(dataMap, SetOptions.merge())
            .await()
    }

    private suspend fun performDelete(packageId: String) {
        val user = auth.currentUser ?: error("No signed-in user")
        val update = mapOf(
            "isDeleted" to true,
            "lastModified" to System.currentTimeMillis()
        )
        firestore.collection("users").document(user.uid)
            .collection("wishlist").document(packageId)
            .set(update, SetOptions.merge())
            .await()
    }

    // ── Sync from Firestore ──────────────────────────────────────────────────

    override suspend fun syncFromFirestore() = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null) {
            android.util.Log.w("WishlistRepo", "syncFromFirestore: skipped - no user")
            return@withContext
        }

        try {
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val lastSyncTimeKey = "last_sync_time_wishlist_${user.uid}"
            val lastSyncTime = prefs.getLong(lastSyncTimeKey, 0L)

            var query: com.google.firebase.firestore.Query = firestore.collection("users").document(user.uid).collection("wishlist")
            if (lastSyncTime > 0L) {
                query = query.whereGreaterThan("lastModified", lastSyncTime)
            }

            val snapshot = query.get().await()
            val localApps = dao.getAllSnapshot().associateBy { it.packageId }

            var maxModified = lastSyncTime

            for (doc in snapshot.documents) {
                val remoteLastModified = doc.getLong("lastModified") ?: 0L
                if (remoteLastModified > maxModified) {
                    maxModified = remoteLastModified
                }

                val packageId = doc.getString("packageId") ?: doc.id
                val isDeleted = doc.getBoolean("isDeleted") ?: false

                if (isDeleted) {
                    val localApp = localApps[packageId]
                    if (localApp != null && localApp.lastModified > remoteLastModified) {
                        enqueueUpsert(localApp.toDomain())
                    } else {
                        dao.delete(packageId)
                    }
                    continue
                }

                val remoteApp = doc.toWishlistAppOrNull() ?: continue
                val localApp = localApps[remoteApp.packageId]?.toDomain()
                
                when {
                    localApp == null -> dao.insert(remoteApp.toEntity())
                    remoteApp.lastModified >= localApp.lastModified -> dao.insert(remoteApp.toEntity())
                    else -> enqueueUpsert(localApp)
                }
            }

            if (maxModified > lastSyncTime) {
                prefs.edit().putLong(lastSyncTimeKey, maxModified).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("WishlistRepo", "syncFromFirestore failed", e)
        }
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun WishlistApp.toFirestoreMap() = mapOf(
        "packageId"    to packageId.take(200),
        "name"         to name.take(200),
        "iconUrl"      to iconUrl.take(1000),       // Store URL, heavily truncated to avoid any size limits
        "description"  to description.take(2000),   // Truncate overly long descriptions
        "playStoreUrl" to playStoreUrl.take(1000),
        "category"     to (category?.take(100) ?: ""),
        "notes"        to notes.take(1000),
        "addedAt"      to addedAt,
        "lastModified" to lastModified,
        "isDeleted"    to false
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
