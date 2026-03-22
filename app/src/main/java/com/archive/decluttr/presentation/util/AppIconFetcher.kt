package com.archive.decluttr.presentation.util

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.archive.decluttr.domain.usecase.IconCacheManager
import java.io.ByteArrayOutputStream

import com.archive.decluttr.domain.repository.AppRepository

data class AppIconModel(val packageName: String)

class AppIconFetcher(
    private val context: Context,
    private val data: AppIconModel,
    private val iconCacheManager: IconCacheManager? = null,
    private val appRepository: AppRepository? = null
) : Fetcher {
    private val cacheSizePx = 128

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

        // 1.5. Try Database Cache if it's an archived/uninstalled app
        if (appRepository != null) {
            val app = appRepository.getAppById(data.packageName)
            if (app?.iconBytes != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
                if (bitmap != null) {
                    // Update memory cache too
                    iconCacheManager?.put(data.packageName, app.iconBytes)
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
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

            if (iconCacheManager != null) {
                val bitmap = drawableToBitmap(icon, cacheSizePx, cacheSizePx)
                val stream = ByteArrayOutputStream()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 80, stream)
                }
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

    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    class Factory(
        private val context: Context,
        private val iconCacheManager: IconCacheManager? = null,
        private val appRepository: AppRepository? = null
    ) : Fetcher.Factory<AppIconModel> {
        override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(context, data, iconCacheManager, appRepository)
        }
    }
}
