package com.archive.decluttr.domain.usecase

import com.archive.decluttr.domain.repository.AppRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ExportArchiveUseCase @Inject constructor(
    private val repository: AppRepository
) {
    suspend operator fun invoke(): String {
        val apps = repository.getAllArchivedApps().first()
        val jsonArray = JSONArray()

        apps.forEach { app ->
            val jsonObject = JSONObject().apply {
                put("packageId", app.packageId)
                put("name", app.name)
                put("category", app.category ?: "")
                put("tags", app.tags.joinToString(","))
                put("notes", app.notes ?: "")
                put("archivedAt", app.archivedAt)
                // We typically skip exporting iconBytes in JSON due to size,
                // but we could Base64 encode it if necessary.
                // For a lightweight JSON export, we skip the icon.
            }
            jsonArray.put(jsonObject)
        }

        val rootObject = JSONObject().apply {
            put("version", 1)
            put("apps", jsonArray)
        }

        return rootObject.toString(2)
    }
}
