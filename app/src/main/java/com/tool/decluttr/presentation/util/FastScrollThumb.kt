package com.tool.decluttr.presentation.util

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR

/**
 * A lightweight, draggable fast-scroll thumb that overlays a [RecyclerView].
 *
 * The thumb fades in while the list is being scrolled (or dragged) and fades
 * out shortly after scrolling stops, so it stays hidden while idle. Its color
 * is resolved from `?attr/colorPrimary` at runtime so it automatically matches
 * the active (light/dark) theme.
 *
 * The [thumb] view must be a sibling that overlays the [recyclerView] (e.g. a
 * child of the same FrameLayout wrapper), aligned to the end edge, so that its
 * vertical travel matches the list's scrollable range.
 */
class FastScrollThumb(
    private val recyclerView: RecyclerView,
    private val thumb: View
) {
    private val handler = Handler(Looper.getMainLooper())
    private val density = thumb.resources.displayMetrics.density

    private var isDragging = false
    private var dragDownRawY = 0f
    private var dragDownTranslationY = 0f

    private val hideDelayMs = 1200L
    private val hideRunnable = Runnable { animateOut() }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (isDragging) return
            updateThumbPosition()
            if (isScrollable()) {
                animateIn()
                scheduleHide()
            } else {
                cancelHide()
                animateOut()
            }
        }
    }

    init {
        applyThemedThumbDrawable()
        thumb.alpha = 0f
        thumb.visibility = View.GONE

        recyclerView.addOnScrollListener(scrollListener)
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refresh()
        }
        setupDrag()
    }

    /** Re-evaluates thumb visibility/position (e.g. after the dataset changes). */
    fun refresh() {
        if (!isScrollable()) {
            cancelHide()
            if (!isDragging) animateOut()
            return
        }
        updateThumbPosition()
    }

    /** Removes listeners and pending callbacks. Call from onDestroyView. */
    fun detach() {
        cancelHide()
        recyclerView.removeOnScrollListener(scrollListener)
        thumb.setOnTouchListener(null)
    }

    private fun isScrollable(): Boolean {
        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()
        return range - extent > 0
    }

    private fun trackHeight(): Int {
        val parent = thumb.parent as? View ?: recyclerView
        return (parent.height - thumb.height).coerceAtLeast(0)
    }

    private fun updateThumbPosition() {
        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()
        val offset = recyclerView.computeVerticalScrollOffset()
        val maxOffset = range - extent
        val track = trackHeight()
        if (maxOffset <= 0 || track <= 0) return
        val fraction = (offset.toFloat() / maxOffset).coerceIn(0f, 1f)
        thumb.translationY = fraction * track
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, hideDelayMs)
    }

    private fun cancelHide() {
        handler.removeCallbacks(hideRunnable)
    }

    private fun animateIn() {
        if (thumb.visibility != View.VISIBLE) {
            thumb.visibility = View.VISIBLE
        }
        thumb.animate().cancel()
        thumb.animate().alpha(1f).setDuration(150).start()
    }

    private fun animateOut() {
        if (thumb.visibility == View.GONE) return
        thumb.animate().cancel()
        thumb.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { thumb.visibility = View.GONE }
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        thumb.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isScrollable()) return@setOnTouchListener false
                    isDragging = true
                    dragDownRawY = event.rawY
                    dragDownTranslationY = thumb.translationY
                    cancelHide()
                    recyclerView.stopScroll()
                    thumb.parent?.requestDisallowInterceptTouchEvent(true)
                    thumb.animate().cancel()
                    thumb.alpha = 1f
                    thumb.visibility = View.VISIBLE
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) return@setOnTouchListener false
                    val track = trackHeight()
                    if (track <= 0) return@setOnTouchListener true
                    val targetTop =
                        (dragDownTranslationY + (event.rawY - dragDownRawY))
                            .coerceIn(0f, track.toFloat())
                    thumb.translationY = targetTop
                    scrollToFraction(targetTop / track)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        thumb.parent?.requestDisallowInterceptTouchEvent(false)
                        updateThumbPosition()
                        scheduleHide()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun scrollToFraction(fraction: Float) {
        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()
        val maxOffset = range - extent
        if (maxOffset <= 0) return
        val targetOffset = fraction * maxOffset
        val currentOffset = recyclerView.computeVerticalScrollOffset()
        recyclerView.scrollBy(0, (targetOffset - currentOffset).toInt())
    }

    private fun applyThemedThumbDrawable() {
        val color = resolveThemeColor(MaterialR.attr.colorPrimary)
        val bar = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f * density
            setColor(color)
        }
        // Slim visible bar pushed toward the end edge inside a wider touch target.
        val background = InsetDrawable(
            bar,
            (16f * density).toInt(),
            0,
            (2f * density).toInt(),
            0
        )
        thumb.background = background
    }

    private fun resolveThemeColor(attrRes: Int): Int {
        return com.google.android.material.color.MaterialColors.getColor(
            recyclerView,
            attrRes,
            android.graphics.Color.GRAY
        )
    }
}
