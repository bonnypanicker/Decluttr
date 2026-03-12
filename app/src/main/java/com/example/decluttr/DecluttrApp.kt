package com.example.decluttr

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.decluttr.presentation.util.AppIconFetcher
import com.example.decluttr.presentation.util.AppIconModel
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DecluttrApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AppIconFetcher.Factory(this@DecluttrApp))
            }
            .crossfade(true)
            .build()
    }
}
