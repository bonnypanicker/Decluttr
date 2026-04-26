package com.tool.decluttr.receiver

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
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
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: run {
                finish(); return
            }

            val packageId = PlayStoreScraper.extractPackageId(sharedText) ?: run {
                Toast.makeText(this, "Not a valid Play Store link", Toast.LENGTH_SHORT).show()
                finish(); return
            }

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
        } else {
            finish()
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

        if (info.iconUrl.isNotBlank()) {
            iconView.load(info.iconUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_app_placeholder)
                error(R.drawable.ic_app_placeholder)
            }
        } else {
            // App is installed — load icon from PackageManager
            runCatching {
                iconView.setImageDrawable(packageManager.getApplicationIcon(info.packageId))
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
}
