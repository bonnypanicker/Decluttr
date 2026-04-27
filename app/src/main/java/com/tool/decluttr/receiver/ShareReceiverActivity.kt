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
import com.google.firebase.auth.FirebaseAuth
import com.tool.decluttr.MainActivity
import com.tool.decluttr.R
import com.tool.decluttr.data.remote.PlayStoreAppInfo
import com.tool.decluttr.data.remote.PlayStoreScraper
import com.tool.decluttr.domain.model.WishlistApp
import com.tool.decluttr.presentation.screens.wishlist.WishlistViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WishlistShare"
    }

    @Inject lateinit var scraper: PlayStoreScraper
    @Inject lateinit var firebaseAuth: FirebaseAuth
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
        android.util.Log.d(TAG, "handleIntent: action=${intent.action}")
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: run { finish(); return }
                android.util.Log.d(TAG, "ACTION_SEND: textLen=${sharedText.length}")
                // Reject non-Play Store text immediately so normal messages are ignored.
                if (!isPlayStoreUrl(sharedText)) {
                    android.util.Log.d(TAG, "ACTION_SEND: ignored non-playstore text")
                    finish()
                    return
                }
                val packageId = PlayStoreScraper.extractPackageId(sharedText) ?: run { finish(); return }
                android.util.Log.d(TAG, "ACTION_SEND: extracted pkg=$packageId")
                processPackageId(packageId)
            }

            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: run { finish(); return }
                val packageId = PlayStoreScraper.extractPackageId(url) ?: run { finish(); return }
                android.util.Log.d(TAG, "ACTION_VIEW: extracted pkg=$packageId")
                processPackageId(packageId)
            }

            else -> finish()
        }
    }

    private fun isPlayStoreUrl(text: String): Boolean {
        return text.contains("play.google.com/store/apps/details") ||
            text.contains("market://details") ||
            text.contains("market://search")
    }

    private fun processPackageId(packageId: String) {
        val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageId"
        android.util.Log.d(TAG, "processPackageId: pkg=$packageId")

        lifecycleScope.launch {
            if (!ensureLoggedInOrRedirect()) {
                return@launch
            }

            if (viewModel.exists(packageId)) {
                android.util.Log.d(TAG, "processPackageId: already exists pkg=$packageId")
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Already in your wishlist",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }

            // Try installed app metadata first, then Play Store scrape.
            val info = getLocalAppInfo(packageId) ?: scraper.fetch(packageId)

            if (info != null) {
                android.util.Log.d(
                    TAG,
                    "processPackageId: metadata resolved pkg=${info.packageId} name=${info.name.take(60)}"
                )
                showConfirmDialog(info, playStoreUrl)
            } else {
                runCatching {
                    withContext(NonCancellable) {
                        viewModel.add(
                            WishlistApp(
                                packageId = packageId,
                                name = packageId,
                                iconUrl = "",
                                description = "",
                                playStoreUrl = playStoreUrl,
                            )
                        )
                    }
                }.onFailure {
                    android.util.Log.e(TAG, "processPackageId: fallback add failed pkg=$packageId", it)
                }.onSuccess {
                    android.util.Log.d(TAG, "processPackageId: fallback add completed pkg=$packageId")
                }

                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Saved to wishlist (details unavailable offline)",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun getLocalAppInfo(packageId: String): PlayStoreAppInfo? = try {
        val appInfo = packageManager.getApplicationInfo(packageId, 0)
        PlayStoreAppInfo(
            packageId = packageId,
            name = packageManager.getApplicationLabel(appInfo).toString(),
            iconUrl = "",
            description = "",
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun showConfirmDialog(info: PlayStoreAppInfo, playStoreUrl: String) {
        android.util.Log.d(TAG, "showConfirmDialog: pkg=${info.packageId}")
        val view = layoutInflater.inflate(R.layout.dialog_wishlist_confirm, null)

        val iconView = view.findViewById<android.widget.ImageView>(R.id.iv_app_icon)
        val nameView = view.findViewById<android.widget.TextView>(R.id.tv_app_name)
        val descView = view.findViewById<android.widget.TextView>(R.id.tv_app_desc)

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
                    if (!ensureLoggedInOrRedirect()) {
                        return@launch
                    }
                    android.util.Log.d(TAG, "confirmAdd: start pkg=${info.packageId}")
                    runCatching {
                        withContext(NonCancellable) {
                            viewModel.add(
                                WishlistApp(
                                    packageId = info.packageId,
                                    name = info.name,
                                    iconUrl = info.iconUrl,
                                    description = info.description,
                                    playStoreUrl = playStoreUrl,
                                    category = info.category
                                )
                            )
                        }
                    }.onFailure { error ->
                        android.util.Log.e(TAG, "confirmAdd: failed pkg=${info.packageId}", error)
                        Toast.makeText(
                            this@ShareReceiverActivity,
                            "Couldn't save right now. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onSuccess {
                        android.util.Log.d(TAG, "confirmAdd: completed pkg=${info.packageId}")
                        Toast.makeText(
                            this@ShareReceiverActivity,
                            "${info.name} added to wishlist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun ensureLoggedInOrRedirect(): Boolean {
        if (firebaseAuth.currentUser != null) {
            return true
        }
        android.util.Log.d(TAG, "loginRequired: share blocked because user is logged out")
        Toast.makeText(
            this,
            "Please log in first to add to wishlist.",
            Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
        return false
    }

    private fun generateColorFromString(input: String): Int {
        val hash = input.fold(0) { acc, c -> acc * 31 + c.code }
        val hue = (Math.abs(hash) % 360).toFloat()
        return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.55f))
    }
}
