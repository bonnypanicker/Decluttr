package com.tool.decluttr

import android.app.Application
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tool.decluttr.domain.usecase.IconCacheManager
import com.tool.decluttr.presentation.util.AppIconFetcher
import com.tool.decluttr.presentation.util.AppIconKeyer
import com.tool.decluttr.domain.repository.AppRepository
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class DecluttrApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var iconCacheManager: IconCacheManager

    @Inject
    lateinit var appRepository: AppRepository

    override fun onCreate() {
        appendStartupLog(this, "Application onCreate start")
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            appendStartupLog(this, "Uncaught exception on ${thread.name}", throwable)
            FirebaseCrashlytics.getInstance().recordException(throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        super.onCreate()
        appendStartupLog(this, "Application onCreate complete")
    }

    override fun newImageLoader(): ImageLoader {
        appendStartupLog(this, "newImageLoader invoked")
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

    companion object {
        private const val STARTUP_LOG_TAG = "DecluttrStartup"
        private const val STARTUP_LOG_FILE = "startup_debug.log"

        fun appendStartupLog(context: Context, message: String, throwable: Throwable? = null) {
            val body = buildString {
                append(System.currentTimeMillis())
                append(" | ")
                append(message)
                if (throwable != null) {
                    append('\n')
                    append(throwable.stackTraceToStringSafe())
                }
                append('\n')
            }
            runCatching {
                File(context.cacheDir, STARTUP_LOG_FILE).appendText(body)
            }
            if (throwable == null) {
                Log.d(STARTUP_LOG_TAG, message)
            } else {
                Log.e(STARTUP_LOG_TAG, message, throwable)
            }
        }

        private fun Throwable.stackTraceToStringSafe(): String {
            return StringWriter().also { writer ->
                printStackTrace(PrintWriter(writer))
            }.toString()
        }
    }
}
