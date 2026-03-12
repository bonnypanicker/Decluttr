package com.example.decluttr

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.decluttr.domain.usecase.IconCacheManager
import com.example.decluttr.presentation.util.AppIconFetcher
import com.example.decluttr.presentation.util.AppIconModel
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DecluttrApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var iconCacheManager: IconCacheManager

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                // Pass the injected cache manager to the fetcher factory
                add(AppIconFetcher.Factory(this@DecluttrApp, iconCacheManager))
            }
            .crossfade(true)
            // Optional: Limit memory cache size for Coil itself to avoid OOM with large lists
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
    }
}
