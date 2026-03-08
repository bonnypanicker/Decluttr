package com.example.decluttr.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getAppDetailsUseCase: GetAppDetailsUseCase
) {
    data class InstalledAppInfo(
        val packageId: String,
        val name: String,
        val iconBytes: ByteArray?,
        val apkSizeBytes: Long,
        val isOsArchived: Boolean = false,
        val isPlayStoreInstalled: Boolean = true
    )

    suspend operator fun invoke(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        
        // Get all installed packages
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        // Filter out system apps, keep strictly user installed apps
        val userApps = packages.filter { appInfo ->
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isWebApk = appInfo.packageName.startsWith("org.chromium.webapk") || 
                           appInfo.packageName.startsWith("com.android.chrome.webapk") || 
                           appInfo.packageName.startsWith("com.google.android.apps.chrome.webapk")
            !isSystemApp && !isWebApk
        }

        userApps.mapNotNull { appInfo ->
            val packageId = appInfo.packageName
            // Exclude our own app
            if (packageId == context.packageName) return@mapNotNull null
            
            val details = getAppDetailsUseCase(packageId)
            val apkSize = try {
                val sourceDir = appInfo.sourceDir
                java.io.File(sourceDir).length()
            } catch (e: Exception) {
                0L
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
                iconBytes = details?.iconBytes,
                apkSizeBytes = apkSize,
                isOsArchived = isArchived,
                isPlayStoreInstalled = isPlayStore
            )
        }.sortedBy { it.name.lowercase() }
    }
}
