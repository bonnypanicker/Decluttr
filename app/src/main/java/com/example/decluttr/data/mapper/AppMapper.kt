package com.example.decluttr.data.mapper

import com.example.decluttr.data.local.entity.AppEntity
import com.example.decluttr.domain.model.ArchivedApp

fun AppEntity.toArchivedApp(): ArchivedApp {
    return ArchivedApp(
        packageId = packageId,
        name = name,
        isPlayStoreInstalled = isPlayStoreInstalled,
        category = category,
        tags = if (tags.isNotBlank()) tags.split(",") else emptyList(),
        notes = notes,
        iconBytes = iconBytes,
        archivedAt = archivedAt,
        lastTimeUsed = lastTimeUsed
    )
}

fun ArchivedApp.toAppEntity(): AppEntity {
    return AppEntity(
        packageId = packageId,
        name = name,
        category = category,
        tags = tags.joinToString(","),
        notes = notes,
        iconBytes = iconBytes,
        isPlayStoreInstalled = isPlayStoreInstalled,
        archivedAt = archivedAt,
        lastTimeUsed = lastTimeUsed
    )
}
