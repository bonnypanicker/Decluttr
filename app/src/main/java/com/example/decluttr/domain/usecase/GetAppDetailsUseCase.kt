package com.example.decluttr.domain.usecase

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
        val iconBytes: ByteArray?
    )

    operator fun invoke(packageId: String): AppDetailsResult? {
        val packageManager = context.packageManager
        return try {
            val appInfo = packageManager.getApplicationInfo(packageId, PackageManager.GET_META_DATA)
            val name = packageManager.getApplicationLabel(appInfo).toString()
            val tempIcon = packageManager.getApplicationIcon(appInfo)
            
            val iconBytes = drawableToByteArray(tempIcon)
            
            AppDetailsResult(name, iconBytes)
        } catch (e: PackageManager.NameNotFoundException) {
            null
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
        scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
        val byteArray = stream.toByteArray()
        
        return byteArray.takeIf { it.isNotEmpty() }
    }
}
