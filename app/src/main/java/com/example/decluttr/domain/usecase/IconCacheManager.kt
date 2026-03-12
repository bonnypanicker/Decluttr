package com.example.decluttr.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconCacheManager @Inject constructor() {
    private val cache = mutableMapOf<String, ByteArray?>()

    fun get(packageId: String): ByteArray? = cache[packageId]

    fun put(packageId: String, iconBytes: ByteArray?) {
        cache[packageId] = iconBytes
    }

    fun has(packageId: String): Boolean = cache.containsKey(packageId)

    fun clear() = cache.clear()
}
