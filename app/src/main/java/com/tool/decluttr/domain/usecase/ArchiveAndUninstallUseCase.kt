package com.tool.decluttr.domain.usecase

import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.repository.AppRepository
import javax.inject.Inject

class ArchiveAndUninstallUseCase @Inject constructor(
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val uninstallAppUseCase: UninstallAppUseCase,
    private val repository: AppRepository
) {
    data class ArchiveSourceInfo(
        val isPlayStoreInstalled: Boolean,
        val lastTimeUsed: Long,
        val archivedSizeBytes: Long?,
        val name: String? = null,
        val category: String? = null,
        val iconBytes: ByteArray? = null
    )

    suspend operator fun invoke(
        packageIds: List<String>,
        appInfoMap: Map<String, ArchiveSourceInfo> = emptyMap(),
        performUninstall: Boolean = true
    ) {
        for (packageId in packageIds) {
            // Capture metadata only when we know we want to archive this package.
            val details = getAppDetailsUseCase(packageId, fetchIcon = true)
            
            val info = appInfoMap[packageId]
            val app = ArchivedApp(
                packageId = packageId,
                name = details?.name ?: info?.name ?: packageId,
                iconBytes = details?.iconBytes ?: info?.iconBytes,
                category = details?.category ?: info?.category,
                isPlayStoreInstalled = info?.isPlayStoreInstalled ?: true,
                lastTimeUsed = info?.lastTimeUsed ?: 0L,
                archivedSizeBytes = details?.archivedSizeBytes ?: info?.archivedSizeBytes
            )
            
            repository.insertApp(app)
            
            if (performUninstall) {
                uninstallAppUseCase(packageId)
            }
        }
    }
}
