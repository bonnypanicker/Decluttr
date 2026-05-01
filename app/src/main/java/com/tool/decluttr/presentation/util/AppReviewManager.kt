package com.tool.decluttr.presentation.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tool.decluttr.R

object AppReviewManager {
    private const val PREFS_NAME = "decluttr_prefs"
    private const val KEY_LAUNCH_COUNT = "app_launch_count"
    private const val KEY_RATING_CARD_SHOWN = "rating_card_shown"
    private const val KEY_RATING_CARD_SELECTED_STARS = "rating_card_selected_stars"
    private const val KEY_RATING_CARD_SUBMITTED_AT = "rating_card_submitted_at"
    private const val TAG = "AppReviewManager"

    fun checkAndShowReview(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()

        val alreadyShown = prefs.getBoolean(KEY_RATING_CARD_SHOWN, false)
        Log.d(TAG, "App launch count=$launchCount shown=$alreadyShown")

        if (launchCount == 3 && !alreadyShown) {
            showRatingCard(activity)
        }
    }

    private fun showRatingCard(activity: Activity) {
        var selectedStars = 0

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_app_rating_card, null)
        val btnRate = dialogView.findViewById<MaterialButton>(R.id.btn_rating_rate_now)
        val btnLater = dialogView.findViewById<MaterialButton>(R.id.btn_rating_maybe_later)
        val selectedLabel = dialogView.findViewById<TextView>(R.id.tv_selected_rating)
        val stars = listOf(
            dialogView.findViewById<ImageButton>(R.id.star_1),
            dialogView.findViewById<ImageButton>(R.id.star_2),
            dialogView.findViewById<ImageButton>(R.id.star_3),
            dialogView.findViewById<ImageButton>(R.id.star_4),
            dialogView.findViewById<ImageButton>(R.id.star_5)
        )

        fun renderStars() {
            stars.forEachIndexed { index, star ->
                val filled = index < selectedStars
                star.setImageResource(if (filled) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                val cd = "Rate ${index + 1} star" + if (index > 0) "s" else ""
                star.contentDescription = cd
            }
            btnRate.isEnabled = selectedStars > 0
            selectedLabel.text = if (selectedStars > 0) {
                "$selectedStars/5 selected"
            } else {
                "Select a star rating"
            }
        }

        stars.forEachIndexed { index, imageButton ->
            imageButton.setOnClickListener {
                selectedStars = index + 1
                renderStars()
            }
        }

        renderStars()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        fun markPromptShown() {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RATING_CARD_SHOWN, true)
                .apply()
        }

        btnLater.setOnClickListener {
            markPromptShown()
            dialog.dismiss()
        }

        btnRate.setOnClickListener {
            val now = System.currentTimeMillis()
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RATING_CARD_SHOWN, true)
                .putInt(KEY_RATING_CARD_SELECTED_STARS, selectedStars)
                .putLong(KEY_RATING_CARD_SUBMITTED_AT, now)
                .apply()

            Log.d(TAG, "Rating selected stars=$selectedStars, redirecting to Play Store")
            openPlayStoreFeedback(activity)
            dialog.dismiss()
        }

        dialog.setOnCancelListener {
            markPromptShown()
        }

        dialog.show()
    }

    private fun openPlayStoreFeedback(activity: Activity) {
        val packageName = activity.packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            activity.startActivity(marketIntent)
        } catch (_: Exception) {
            activity.startActivity(webIntent)
        }
    }
}
