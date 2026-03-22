package com.tool.decluttr.domain.usecase

import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

class GetUnusedAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) {
    // 30 days in milliseconds
    private val thresholdMs = 30L * 24 * 60 * 60 * 1000

    data class UnusedAppsResult(
        val allApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
        val unusedApps: List<GetInstalledAppsUseCase.InstalledAppInfo>
    )

    /**
     * Returns both the full installed apps list AND the unused subset.
     * This avoids fetching all installed apps twice.
     */
    suspend fun fetchAll(): UnusedAppsResult = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

        // Get all installed user apps (single fetch!)
        val userApps = getInstalledAppsUseCase()

        if (usageStatsManager == null) {
            return@withContext UnusedAppsResult(allApps = userApps, unusedApps = emptyList())
        }

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        val startTime = endTime - thresholdMs

        // Query usage stats for the last 30 days
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_MONTHLY,
            startTime,
            endTime
        )

        // If usageStatsList is empty, it probably means the app doesn't have the PACKAGE_USAGE_STATS permission
        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return@withContext UnusedAppsResult(allApps = userApps, unusedApps = emptyList())
        }

        // Map of packageName to last time used
        val lastUsedMap = mutableMapOf<String, Long>()
        for (usageStats in usageStatsList) {
            val pkg = usageStats.packageName
            val lastUsed = usageStats.lastTimeUsed
            
            val currentBest = lastUsedMap[pkg] ?: 0L
            if (lastUsed > currentBest) {
                lastUsedMap[pkg] = lastUsed
            }
        }

        // Map the last used time back into the primary app list for display purposes
        val userAppsWithTime = userApps.map { app ->
            val lastUsed = lastUsedMap[app.packageId] ?: 0L
            app.copy(lastTimeUsed = lastUsed)
        }

        // Filter out apps that have been used within the threshold
        val unused = userAppsWithTime.filter { app ->
            app.lastTimeUsed <= startTime
        }
        
        UnusedAppsResult(allApps = userAppsWithTime, unusedApps = unused)
    }

    // Kept for backward compatibility if needed elsewhere
    suspend operator fun invoke(): List<GetInstalledAppsUseCase.InstalledAppInfo> {
        return fetchAll().unusedApps
    }
}
