package com.tool.decluttr.domain.usecase

import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.repository.AppRepository
import javax.inject.Inject

class ArchiveAndUninstallUseCase @Inject constructor(
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val uninstallAppUseCase: UninstallAppUseCase,
    private val repository: AppRepository
) {
    suspend operator fun invoke(packageIds: List<String>, appInfoMap: Map<String, Pair<Boolean, Long>> = emptyMap()) {
        for (packageId in packageIds) {
            // Re-fetch details just to be safe, making sure to fetch the icon bytes!
            val details = getAppDetailsUseCase(packageId, fetchIcon = true)
            
            val info = appInfoMap[packageId]
            val app = ArchivedApp(
                packageId = packageId,
                name = details?.name ?: packageId,
                iconBytes = details?.iconBytes,
                category = details?.category,
                isPlayStoreInstalled = info?.first ?: true,
                lastTimeUsed = info?.second ?: 0L
            )
            
            // Save to DB
            repository.insertApp(app)
            
            // Trigger OS Uninstall (async, requires user confirm for each)
            uninstallAppUseCase(packageId)
        }
    }
}
