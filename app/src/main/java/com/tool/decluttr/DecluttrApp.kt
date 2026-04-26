package com.tool.decluttr

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import android.webkit.WebView
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tool.decluttr.data.local.DecluttrDatabase
import com.tool.decluttr.domain.repository.AppRepository
import com.tool.decluttr.domain.usecase.IconCacheManager
import com.tool.decluttr.presentation.util.AppIconFetcher
import com.tool.decluttr.presentation.util.AppIconKeyer
import com.tool.decluttr.presentation.util.ThemePreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
    
    @Inject
    lateinit var database: DecluttrDatabase

    override fun onCreate() {
        startLogWriter()
        appendStartupLog(this, "Application onCreate start")
        // Apply theme immediately to prevent light mode flashes if system is light
        ThemePreferences.applyTheme(this)
        
        // Fix 9: Pre-open Room DB off-thread so first query doesn't pay open cost
        logScope.launch(Dispatchers.IO) {
            runCatching { database.openHelper.readableDatabase }
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            appendStartupLog(this, "Uncaught exception on ${thread.name}", throwable)
            recordExceptionIfAvailable(this, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        super.onCreate()
        
        // Fix 4: Pre-warm WebView during splash if user is new
        Looper.myQueue().addIdleHandler(object : MessageQueue.IdleHandler {
            override fun queueIdle(): Boolean {
                val isNewUser = if (FirebaseApp.getApps(this@DecluttrApp).isNotEmpty()) {
                    runCatching { FirebaseAuth.getInstance().currentUser == null }.getOrDefault(true)
                } else {
                    true
                }
                if (isNewUser) {
                    appendStartupLog(this@DecluttrApp, "Pre-warming WebView on Idle")
                    runCatching { WebView(this@DecluttrApp) }
                } else {
                    runCatching {
                        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
                        FirebaseAnalytics.getInstance(this@DecluttrApp).setAnalyticsCollectionEnabled(true)
                    }
                }
                return false // Run only once
            }
        })
        
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
        
        private val logChannel = Channel<Pair<Context, String>>(Channel.UNLIMITED)
        private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun startLogWriter() {
            logScope.launch {
                for (log in logChannel) {
                    runCatching {
                        File(log.first.cacheDir, STARTUP_LOG_FILE).appendText(log.second)
                    }
                }
            }
        }

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
            logChannel.trySend(context.applicationContext to body)
            
            if (throwable == null) {
                Log.d(STARTUP_LOG_TAG, message)
            } else {
                Log.e(STARTUP_LOG_TAG, message, throwable)
            }
        }

        fun recordExceptionIfAvailable(context: Context, throwable: Throwable) {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                runCatching { FirebaseCrashlytics.getInstance().recordException(throwable) }
            }
        }

        private fun Throwable.stackTraceToStringSafe(): String {
            return StringWriter().also { writer ->
                printStackTrace(PrintWriter(writer))
            }.toString()
        }
    }
}
