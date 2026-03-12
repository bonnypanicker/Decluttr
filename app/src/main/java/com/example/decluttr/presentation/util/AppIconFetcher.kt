package com.example.decluttr.presentation.util

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.example.decluttr.domain.usecase.IconCacheManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

data class AppIconModel(val packageName: String)

class AppIconFetcher(
    private val context: Context,
    private val data: AppIconModel,
    private val iconCacheManager: IconCacheManager? = null
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // 1. Try Memory Cache first (if available)
        if (iconCacheManager != null) {
            val cachedBytes = iconCacheManager.get(data.packageName)
            if (cachedBytes != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(cachedBytes, 0, cachedBytes.size)
                if (bitmap != null) {
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.MEMORY_CACHE
                    )
                }
            }
        }

        return try {
            // 2. Efficient Fetch via LauncherApps (avoids full APK parse sometimes)
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activityList = launcherApps.getActivityList(data.packageName, android.os.Process.myUserHandle())
            
            val icon = if (activityList.isNotEmpty()) {
                // Get the icon for the main activity (usually the launcher icon)
                // getBadgedIcon is often more efficient and correct for current theme
                val drawable = activityList[0].getBadgedIcon(0)
                drawable
            } else {
                // Fallback for apps without launcher activity
                context.packageManager.getApplicationIcon(data.packageName)
            }

            // 3. Populate Cache (async-friendly but here we do it inline for simplicity)
            if (iconCacheManager != null && icon is BitmapDrawable) {
                val bitmap = icon.bitmap
                val stream = ByteArrayOutputStream()
                // Compress significantly to reduce memory pressure
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                iconCacheManager.put(data.packageName, stream.toByteArray())
            }

            DrawableResult(
                drawable = icon,
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            // Handle NameNotFound or other system errors
            null
        }
    }

    class Factory(
        private val context: Context,
        private val iconCacheManager: IconCacheManager? = null
    ) : Fetcher.Factory<AppIconModel> {
        override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(context, data, iconCacheManager)
        }
    }
}
