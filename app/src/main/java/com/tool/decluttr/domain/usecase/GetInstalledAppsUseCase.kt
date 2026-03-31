package com.tool.decluttr.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class InstalledAppInfo(
        val packageId: String,
        val name: String,
        val apkSizeBytes: Long,
        val isOsArchived: Boolean = false,
        val isPlayStoreInstalled: Boolean = true,
        val lastTimeUsed: Long = 0L // Populated later by UsageStats
    )

    suspend operator fun invoke(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        runCatching { packageManager.getInstalledPackages(0) }
        
        // Get all installed packages
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        // Filter out system apps, keep strictly user installed apps
        val userApps = packages.filter { appInfo ->
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isWebApk = appInfo.packageName.startsWith("org.chromium.webapk") || 
                           appInfo.packageName.startsWith("com.android.chrome.webapk") || 
                           appInfo.packageName.startsWith("com.google.android.apps.chrome.webapk")
            !isSystemApp && !isWebApk && appInfo.packageName != context.packageName
        }

        val processedApps = mutableListOf<InstalledAppInfo>()
        coroutineScope {
            userApps.chunked(12).forEach { appBatch ->
                val chunkResults = appBatch.map { appInfo ->
                    async(Dispatchers.IO) {
                    val packageId = appInfo.packageName
                    val apkSizeBytes = runCatching { java.io.File(appInfo.sourceDir).length() }.getOrDefault(0L)
                    
                    val isArchived = if (android.os.Build.VERSION.SDK_INT >= 35) {
                        appInfo.isArchived
                    } else {
                        false
                    }

                    val installerName = try {
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            packageManager.getInstallSourceInfo(packageId).installingPackageName
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getInstallerPackageName(packageId)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    
                    val isPlayStore = installerName == "com.android.vending"

                    InstalledAppInfo(
                        packageId = packageId,
                        name = packageManager.getApplicationLabel(appInfo).toString(),
                        apkSizeBytes = apkSizeBytes,
                        isOsArchived = isArchived,
                        isPlayStoreInstalled = isPlayStore
                    )
                    }
                }.awaitAll()
                processedApps += chunkResults
            }
        }
        processedApps.sortedBy { it.name.lowercase() }
    }
}
