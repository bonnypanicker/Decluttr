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
        val isOsArchived: Boolean = false
    )

    suspend operator fun invoke(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        
        // Get all installed packages
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        // Filter out system apps, keep user installed apps
        val userApps = packages.filter { appInfo ->
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || 
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
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

            InstalledAppInfo(
                packageId = packageId,
                name = details?.name ?: packageManager.getApplicationLabel(appInfo).toString(),
                iconBytes = details?.iconBytes,
                apkSizeBytes = apkSize,
                isOsArchived = isArchived
            )
        }.sortedBy { it.name.lowercase() }
    }
}
