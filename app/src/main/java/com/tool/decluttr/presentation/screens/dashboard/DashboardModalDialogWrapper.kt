package com.tool.decluttr.presentation.screens.dashboard

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import com.tool.decluttr.R

internal class DashboardModalDialogWrapper(
    private val context: Context,
    private val contentLayoutRes: Int,
    private val dismissOnOutside: Boolean
) {
    fun build(): Dialog {
        return Dialog(context, R.style.ThemeOverlay_Decluttr_DashboardModal).apply {
            setContentView(contentLayoutRes)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setDimAmount(0.58f)

            val density = context.resources.displayMetrics.density
            val maxDialogWidth = (520 * density).toInt()
            val horizontalMargin = (24 * density).toInt()
            val availableWidth = context.resources.displayMetrics.widthPixels - (horizontalMargin * 2)
            val targetWidth = minOf(maxDialogWidth, availableWidth)
            window?.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

            setCancelable(true)
            setCanceledOnTouchOutside(dismissOnOutside)
            setupKeyboardHandling(this)
        }
    }

    private fun setupKeyboardHandling(dialog: Dialog) {
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)
            ) {
                dialog.dismiss()
                return@setOnKeyListener true
            }
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_TAB) {
                val root = dialog.window?.decorView ?: return@setOnKeyListener false
                val focusables = root.getFocusables(View.FOCUS_FORWARD).filter { it.isShown && it.isFocusable }
                if (focusables.isEmpty()) return@setOnKeyListener false
                val current = root.findFocus()
                val currentIndex = focusables.indexOf(current)
                val nextIndex = if (event.isShiftPressed) {
                    if (currentIndex <= 0) focusables.lastIndex else currentIndex - 1
                } else {
                    if (currentIndex == -1 || currentIndex >= focusables.lastIndex) 0 else currentIndex + 1
                }
                focusables[nextIndex].requestFocus()
                return@setOnKeyListener true
            }
            false
        }
    }
}
