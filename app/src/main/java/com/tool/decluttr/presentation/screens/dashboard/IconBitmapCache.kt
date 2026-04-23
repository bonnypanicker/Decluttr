package com.tool.decluttr.presentation.screens.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.tool.decluttr.domain.model.ArchivedApp

object IconBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getOrDecode(app: ArchivedApp): Bitmap? {
        val bytes = app.iconBytes ?: return null
        var bmp = cache.get(app.packageId)
        if (bmp == null) {
            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                cache.put(app.packageId, bmp)
            }
        }
        return bmp
    }

    fun evict(packageId: String) {
        cache.remove(packageId)
    }
}
