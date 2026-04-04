package com.tool.decluttr.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        private const val FIRESTORE_ICON_MAX_DIM = 128
        private const val FIRESTORE_ICON_MAX_BYTES = 16 * 1024
        private val FIRESTORE_ICON_QUALITIES = intArrayOf(82, 74, 66, 58)
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
                when {
                    localApp == null -> dao.insertApp(remoteApp.toAppEntity())
                    remoteApp.lastModified >= localApp.lastModified -> dao.insertApp(remoteApp.toAppEntity())
                    else -> syncToFirestore(localApp)
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
        return ArchivedApp(
            packageId = id,
            name = getString("name") ?: id,
            isPlayStoreInstalled = getBoolean("isPlayStoreInstalled") ?: true,
            category = getString("category"),
            tags = parseTags(get("tags")),
            notes = getString("notes"),
            iconBytes = getString("iconBase64")?.let { Base64.decode(it, Base64.DEFAULT) },
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
        val compressedBytes = compressBitmapAdaptiveForFirestore(scaledBitmap)
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

    private fun compressBitmapAdaptiveForFirestore(bitmap: Bitmap): ByteArray? {
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        var best: ByteArray? = null
        for (quality in FIRESTORE_ICON_QUALITIES) {
            val stream = java.io.ByteArrayOutputStream()
            val ok = bitmap.compress(format, quality, stream)
            if (!ok) continue
            val bytes = stream.toByteArray()
            if (bytes.isEmpty()) continue
            if (best == null || bytes.size < best.size) best = bytes
            if (bytes.size <= FIRESTORE_ICON_MAX_BYTES) return bytes
        }
        return best
    }

    private fun normalizeForWrite(previous: ArchivedApp?, app: ArchivedApp): ArchivedApp {
        val contentChanged = previous == null ||
            previous.name != app.name ||
            previous.isPlayStoreInstalled != app.isPlayStoreInstalled ||
            previous.category != app.category ||
            previous.tags != app.tags ||
            previous.notes != app.notes ||
            previous.lastTimeUsed != app.lastTimeUsed ||
            previous.folderName != app.folderName ||
            !previous.iconBytes.contentEquals(app.iconBytes)
        android.util.Log.v(
            TAG,
            "normalizeForWrite pkg=${app.packageId} changed=$contentChanged prevFolder=${previous?.folderName} newFolder=${app.folderName}"
        )
        return if (contentChanged) {
            app.copy(lastModified = System.currentTimeMillis())
        } else {
            app
        }
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
        val previous = dao.getAppById(app.packageId)?.toArchivedApp()
        val updatedApp = normalizeForWrite(previous, app)
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
        val previous = dao.getAppById(app.packageId)?.toArchivedApp()
        val updatedApp = normalizeForWrite(previous, app)
        android.util.Log.d(
            TAG,
            "updateApp pkg=${app.packageId} prevExists=${previous != null} prevFolder=${previous?.folderName} newFolder=${updatedApp.folderName}"
        )
        dao.updateApp(updatedApp.toAppEntity())
        syncToFirestore(updatedApp)
    }
}
