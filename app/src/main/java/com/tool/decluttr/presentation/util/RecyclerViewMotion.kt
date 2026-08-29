package com.tool.decluttr.presentation.util

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * Apply the shared Decluttr item animator timings to a RecyclerView.
 * Use this once per RecyclerView in onViewCreated so add/remove/move/change
 * animations are consistent across every list in the app.
 */
object RecyclerViewMotion {
    fun apply(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220
            removeDuration = 220
            moveDuration = 260
            changeDuration = 220
        }
    }
}
