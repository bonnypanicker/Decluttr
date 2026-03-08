package com.example.decluttr.domain.usecase

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

    suspend operator fun invoke(): List<GetInstalledAppsUseCase.InstalledAppInfo> = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext emptyList()

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
            return@withContext emptyList()
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

        // Get all installed user apps
        val userApps = getInstalledAppsUseCase()

        // Filter out apps that have been used within the threshold
        userApps.filter { app ->
            val lastUsed = lastUsedMap[app.packageId] ?: 0L
            // If lastUsed is 0, it means the app wasn't used at all in the queried interval
            // Or it was used before the interval. So it's unused.
            // If it was used within the interval (lastUsed > startTime), we exclude it
            lastUsed <= startTime
        }
    }
}
