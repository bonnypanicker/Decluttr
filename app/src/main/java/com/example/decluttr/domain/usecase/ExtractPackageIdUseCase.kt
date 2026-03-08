package com.example.decluttr.domain.usecase

import javax.inject.Inject

class ExtractPackageIdUseCase @Inject constructor() {
    operator fun invoke(text: String): String? {
        val playStoreRegex = ".*play\\.google\\.com/store/apps/details\\?id=([^&\\s]+).*".toRegex()
        val matchResult = playStoreRegex.find(text)
        return matchResult?.groupValues?.get(1)
    }
}
