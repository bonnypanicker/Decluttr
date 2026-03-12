package com.example.decluttr.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAppDetailsUseCase: GetAppDetailsUseCase
) {
    @androidx.compose.runtime.Immutable
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
        val storageStatsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else null
        
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

        // Process apps in parallel batches for much faster loading
        coroutineScope {
            userApps.map { appInfo ->
                async(Dispatchers.IO) {
                    val packageId = appInfo.packageName
                    
                    // Use cached icon if available, otherwise fetch and cache
                    val details = getAppDetailsUseCase(packageId, fetchIcon = false)
                    
                    var totalSize = 0L
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && storageStatsManager != null) {
                            val stats = storageStatsManager.queryStatsForPackage(
                                StorageManager.UUID_DEFAULT,
                                packageId,
                                android.os.Process.myUserHandle()
                            )
                            totalSize = stats.appBytes + stats.dataBytes + stats.cacheBytes
                        } else {
                            totalSize = java.io.File(appInfo.sourceDir).length()
                        }
                    } catch (e: SecurityException) {
                        // Fallback if PACKAGE_USAGE_STATS is not explicitly granted
                        totalSize = try { java.io.File(appInfo.sourceDir).length() } catch (ignored: Exception) { 0L }
                    } catch (e: Exception) {
                        totalSize = try { java.io.File(appInfo.sourceDir).length() } catch (ignored: Exception) { 0L }
                    }
                    
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
                        name = details?.name ?: packageManager.getApplicationLabel(appInfo).toString(),
                        apkSizeBytes = totalSize,
                        isOsArchived = isArchived,
                        isPlayStoreInstalled = isPlayStore
                    )
                }
            }.awaitAll()
        }.sortedBy { it.name.lowercase() }
    }
}
