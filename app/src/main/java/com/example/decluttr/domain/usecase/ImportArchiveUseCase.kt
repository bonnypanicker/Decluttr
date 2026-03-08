package com.example.decluttr.domain.usecase

import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.domain.repository.AppRepository
import org.json.JSONObject
import javax.inject.Inject

class ImportArchiveUseCase @Inject constructor(
    private val repository: AppRepository
) {
    suspend operator fun invoke(jsonString: String): Boolean {
        return try {
            val rootObject = JSONObject(jsonString)
            val jsonArray = rootObject.getJSONArray("apps")

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val app = ArchivedApp(
                    packageId = obj.getString("packageId"),
                    name = obj.getString("name"),
                    category = obj.optString("category").takeIf { it.isNotBlank() },
                    tags = obj.optString("tags").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    notes = obj.optString("notes").takeIf { it.isNotBlank() },
                    archivedAt = obj.optLong("archivedAt", System.currentTimeMillis()),
                    iconBytes = null // Icons will be downloaded or re-fetched if installed, or left null
                )
                repository.insertApp(app)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
