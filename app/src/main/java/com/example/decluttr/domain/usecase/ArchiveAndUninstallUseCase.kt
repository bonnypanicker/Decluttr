package com.example.decluttr.domain.usecase

import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.domain.repository.AppRepository
import javax.inject.Inject

class ArchiveAndUninstallUseCase @Inject constructor(
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val repository: AppRepository
) {
    suspend operator fun invoke(packageIds: List<String>, appInfoMap: Map<String, Pair<Boolean, Long>> = emptyMap()) {
        for (packageId in packageIds) {
            // Re-fetch details just to be safe
            val details = getAppDetailsUseCase(packageId)
            
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
        }
    }
}
