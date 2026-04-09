package com.tool.decluttr.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tool.decluttr.data.local.dao.AppDao
import com.tool.decluttr.data.mapper.toAppEntity
import com.tool.decluttr.data.mapper.toArchivedApp
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.model.ArchiveLimitExceededException
import com.tool.decluttr.domain.repository.AppRepository
import com.tool.decluttr.domain.repository.BillingRepository
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
import javax.inject.Provider

class AppRepositoryImpl(
    @ApplicationContext private val context: Context,
    private val dao: AppDao,
    private val authProvider: Provider<FirebaseAuth>,
    private val firestoreProvider: Provider<FirebaseFirestore>,
    private val billingRepository: BillingRepository
) : AppRepository {
    companion object {
        private const val TAG = "DecluttrDragDbgRepo"
        private const val FREE_ARCHIVE_LIMIT = 50
        private const val LOCAL_ICON_DIM = 144
        private const val FIRESTORE_ICON_MAX_DIM = 128
        private const val FIRESTORE_ICON_MAX_BYTES = 24 * 1024
        private val FIRESTORE_JPEG_QUALITIES = intArrayOf(92, 84, 76, 68)
        private const val MAX_SYNC_RETRIES = 3
        private const val SYNC_RETRY_BASE_DELAY_MS = 1500L
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val syncStateLock = Any()
    private val pendingUpserts = LinkedHashMap<String, ArchivedApp>()
    private val pendingDeletes = LinkedHashSet<String>()
    @Volatile
    private var pendingSyncJob: Job? = null
    @Volatile
    private var currentUserId: String? = null

    init {
        firebaseAuthOrNull()?.addAuthStateListener { firebaseAuth ->
            scope.launch {
                val uid = firebaseAuth.currentUser?.uid
                if (uid != null) {
                    if (currentUserId != uid) {
                        clearPendingSyncState()
                        currentUserId = uid
                    }
                    syncFromFirestore()
                    flushPendingSyncs()
                } else {
                    clearPendingSyncState()
                    currentUserId = null
                    dao.deleteAllApps()
                }
            }
        }
    }

    private suspend fun syncFromFirestore() {
        val auth = firebaseAuthOrNull() ?: return
        val firestore = firestoreOrNull() ?: return
        val user = auth.currentUser ?: return
        try {
            val snapshot = firestore.collection("users").document(user.uid).collection("apps").get().await()
            val localApps = dao.getArchivedAppsSnapshot().associateBy { it.packageId }
            val remoteApps = snapshot.documents.mapNotNull { doc ->
                doc.toArchivedAppOrNull()
            }

            remoteApps.forEach { remoteApp ->
                val localApp = localApps[remoteApp.packageId]?.toArchivedApp()
                val mergedRemoteApp = mergeRemoteWithLocal(remoteApp, localApp)
                val enrichedRemoteApp = enrichArchiveApp(mergedRemoteApp)
                val shouldHealRemote = requiresRemoteHealing(remoteApp, enrichedRemoteApp)
                val usingRemoteAsSource = localApp == null || remoteApp.lastModified >= localApp.lastModified
                when {
                    localApp == null -> dao.insertApp(enrichedRemoteApp.toAppEntity())
                    remoteApp.lastModified >= localApp.lastModified -> dao.insertApp(enrichedRemoteApp.toAppEntity())
                    else -> enqueueUpsert(localApp)
                }
                if (usingRemoteAsSource && shouldHealRemote) {
                    enqueueUpsert(enrichedRemoteApp)
                }
            }

            localApps.values
                .map { it.toArchivedApp() }
                .filter { localApp -> remoteApps.none { it.packageId == localApp.packageId } }
                .forEach { localOnlyApp ->
                    enqueueUpsert(localOnlyApp)
                }
        } catch (e: Exception) {
            recordException(e)
        }
    }

    private fun DocumentSnapshot.toArchivedAppOrNull(): ArchivedApp? {
        val id = getString("packageId") ?: return null
        val archivedAt = getLong("archivedAt") ?: System.currentTimeMillis()
        val rawIconBase64 = getString("iconBase64")
        val iconBytes = decodeIconBase64(rawIconBase64).also { decoded ->
            if (rawIconBase64 != null && decoded == null) {
                android.util.Log.w(TAG, "decodeIconBase64 failed pkg=$id")
            }
        }
        return ArchivedApp(
            packageId = id,
            name = getString("name") ?: id,
            isPlayStoreInstalled = getBoolean("isPlayStoreInstalled") ?: true,
            category = getString("category")?.takeIf { it.isNotBlank() },
            tags = parseTags(get("tags")),
            notes = getString("notes"),
            iconBytes = iconBytes,
            archivedSizeBytes = getLong("archivedSizeBytes")?.takeIf { it > 0L },
            archivedAt = archivedAt,
            lastTimeUsed = getLong("lastTimeUsed") ?: 0L,
            folderName = getString("folderName"),
            lastModified = getLong("lastModified") ?: archivedAt
        )
    }

    private fun parseTags(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun enqueueUpsert(app: ArchivedApp) {
        synchronized(syncStateLock) {
            pendingDeletes.remove(app.packageId)
            val previous = pendingUpserts[app.packageId]
            if (previous == null || app.lastModified >= previous.lastModified) {
                pendingUpserts[app.packageId] = app
            }
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
        val existing = pendingSyncJob
        if (existing?.isActive == true) return
        pendingSyncJob = scope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            flushPendingSyncs()
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
                    pendingSyncJob = scope.launch {
                        delay(SYNC_RETRY_BASE_DELAY_MS * (MAX_SYNC_RETRIES + 1))
                        flushPendingSyncs()
                    }
                    return
                }

                val nextUpsert = synchronized(syncStateLock) {
                    pendingUpserts.entries.firstOrNull()?.toPair()
                } ?: return

                val packageId = nextUpsert.first
                val app = nextUpsert.second
                if (performUpsertWithRetry(app)) {
                    synchronized(syncStateLock) {
                        pendingUpserts.remove(packageId)
                    }
                    continue
                }
                pendingSyncJob = scope.launch {
                    delay(SYNC_RETRY_BASE_DELAY_MS * (MAX_SYNC_RETRIES + 1))
                    flushPendingSyncs()
                }
                return
            }
        }
    }

    private suspend fun performUpsertWithRetry(app: ArchivedApp): Boolean {
        repeat(MAX_SYNC_RETRIES) { attempt ->
            val ok = runCatching { performUpsert(app) }
                .onFailure { recordException(it) }
                .isSuccess
            if (ok) {
                return true
            }
            if (attempt < MAX_SYNC_RETRIES - 1) {
                delay(SYNC_RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }
        return false
    }

    private suspend fun performDeleteWithRetry(packageId: String): Boolean {
        repeat(MAX_SYNC_RETRIES) { attempt ->
            val ok = runCatching { performDelete(packageId) }
                .onFailure { recordException(it) }
                .isSuccess
            if (ok) {
                return true
            }
            if (attempt < MAX_SYNC_RETRIES - 1) {
                delay(SYNC_RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }
        return false
    }

    private suspend fun performUpsert(app: ArchivedApp) {
        val auth = firebaseAuthOrNull() ?: error("FirebaseAuth unavailable")
        val firestore = firestoreOrNull() ?: error("Firestore unavailable")
        val user = auth.currentUser ?: error("No signed-in user")
        val iconBase64 = compressIconForFirestore(app.iconBytes)
        val data = mapOf(
            "packageId" to app.packageId,
            "name" to app.name,
            "iconBase64" to iconBase64,
            "archivedSizeBytes" to app.archivedSizeBytes,
            "category" to app.category?.takeIf { it.isNotBlank() },
            "tags" to app.tags,
            "notes" to app.notes,
            "archivedAt" to app.archivedAt,
            "isPlayStoreInstalled" to app.isPlayStoreInstalled,
            "lastTimeUsed" to app.lastTimeUsed,
            "folderName" to app.folderName,
            "lastModified" to app.lastModified
        )
        firestore.collection("users").document(user.uid).collection("apps")
            .document(app.packageId)
            .set(data, SetOptions.merge())
            .await()
    }

    private suspend fun performDelete(packageId: String) {
        val auth = firebaseAuthOrNull() ?: error("FirebaseAuth unavailable")
        val firestore = firestoreOrNull() ?: error("Firestore unavailable")
        val user = auth.currentUser ?: error("No signed-in user")
        firestore.collection("users").document(user.uid).collection("apps")
            .document(packageId)
            .delete()
            .await()
    }

    private fun compressIconForFirestore(iconBytes: ByteArray?): String? {
        if (iconBytes == null) return null

        val bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
            ?: return Base64.encodeToString(iconBytes, Base64.NO_WRAP)

        val scaledBitmap = scaleDownIfNeeded(bitmap, FIRESTORE_ICON_MAX_DIM)
        val compressedBytes = compressBitmapForFirestore(scaledBitmap)
        if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }
        return Base64.encodeToString(compressedBytes ?: iconBytes, Base64.NO_WRAP)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val largest = maxOf(width, height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun compressBitmapForFirestore(bitmap: Bitmap): ByteArray? {
        val pngBytes = encodeBitmap(bitmap, Bitmap.CompressFormat.PNG, 100)
        if (pngBytes != null && pngBytes.size <= FIRESTORE_ICON_MAX_BYTES) {
            return pngBytes
        }

        var best = pngBytes
        for (quality in FIRESTORE_JPEG_QUALITIES) {
            val jpegBytes = encodeBitmap(bitmap, Bitmap.CompressFormat.JPEG, quality) ?: continue
            if (best == null || jpegBytes.size < best.size) best = jpegBytes
            if (jpegBytes.size <= FIRESTORE_ICON_MAX_BYTES) return jpegBytes
        }
        return best
    }

    private fun normalizeForWrite(previous: ArchivedApp?, app: ArchivedApp): ArchivedApp {
        val resolvedName = if (
            previous != null &&
            isLikelyPackageId(app.name) &&
            !isLikelyPackageId(previous.name)
        ) {
            previous.name
        } else {
            app.name
        }
        val resolvedIconBytes = app.iconBytes ?: previous?.iconBytes
        val resolvedSizeBytes = app.archivedSizeBytes ?: previous?.archivedSizeBytes
        val resolvedCategory = app.category?.takeIf { it.isNotBlank() } ?: previous?.category
        val normalizedApp = app.copy(
            name = resolvedName,
            category = resolvedCategory,
            iconBytes = resolvedIconBytes,
            archivedSizeBytes = resolvedSizeBytes
        )

        val contentChanged = previous == null ||
            previous.name != normalizedApp.name ||
            previous.isPlayStoreInstalled != normalizedApp.isPlayStoreInstalled ||
            previous.category != normalizedApp.category ||
            previous.tags != normalizedApp.tags ||
            previous.notes != normalizedApp.notes ||
            previous.archivedSizeBytes != normalizedApp.archivedSizeBytes ||
            previous.lastTimeUsed != normalizedApp.lastTimeUsed ||
            previous.folderName != normalizedApp.folderName ||
            !previous.iconBytes.contentEquals(normalizedApp.iconBytes)
        android.util.Log.v(
            TAG,
            "normalizeForWrite pkg=${app.packageId} changed=$contentChanged prevFolder=${previous?.folderName} newFolder=${normalizedApp.folderName}"
        )
        return if (contentChanged) {
            normalizedApp.copy(lastModified = System.currentTimeMillis())
        } else {
            normalizedApp
        }
    }

    private fun isLikelyPackageId(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return value.contains('.') && value == value.lowercase()
    }

    private fun mergeRemoteWithLocal(remoteApp: ArchivedApp, localApp: ArchivedApp?): ArchivedApp {
        if (localApp == null) return remoteApp

        val resolvedName = if (
            isLikelyPackageId(remoteApp.name) &&
            !isLikelyPackageId(localApp.name)
        ) {
            localApp.name
        } else {
            remoteApp.name
        }
        val resolvedIconBytes = remoteApp.iconBytes ?: localApp.iconBytes
        val resolvedSizeBytes = remoteApp.archivedSizeBytes ?: localApp.archivedSizeBytes
        val resolvedCategory = remoteApp.category?.takeIf { it.isNotBlank() } ?: localApp.category
        return remoteApp.copy(
            name = resolvedName,
            category = resolvedCategory,
            iconBytes = resolvedIconBytes,
            archivedSizeBytes = resolvedSizeBytes
        )
    }

    private fun requiresRemoteHealing(remoteApp: ArchivedApp, mergedApp: ArchivedApp): Boolean {
        return remoteApp.name != mergedApp.name ||
            remoteApp.category != mergedApp.category ||
            remoteApp.archivedSizeBytes != mergedApp.archivedSizeBytes ||
            !sameBytes(remoteApp.iconBytes, mergedApp.iconBytes)
    }

    private fun sameBytes(a: ByteArray?, b: ByteArray?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a.contentEquals(b)
    }

    private fun resolveCategoryName(categoryNumber: Int): String? {
        return when (categoryNumber) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Photography"
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News & Magazines"
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Maps & Navigation"
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
            else -> null
        }
    }

    private fun enrichArchiveApp(app: ArchivedApp): ArchivedApp {
        var result = app
        val pm = context.packageManager
        try {
            val appInfo = pm.getApplicationInfo(app.packageId, 0)
            val label = pm.getApplicationLabel(appInfo)?.toString()?.trim()
            if (!label.isNullOrBlank() && (result.name.isBlank() || isLikelyPackageId(result.name))) {
                result = result.copy(name = label)
            }
            if (result.archivedSizeBytes == null) {
                val size = runCatching { java.io.File(appInfo.sourceDir).length() }.getOrNull()
                if (size != null && size > 0L) {
                    result = result.copy(archivedSizeBytes = size)
                }
            }
            if (result.category.isNullOrBlank()) {
                resolveCategoryName(appInfo.category)?.let { category ->
                    result = result.copy(category = category)
                }
            }
            if (result.iconBytes == null) {
                val drawable = pm.getApplicationIcon(appInfo)
                val iconBytes = drawableToCompressedIconBytes(drawable)
                if (iconBytes != null) {
                    result = result.copy(iconBytes = iconBytes)
                }
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // App not installed; keep archived data as-is.
        } catch (e: Exception) {
            recordException(e)
        }
        return result
    }

    private fun drawableToCompressedIconBytes(drawable: Drawable): ByteArray? {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        val scaled = Bitmap.createScaledBitmap(bitmap, LOCAL_ICON_DIM, LOCAL_ICON_DIM, true)
        val bytes = encodeBitmap(scaled, Bitmap.CompressFormat.PNG, 100)
        if (scaled !== bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }
        return bytes
    }

    private fun firebaseAuthOrNull(): FirebaseAuth? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return null
        }
        return runCatching { authProvider.get() }.getOrNull()
    }

    private fun firestoreOrNull(): FirebaseFirestore? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return null
        }
        return runCatching { firestoreProvider.get() }.getOrNull()
    }

    private fun recordException(throwable: Throwable) {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            runCatching { FirebaseCrashlytics.getInstance().recordException(throwable) }
        }
    }

    override fun getAllArchivedApps(): Flow<List<ArchivedApp>> {
        return dao.getAllArchivedApps().map { entities ->
            entities.map { it.toArchivedApp() }
        }
    }

    override suspend fun getAppById(packageId: String): ArchivedApp? {
        return dao.getAppById(packageId)?.toArchivedApp()
    }

    override suspend fun getArchivedAppCount(): Int {
        return dao.getArchivedAppCount().coerceAtLeast(0)
    }

    override suspend fun insertApp(app: ArchivedApp) {
        val previous = dao.getAppById(app.packageId)?.toArchivedApp()
        if (previous == null) {
            val entitlement = billingRepository.currentEntitlement()
            if (!entitlement.isPremium) {
                val used = dao.getArchivedAppCount().coerceAtLeast(0)
                if (used >= FREE_ARCHIVE_LIMIT) {
                    throw ArchiveLimitExceededException(
                        used = used,
                        limit = FREE_ARCHIVE_LIMIT,
                        requested = 1,
                        overflow = (used + 1 - FREE_ARCHIVE_LIMIT).coerceAtLeast(1)
                    )
                }
            }
        }
        val enriched = enrichArchiveApp(app)
        val updatedApp = normalizeForWrite(previous, enriched)
        android.util.Log.d(
            TAG,
            "insertApp pkg=${app.packageId} prevExists=${previous != null} prevFolder=${previous?.folderName} newFolder=${updatedApp.folderName}"
        )
        dao.insertApp(updatedApp.toAppEntity())
        enqueueUpsert(updatedApp)
    }

    override suspend fun deleteApp(app: ArchivedApp) {
        dao.deleteApp(app.toAppEntity())
        enqueueDelete(app.packageId)
    }

    override suspend fun deleteAppById(packageId: String) {
        dao.deleteAppById(packageId)
        enqueueDelete(packageId)
    }

    override suspend fun updateApp(app: ArchivedApp) {
        val enriched = enrichArchiveApp(app)
        val previous = dao.getAppById(app.packageId)?.toArchivedApp()
        val updatedApp = normalizeForWrite(previous, enriched)
        android.util.Log.d(
            TAG,
            "updateApp pkg=${app.packageId} prevExists=${previous != null} prevFolder=${previous?.folderName} newFolder=${updatedApp.folderName}"
        )
        dao.updateApp(updatedApp.toAppEntity())
        enqueueUpsert(updatedApp)
    }

    private fun decodeIconBase64(base64: String?): ByteArray? {
        if (base64.isNullOrBlank()) return null

        val sanitized = base64.trim()
            .replace("\n", "")
            .replace("\r", "")
        val candidates = listOf(base64, sanitized).distinct()
        val flags = intArrayOf(
            Base64.DEFAULT,
            Base64.NO_WRAP,
            Base64.URL_SAFE,
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        for (candidate in candidates) {
            for (flag in flags) {
                val decoded = runCatching { Base64.decode(candidate, flag) }.getOrNull() ?: continue
                if (decoded.isNotEmpty() && isDecodableBitmap(decoded)) {
                    return decoded
                }
            }
        }
        return null
    }

    private fun encodeBitmap(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int
    ): ByteArray? {
        val stream = java.io.ByteArrayOutputStream()
        val ok = bitmap.compress(format, quality, stream)
        if (!ok) return null
        val bytes = stream.toByteArray()
        return bytes.takeIf { it.isNotEmpty() && isDecodableBitmap(it) }
    }

    private fun isDecodableBitmap(bytes: ByteArray): Boolean {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null
    }
}
