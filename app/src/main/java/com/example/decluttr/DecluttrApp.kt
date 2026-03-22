package com.example.decluttr

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.decluttr.domain.usecase.IconCacheManager
import com.example.decluttr.presentation.util.AppIconFetcher
import com.example.decluttr.presentation.util.AppIconKeyer
import com.example.decluttr.domain.repository.AppRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DecluttrApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var iconCacheManager: IconCacheManager

    @Inject
    lateinit var appRepository: AppRepository

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AppIconFetcher.Factory(this@DecluttrApp, iconCacheManager, appRepository))
                add(AppIconKeyer())
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
    }
}
