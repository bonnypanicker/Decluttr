package com.tool.decluttr.presentation.screens.dashboard

import android.content.Context
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tool.decluttr.R

/**
 * Lightweight category picker used by the archive details popup and the
 * post-uninstall review pages. Shows common suggestions first; "Other…"
 * opens a minimal free-text input.
 */
object CategoryPicker {

    fun show(context: Context, onCategorySelected: (String) -> Unit) {
        val suggestions = context.resources.getStringArray(R.array.category_suggestions)
        val items = suggestions + context.getString(R.string.category_picker_custom_option)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.category_picker_title))
            .setItems(items) { _, which ->
                if (which < suggestions.size) {
                    onCategorySelected(suggestions[which])
                } else {
                    showCustomInput(context, onCategorySelected)
                }
            }
            .show()
    }

    private fun showCustomInput(context: Context, onCategorySelected: (String) -> Unit) {
        val density = context.resources.displayMetrics.density
        val input = TextInputEditText(context).apply {
            hint = context.getString(R.string.category_picker_custom_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            maxLines = 1
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val container = FrameLayout(context).apply {
            setPadding(
                (24 * density).toInt(),
                (8 * density).toInt(),
                (24 * density).toInt(),
                0
            )
            addView(input)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.category_picker_title))
            .setView(container)
            .setPositiveButton(context.getString(R.string.category_picker_add_action)) { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    onCategorySelected(text)
                }
            }
            .setNegativeButton(context.getString(R.string.archive_popup_cancel_action), null)
            .show()

        input.requestFocus()
        input.post {
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}
