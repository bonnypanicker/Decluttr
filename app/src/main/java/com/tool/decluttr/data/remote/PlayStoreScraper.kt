package com.tool.decluttr.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

data class PlayStoreAppInfo(
    val packageId: String,
    val name: String,
    val iconUrl: String,
    val description: String,
    val category: String? = null
)

@Singleton
class PlayStoreScraper @Inject constructor() {

    suspend fun fetch(packageId: String): PlayStoreAppInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = Jsoup
                    .connect("https://play.google.com/store/apps/details?id=$packageId&hl=en")
                    .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
                    .timeout(8_000)
                    .get()

                // og:title arrives as "App Name - Tagline", strip the tagline part
                val rawTitle = doc.select("meta[property=og:title]").attr("content")
                val name = rawTitle.substringBefore(" - ").trim().ifBlank { rawTitle }

                // og:image is the app icon on play-lh.googleusercontent.com
                // Removed size conversion/compression regex per user request
                val iconUrl = doc.select("meta[property=og:image]").attr("content")

                val description = doc
                    .select("meta[property=og:description]")
                    .attr("content")

                // Category sits in an itemprop="genre" element
                val category = doc.select("[itemprop=genre]").first()?.text()
                    ?: doc.select("a[href*='/store/apps/category/']").first()?.text()

                PlayStoreAppInfo(packageId, name, iconUrl, description, category)
            }
            .onFailure { it.printStackTrace() }
            .getOrNull()
        }

    companion object {
        /**
         * Pulls the package ID out of any Play Store share URL.
         * Handles:
         *   Check out this app! https://play.google.com/store/apps/details?id=com.example.app
         *   https://market.android.com/details?id=com.example.app
         *   market://details?id=com.example.app
         */
        fun extractPackageId(sharedText: String): String? {
            val regex = Regex("""id=([a-zA-Z0-9_.]+)""")
            val match = regex.find(sharedText)
            return match?.groupValues?.get(1)
        }
    }
}
