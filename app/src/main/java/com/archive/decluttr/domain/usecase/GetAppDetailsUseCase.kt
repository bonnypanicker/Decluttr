package com.archive.decluttr.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class GetAppDetailsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class AppDetailsResult(
        val name: String,
        val iconBytes: ByteArray?,
        val category: String?
    )

    operator fun invoke(packageId: String, fetchIcon: Boolean = false): AppDetailsResult? {
        val packageManager = context.packageManager
        return try {
            val appInfo = packageManager.getApplicationInfo(packageId, PackageManager.GET_META_DATA)
            val name = packageManager.getApplicationLabel(appInfo).toString()
            
            val iconBytes = if (fetchIcon) {
                val tempIcon = packageManager.getApplicationIcon(appInfo)
                drawableToByteArray(tempIcon)
            } else {
                null
            }
            
            val category = getCategoryName(appInfo.category)
            
            AppDetailsResult(name, iconBytes, category)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getCategoryName(categoryNumber: Int): String? {
        return when (categoryNumber) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Photography"
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News & Magazines"
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Maps & Navigation"
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
            else -> null
        }
    }

    private fun drawableToByteArray(drawable: Drawable): ByteArray? {
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }

        val stream = ByteArrayOutputStream()
        // Compress as PNG with 100% quality or WEBP? PNG is safer. 
        // 50KB constraint means maybe lower quality or WEBP, but let's stick to standard size icon
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        scaledBitmap.compress(format, 60, stream)
        val byteArray = stream.toByteArray()
        
        return byteArray.takeIf { it.isNotEmpty() }
    }
}
