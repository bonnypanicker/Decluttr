package com.tool.decluttr.presentation.screens.dashboard

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import coil.load
import com.google.android.material.button.MaterialButton
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
        dialog = DashboardModalDialogWrapper(
            context = context,
            contentLayoutRes = R.layout.dialog_app_details,
            dismissOnOutside = true
        ).build().apply {
            setOnDismissListener { onDismissRequest() }

            val appIcon = findViewById<ImageView>(R.id.app_icon)
            val appName = findViewById<TextView>(R.id.app_name)
            val appCategory = findViewById<TextView>(R.id.app_category)
            val btnDelete = findViewById<ImageButton>(R.id.btn_delete)
            val btnReinstall = findViewById<MaterialButton>(R.id.btn_reinstall)
            
            appName.text = app.name
            appCategory.text = app.category ?: context.getString(R.string.archive_popup_uncategorized)
            appIcon.load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
            }

            ArchiveNotesCardMolecule(
                root = findViewById(R.id.archive_notes_card),
                initialText = app.notes,
                onSave = onNotesUpdated
            )

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
        }
        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
