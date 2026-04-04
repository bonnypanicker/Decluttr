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
import com.tool.decluttr.domain.repository.AppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Provider

class AppRepositoryImpl(
    @ApplicationContext private val context: Context,
    private val dao: AppDao,
    private val authProvider: Provider<FirebaseAuth>,
    private val firestoreProvider: Provider<FirebaseFirestore>
) : AppRepository {
    companion object {
        private const val TAG = "DecluttrDragDbgRepo"
        private const val LOCAL_ICON_DIM = 144
        private const val FIRESTORE_ICON_MAX_DIM = 128
        private const val FIRESTORE_ICON_MAX_BYTES = 24 * 1024
        private val FIRESTORE_JPEG_QUALITIES = intArrayOf(92, 84, 76, 68)
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        firebaseAuthOrNull()?.addAuthStateListener { firebaseAuth ->
            scope.launch {
                if (firebaseAuth.currentUser != null) {
                    syncFromFirestore()
                } else {
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
                    else -> syncToFirestore(localApp)
                }
                if (usingRemoteAsSource && shouldHealRemote) {
                    syncToFirestore(enrichedRemoteApp)
                }
            }

            localApps.values
                .map { it.toArchivedApp() }
                .filter { localApp -> remoteApps.none { it.packageId == localApp.packageId } }
                .forEach { localOnlyApp ->
                    syncToFirestore(localOnlyApp)
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
            category = getString("category"),
            tags = parseTags(get("tags")),
            notes = getString("notes"),
            iconBytes = iconBytes,
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

    private fun syncToFirestore(app: ArchivedApp) {
        val auth = firebaseAuthOrNull() ?: return
        val firestore = firestoreOrNull() ?: return
        val user = auth.currentUser ?: return
        scope.launch {
            try {
                val iconBase64 = compressIconForFirestore(app.iconBytes)
                val data = mapOf(
                    "packageId" to app.packageId,
                    "name" to app.name,
                    "iconBase64" to iconBase64,
                    "category" to app.category,
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
            } catch (e: Exception) {
                recordException(e)
            }
        }
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
        val normalizedApp = app.copy(
            name = resolvedName,
            iconBytes = resolvedIconBytes
        )

        val contentChanged = previous == null ||
            previous.name != normalizedApp.name ||
            previous.isPlayStoreInstalled != normalizedApp.isPlayStoreInstalled ||
            previous.category != normalizedApp.category ||
            previous.tags != normalizedApp.tags ||
            previous.notes != normalizedApp.notes ||
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
        return remoteApp.copy(name = resolvedName, iconBytes = resolvedIconBytes)
    }

    private fun requiresRemoteHealing(remoteApp: ArchivedApp, mergedApp: ArchivedApp): Boolean {
        return remoteApp.name != mergedApp.name ||
            !sameBytes(remoteApp.iconBytes, mergedApp.iconBytes)
    }

    private fun sameBytes(a: ByteArray?, b: ByteArray?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a.contentEquals(b)
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

    private fun deleteFromFirestore(packageId: String) {
        val auth = firebaseAuthOrNull() ?: return
        val firestore = firestoreOrNull() ?: return
        val user = auth.currentUser ?: return
        scope.launch {
            try {
                firestore.collection("users").document(user.uid).collection("apps")
                    .document(packageId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                recordException(e)
            }
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

    override suspend fun insertApp(app: ArchivedApp) {
        val enriched = enrichArchiveApp(app)
        val previous = dao.getAppById(app.packageId)?.toArchivedApp()
        val updatedApp = normalizeForWrite(previous, enriched)
        android.util.Log.d(
            TAG,
            "insertApp pkg=${app.packageId} prevExists=${previous != null} prevFolder=${previous?.folderName} newFolder=${updatedApp.folderName}"
        )
        dao.insertApp(updatedApp.toAppEntity())
        syncToFirestore(updatedApp)
    }

    override suspend fun deleteApp(app: ArchivedApp) {
        dao.deleteApp(app.toAppEntity())
        deleteFromFirestore(app.packageId)
    }

    override suspend fun deleteAppById(packageId: String) {
        dao.deleteAppById(packageId)
        deleteFromFirestore(packageId)
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
        syncToFirestore(updatedApp)
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
