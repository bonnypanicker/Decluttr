package com.example.decluttr

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.decluttr.domain.usecase.IconCacheManager
import com.example.decluttr.presentation.util.AppIconFetcher
import com.example.decluttr.presentation.util.AppIconKeyer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DecluttrApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var iconCacheManager: IconCacheManager

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AppIconFetcher.Factory(this@DecluttrApp, iconCacheManager))
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
