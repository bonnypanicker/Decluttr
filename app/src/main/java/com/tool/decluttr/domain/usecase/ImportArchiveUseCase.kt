package com.tool.decluttr.domain.usecase

import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.model.ArchiveLimitExceededException
import com.tool.decluttr.domain.repository.AppRepository
import org.json.JSONObject
import javax.inject.Inject

class ImportArchiveUseCase @Inject constructor(
    private val repository: AppRepository
) {
    sealed interface Result {
        data class Success(val importedCount: Int) : Result
        data class LimitReached(
            val used: Int,
            val limit: Int
        ) : Result
        data object InvalidFormat : Result
    }

    suspend operator fun invoke(jsonString: String): Result {
        return try {
            val rootObject = JSONObject(jsonString)
            val jsonArray = rootObject.getJSONArray("apps")
            var importedCount = 0

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val app = ArchivedApp(
                    packageId = obj.getString("packageId"),
                    name = obj.getString("name"),
                    category = obj.optString("category").takeIf { it.isNotBlank() },
                    tags = obj.optString("tags").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    notes = obj.optString("notes").takeIf { it.isNotBlank() },
                    archivedAt = obj.optLong("archivedAt", System.currentTimeMillis()),
                    archivedSizeBytes = obj.optLong("archivedSizeBytes", 0L).takeIf { it > 0L },
                    isPlayStoreInstalled = obj.optBoolean("isPlayStoreInstalled", true),
                    lastTimeUsed = obj.optLong("lastTimeUsed", 0L),
                    folderName = obj.optString("folderName").takeIf { it.isNotBlank() },
                    lastModified = obj.optLong("lastModified", System.currentTimeMillis()),
                    iconBytes = null // Icons will be downloaded or re-fetched if installed, or left null
                )
                repository.insertApp(app)
                importedCount++
            }
            Result.Success(importedCount)
        } catch (limit: ArchiveLimitExceededException) {
            Result.LimitReached(
                used = limit.used,
                limit = limit.limit
            )
        } catch (e: Exception) {
            Result.InvalidFormat
        }
    }
}
