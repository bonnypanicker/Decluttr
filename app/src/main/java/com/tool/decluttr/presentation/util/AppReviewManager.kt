package com.tool.decluttr.presentation.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

object AppReviewManager {
    private const val PREFS_NAME = "decluttr_prefs"
    private const val KEY_LAUNCH_COUNT = "app_launch_count"
    private const val TAG = "AppReviewManager"

    fun checkAndShowReview(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        
        launchCount++
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()

        Log.d(TAG, "App launch count: $launchCount")

        // Trigger the rating window on the 3rd app opening
        if (launchCount == 3) {
            showReviewDialog(activity)
        }
    }

    private fun showReviewDialog(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    Log.d(TAG, "Review flow completed")
                }
            } else {
                Log.w(TAG, "Failed to request review flow", task.exception)
            }
        }
    }
}
