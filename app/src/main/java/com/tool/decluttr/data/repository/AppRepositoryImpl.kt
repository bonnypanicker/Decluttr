package com.tool.decluttr.data.repository

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tool.decluttr.data.local.dao.AppDao
import com.tool.decluttr.data.mapper.toAppEntity
import com.tool.decluttr.data.mapper.toArchivedApp
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppRepositoryImpl(
    private val dao: AppDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AppRepository {
    
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Sync from Firestore whenever the user logs in
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                scope.launch {
                    syncFromFirestore()
                }
            } else {
                // Clear local database when signed out
                scope.launch {
                    dao.deleteAllApps()
                }
            }
        }
    }

    private suspend fun syncFromFirestore() {
        val user = auth.currentUser ?: return
        try {
            val snapshot = firestore.collection("users").document(user.uid).collection("apps").get().await()
            val apps = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("packageId") ?: return@mapNotNull null
                val name = doc.getString("name") ?: id
                val iconBase64 = doc.getString("iconBase64")
                val iconBytes = iconBase64?.let { Base64.decode(it, Base64.DEFAULT) }
                val category = doc.getString("category")
                val isPlayStoreInstalled = doc.getBoolean("isPlayStoreInstalled") ?: true
                val lastTimeUsed = doc.getLong("lastTimeUsed") ?: 0L
                val folderName = doc.getString("folderName")

                ArchivedApp(
                    packageId = id,
                    name = name,
                    iconBytes = iconBytes,
                    category = category,
                    isPlayStoreInstalled = isPlayStoreInstalled,
                    lastTimeUsed = lastTimeUsed,
                    folderName = folderName
                )
            }
            
            // Overwrite local db with firestore state
            apps.forEach { app ->
                dao.insertApp(app.toAppEntity())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncToFirestore(app: ArchivedApp) {
        val user = auth.currentUser ?: return
        scope.launch {
            try {
                val iconBase64 = app.iconBytes?.let { Base64.encodeToString(it, Base64.DEFAULT) }
                val data = mapOf(
                    "packageId" to app.packageId,
                    "name" to app.name,
                    "iconBase64" to iconBase64,
                    "category" to app.category,
                    "isPlayStoreInstalled" to app.isPlayStoreInstalled,
                    "lastTimeUsed" to app.lastTimeUsed,
                    "folderName" to app.folderName
                )
                firestore.collection("users").document(user.uid).collection("apps")
                    .document(app.packageId)
                    .set(data, SetOptions.merge())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteFromFirestore(packageId: String) {
        val user = auth.currentUser ?: return
        scope.launch {
            try {
                firestore.collection("users").document(user.uid).collection("apps")
                    .document(packageId)
                    .delete()
            } catch (e: Exception) {
                e.printStackTrace()
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
        dao.insertApp(app.toAppEntity())
        syncToFirestore(app)
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
        dao.updateApp(app.toAppEntity())
        syncToFirestore(app)
    }
}
