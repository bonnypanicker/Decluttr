package com.tool.decluttr.domain.usecase

import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.repository.AppRepository
import javax.inject.Inject

class CaptureAppUseCase @Inject constructor(
    private val extractPackageIdUseCase: ExtractPackageIdUseCase,
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val repository: AppRepository
) {
    // Returns packageId if successful, null if failed
    suspend operator fun invoke(shareText: String): String? {
        val packageId = extractPackageIdUseCase(shareText) ?: return null
        
        // Check if already archived
        if (repository.getAppById(packageId) != null) return packageId

        val appDetails = getAppDetailsUseCase(packageId)
        
        val archivedApp = ArchivedApp(
            packageId = packageId,
            name = appDetails?.name ?: packageId, // Fallback to package ID if not installed
            iconBytes = appDetails?.iconBytes,
            category = appDetails?.category,
            archivedSizeBytes = appDetails?.archivedSizeBytes
        )
        
        repository.insertApp(archivedApp)
        return packageId
    }
}
