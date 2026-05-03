package com.tool.decluttr.presentation.screens.wishlist

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tool.decluttr.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class WishlistNotesBottomSheet : BottomSheetDialogFragment(R.layout.bottom_sheet_wishlist_notes) {

    companion object {
        private const val ARG_PACKAGE_ID = "packageId"
        private const val ARG_APP_NAME = "appName"
        private const val ARG_ICON_URL = "iconUrl"
        private const val ARG_NOTES = "notes"
        private const val MAX_NOTES_CHARS = 500

        fun newInstance(
            packageId: String,
            appName: String,
            iconUrl: String,
            notes: String
        ): WishlistNotesBottomSheet {
            return WishlistNotesBottomSheet().apply {
                arguments = bundleOf(
                    ARG_PACKAGE_ID to packageId,
                    ARG_APP_NAME to appName,
                    ARG_ICON_URL to iconUrl,
                    ARG_NOTES to notes
                )
            }
        }
    }

    private val viewModel: WishlistViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val packageId = arguments?.getString(ARG_PACKAGE_ID).orEmpty()
        if (packageId.isBlank()) {
            dismissAllowingStateLoss()
            return
        }
        val appName = arguments?.getString(ARG_APP_NAME).orEmpty()
        val iconUrl = arguments?.getString(ARG_ICON_URL).orEmpty()
        val originalNotes = arguments?.getString(ARG_NOTES).orEmpty()

        val ivIcon: ImageView = view.findViewById(R.id.iv_app_icon)
        val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
        val tvCharCount: TextView = view.findViewById(R.id.tv_char_count)
        val etNotes: TextInputEditText = view.findViewById(R.id.et_notes)
        val btnSave: MaterialButton = view.findViewById(R.id.btn_save)
        val btnCancel: MaterialButton = view.findViewById(R.id.btn_cancel)

        tvAppName.text = appName
        etNotes.filters = arrayOf(InputFilter.LengthFilter(MAX_NOTES_CHARS))
        etNotes.setText(originalNotes)
        etNotes.setSelection(originalNotes.length)

        if (iconUrl.isNotBlank()) {
            ivIcon.load(iconUrl) {
                crossfade(false)
                transformations(RoundedCornersTransformation(radius = 18f))
                placeholder(R.drawable.ic_app_placeholder)
                error(R.drawable.ic_app_placeholder)
            }
        } else {
            runCatching {
                ivIcon.setImageDrawable(
                    requireContext().packageManager.getApplicationIcon(packageId)
                )
            }.onFailure {
                ivIcon.setImageResource(R.drawable.ic_app_placeholder)
            }
        }

        fun updateCounter(length: Int) {
            tvCharCount.text = String.format(Locale.US, "%d / %d", length, MAX_NOTES_CHARS)
            val nearLimit = length >= (MAX_NOTES_CHARS * 0.9).toInt()
            tvCharCount.setTextColor(
                if (nearLimit) {
                    requireContext().getColor(android.R.color.holo_orange_dark)
                } else {
                    com.google.android.material.color.MaterialColors.getColor(
                        tvCharCount,
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                }
            )
        }

        updateCounter(originalNotes.length)
        btnSave.isEnabled = false

        etNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val current = s?.toString().orEmpty()
                updateCounter(current.length)
                btnSave.isEnabled = current != originalNotes
            }
        })

        btnSave.setOnClickListener {
            val notes = etNotes.text?.toString()?.trim().orEmpty()
            viewModel.updateNotes(packageId, notes)
            dismissAllowingStateLoss()
        }

        btnCancel.setOnClickListener { dismissAllowingStateLoss() }
    }

    override fun getTheme(): Int {
        return com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog
    }
}

