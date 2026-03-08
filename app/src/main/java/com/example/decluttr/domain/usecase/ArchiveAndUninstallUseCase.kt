package com.example.decluttr.domain.usecase

import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.domain.repository.AppRepository
import javax.inject.Inject

class ArchiveAndUninstallUseCase @Inject constructor(
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val uninstallAppUseCase: UninstallAppUseCase,
    private val repository: AppRepository
) {
    suspend operator fun invoke(packageIds: List<String>) {
        for (packageId in packageIds) {
            // Re-fetch details just to be safe
            val details = getAppDetailsUseCase(packageId)
            
            val app = ArchivedApp(
                packageId = packageId,
                name = details?.name ?: packageId,
                iconBytes = details?.iconBytes
            )
            
            // Save to DB
            repository.insertApp(app)
            
            // Trigger OS Uninstall (async, requires user confirm for each)
            uninstallAppUseCase(packageId)
        }
    }
}
