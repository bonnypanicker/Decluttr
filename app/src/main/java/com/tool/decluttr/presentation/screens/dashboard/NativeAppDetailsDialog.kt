package com.tool.decluttr.presentation.screens.dashboard

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.KeyEvent
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.util.AppIconModel

class NativeAppDetailsDialog(
    private val context: Context,
    private val app: ArchivedApp,
    private val onNotesUpdated: (String) -> Unit,
    private val onDelete: () -> Unit,
    private val onDismissRequest: () -> Unit
) {
    private var dialog: Dialog? = null

    fun show() {
        dialog = Dialog(context, R.style.ThemeOverlay_Decluttr_AppDetailsDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_app_details)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.setDimAmount(0.58f)

            setCancelable(true)
            setCanceledOnTouchOutside(true)
            setOnDismissListener { onDismissRequest() }

            val density = context.resources.displayMetrics.density
            val maxDialogWidth = (520 * density).toInt()
            val horizontalMargin = (24 * density).toInt()
            val availableWidth = context.resources.displayMetrics.widthPixels - (horizontalMargin * 2)
            val targetWidth = minOf(maxDialogWidth, availableWidth)
            window?.setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

            val appIcon = findViewById<ImageView>(R.id.app_icon)
            val appName = findViewById<TextView>(R.id.app_name)
            val appCategory = findViewById<TextView>(R.id.app_category)
            val btnDelete = findViewById<ImageButton>(R.id.btn_delete)
            val btnReinstall = findViewById<MaterialButton>(R.id.btn_reinstall)
            val btnEditDone = findViewById<MaterialButton>(R.id.btn_edit_done)
            val notesInput = findViewById<TextInputEditText>(R.id.notes_input)
            
            // Populate fields
            appName.text = app.name
            appCategory.text = app.category ?: context.getString(R.string.archive_popup_uncategorized)
            appIcon.load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
            }

            val hasNotes = !app.notes.isNullOrBlank()
            notesInput.setText(app.notes ?: "")

            var isEditing = !hasNotes

            fun updateNotesState() {
                if (isEditing) {
                    btnEditDone.text = context.getString(R.string.archive_popup_done)
                    notesInput.isEnabled = true
                    notesInput.requestFocus()
                } else {
                    btnEditDone.text = context.getString(R.string.archive_popup_edit)
                    notesInput.isEnabled = false
                }
            }

            updateNotesState()

            btnEditDone.setOnClickListener {
                if (isEditing) {
                    val newNotes = notesInput.text?.toString() ?: ""
                    onNotesUpdated(newNotes)
                    isEditing = false
                } else {
                    isEditing = true
                }
                updateNotesState()
            }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.archive_popup_delete_title))
                    .setMessage(context.getString(R.string.archive_popup_delete_message, app.name))
                    .setPositiveButton(context.getString(R.string.archive_popup_delete_action)) { _, _ ->
                        onDelete()
                        dismiss()
                    }
                    .setNegativeButton(context.getString(R.string.archive_popup_cancel_action), null)
                    .show()
            }

            btnReinstall.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageId}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageId}"))
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(webIntent)
                }
            }

            setOnShowListener {
                btnReinstall.requestFocus()
            }

            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)
                ) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
        }
        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
