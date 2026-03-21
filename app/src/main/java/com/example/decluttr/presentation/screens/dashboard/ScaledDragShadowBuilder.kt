package com.example.decluttr.presentation.screens.dashboard

import android.graphics.Canvas
import android.graphics.Point
import android.view.View

/**
 * Custom DragShadowBuilder that renders the dragged view at a specified scale.
 * Pixel Launcher uses ~1.1x scale for the drag shadow to provide visual feedback
 * that the icon has been "picked up" from the grid.
 */
class ScaledDragShadowBuilder(
    view: View,
    private val scaleFactor: Float = 1.1f
) : View.DragShadowBuilder(view) {

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        val v = view ?: return
        val width = (v.width * scaleFactor).toInt()
        val height = (v.height * scaleFactor).toInt()
        outShadowSize.set(width, height)
        // Touch point at center of the shadow
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val v = view ?: return
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)
        // Slight alpha reduction to indicate "lifted" state
        canvas.saveLayerAlpha(
            0f, 0f,
            v.width.toFloat(), v.height.toFloat(),
            230 // ~90% opacity
        )
        v.draw(canvas)
        canvas.restore()
        canvas.restore()
    }
}
