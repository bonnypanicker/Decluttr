package com.tool.decluttr.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class GetAppDetailsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ARCHIVE_ICON_SIZE_PX = 144
    }

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

        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            ARCHIVE_ICON_SIZE_PX,
            ARCHIVE_ICON_SIZE_PX,
            true
        )
        val compressed = encodeBitmap(scaledBitmap, Bitmap.CompressFormat.PNG, 100)
            ?: encodeBitmap(scaledBitmap, Bitmap.CompressFormat.JPEG, 92)
        if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }
        return compressed
    }

    private fun encodeBitmap(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int
    ): ByteArray? {
        val stream = ByteArrayOutputStream()
        if (!bitmap.compress(format, quality, stream)) return null
        val bytes = stream.toByteArray()
        if (bytes.isEmpty()) return null
        return bytes.takeIf { BitmapFactory.decodeByteArray(it, 0, it.size) != null }
    }
}
