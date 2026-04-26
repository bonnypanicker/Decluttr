package com.tool.decluttr.receiver

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import com.tool.decluttr.R
import com.tool.decluttr.data.remote.PlayStoreAppInfo
import com.tool.decluttr.data.remote.PlayStoreScraper
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.presentation.screens.wishlist.WishlistViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    @Inject lateinit var scraper: PlayStoreScraper
    private val viewModel: WishlistViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: run { finish(); return }
                // Reject non-Play Store text immediately — don't show Decluttr for WhatsApp messages etc.
                if (!isPlayStoreUrl(sharedText)) {
                    finish()
                    return
                }
                val packageId = PlayStoreScraper.extractPackageId(sharedText) ?: run { finish(); return }
                processPackageId(packageId, sharedText)
            }
            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: run { finish(); return }
                val packageId = PlayStoreScraper.extractPackageId(url) ?: run { finish(); return }
                processPackageId(packageId, url)
            }
            else -> finish()
        }
    }

    private fun isPlayStoreUrl(text: String): Boolean {
        return text.contains("play.google.com/store/apps/details") ||
               text.contains("market://details") ||
               text.contains("market://search")
    }

    private fun processPackageId(packageId: String, sharedText: String) {
        val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageId"

        lifecycleScope.launch {
            // Already on wishlist? Skip
            if (viewModel.exists(packageId)) {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Already in your wishlist",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }

            // 1. Try local PackageManager first — instant, no network
            val info = getLocalAppInfo(packageId)
                ?: scraper.fetch(packageId)   // 2. Fall back to Play Store HTML

            if (info != null) {
                showConfirmDialog(info, playStoreUrl)
            } else {
                // Offline and not installed — save with package ID only
                viewModel.add(
                    WishlistApp(
                        packageId    = packageId,
                        name         = packageId,
                        iconUrl      = "",
                        description  = "",
                        playStoreUrl = playStoreUrl,
                    )
                )
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Saved to wishlist (details unavailable offline)",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun getLocalAppInfo(packageId: String): PlayStoreAppInfo? = try {
        val appInfo = packageManager.getApplicationInfo(packageId, 0)
        PlayStoreAppInfo(
            packageId   = packageId,
            name        = packageManager.getApplicationLabel(appInfo).toString(),
            iconUrl     = "",          // load from PackageManager directly in the dialog
            description = "",
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun showConfirmDialog(info: PlayStoreAppInfo, playStoreUrl: String) {
        // Inflate a simple dialog view — create res/layout/dialog_wishlist_confirm.xml
        val view = layoutInflater.inflate(R.layout.dialog_wishlist_confirm, null)

        val iconView  = view.findViewById<android.widget.ImageView>(R.id.iv_app_icon)
        val nameView  = view.findViewById<android.widget.TextView>(R.id.tv_app_name)
        val descView  = view.findViewById<android.widget.TextView>(R.id.tv_app_desc)

        nameView.text = info.name
        descView.text = info.description.ifBlank { "No description available" }

        val placeholderColor = generateColorFromString(info.name)
        val placeholder = ColorDrawable(placeholderColor)

        if (info.iconUrl.isNotBlank()) {
            iconView.load(info.iconUrl) {
                crossfade(true)
                placeholder(placeholder)
                error(placeholder)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
            }
        } else {
            // App is installed — load icon from PackageManager
            runCatching {
                iconView.setImageDrawable(packageManager.getApplicationIcon(info.packageId))
            }.onFailure {
                iconView.setImageDrawable(placeholder)
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Add to Wishlist") { _, _ ->
                lifecycleScope.launch {
                    viewModel.add(
                        WishlistApp(
                            packageId    = info.packageId,
                            name         = info.name,
                            iconUrl      = info.iconUrl,
                            description  = info.description,
                            playStoreUrl = playStoreUrl,
                            category     = info.category
                        )
                    )
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "${info.name} added to wishlist",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun generateColorFromString(input: String): Int {
        val hash = input.fold(0) { acc, c -> acc * 31 + c.code }
        val hue = (Math.abs(hash) % 360).toFloat()
        return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.55f))
    }
}
