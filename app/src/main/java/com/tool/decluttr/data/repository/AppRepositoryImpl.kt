package com.tool.decluttr.data.repository

import android.content.Context
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
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crashlytics = FirebaseCrashlytics.getInstance()

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
            crashlytics.recordException(e)
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
                val iconBase64 = app.iconBytes?.let { Base64.encodeToString(it, Base64.DEFAULT) }
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
                crashlytics.recordException(e)
            }
        }
    }

    private fun normalizeForWrite(app: ArchivedApp): ArchivedApp {
        return app.copy(lastModified = System.currentTimeMillis())
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
                crashlytics.recordException(e)
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
        val updatedApp = normalizeForWrite(app)
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
        val updatedApp = normalizeForWrite(app)
        dao.updateApp(updatedApp.toAppEntity())
        syncToFirestore(updatedApp)
    }
}
