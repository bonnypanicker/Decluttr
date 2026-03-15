package com.example.decluttr.domain.usecase

import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconCacheManager @Inject constructor() {
    private val cache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun get(packageId: String): ByteArray? = cache.get(packageId)

    fun put(packageId: String, iconBytes: ByteArray?) {
        if (iconBytes != null) {
            cache.put(packageId, iconBytes)
        }
    }

    fun has(packageId: String): Boolean = cache.get(packageId) != null

    fun clear() = cache.evictAll()
}
