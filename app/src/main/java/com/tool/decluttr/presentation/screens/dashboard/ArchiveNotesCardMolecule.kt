package com.tool.decluttr.presentation.screens.dashboard

import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.tool.decluttr.R

internal class ArchiveNotesCardMolecule(
    root: View,
    initialText: String?,
    private val onSave: (String) -> Unit
) {
    private val notesViewCard: MaterialCardView = root.findViewById(R.id.notes_view_card)
    private val notesSavedText: TextView = root.findViewById(R.id.notes_saved_text)
    private val inlineEditButton: MaterialButton = root.findViewById(R.id.btn_notes_inline_edit)
    private val notesEditContainer: LinearLayout = root.findViewById(R.id.notes_edit_container)
    private val notesInput: TextInputEditText = root.findViewById(R.id.notes_input)
    private val saveButton: MaterialButton = root.findViewById(R.id.btn_notes_save)
    private val cancelButton: MaterialButton = root.findViewById(R.id.btn_notes_cancel)

    private val stateMachine = ArchiveNotesStateMachine(initialText)
    private val focusedStrokeColor = ContextCompat.getColor(root.context, android.R.color.holo_blue_light)
    private val defaultStrokeColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutlineVariant)

    init {
        inlineEditButton.setOnClickListener { startEditing() }
        notesViewCard.setOnClickListener { startEditing() }
        notesViewCard.setOnFocusChangeListener { _, hasFocus ->
            notesViewCard.strokeWidth = if (hasFocus) 2 else 1
            if (hasFocus) {
                notesViewCard.strokeColor = focusedStrokeColor
            } else {
                notesViewCard.strokeColor = defaultStrokeColor
            }
        }
        notesViewCard.setOnHoverListener { _, hovered ->
            notesViewCard.alpha = if (hovered) 0.96f else 1f
            false
        }

        saveButton.setOnClickListener { saveChanges() }
        cancelButton.setOnClickListener { cancelEditing() }

        notesInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (stateMachine.onEscape()) {
                    render()
                    true
                } else {
                    false
                }
            } else if (
                event.action == KeyEvent.ACTION_UP &&
                keyCode == KeyEvent.KEYCODE_ENTER &&
                event.isCtrlPressed
            ) {
                val saved = stateMachine.onCtrlEnter()
                if (saved != null) {
                    onSave(saved)
                    render()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        render()
    }

    private fun startEditing() {
        stateMachine.startEdit()
        render()
    }

    private fun saveChanges() {
        stateMachine.updateDraft(notesInput.text?.toString().orEmpty())
        val saved = stateMachine.save()
        onSave(saved)
        render()
    }

    private fun cancelEditing() {
        stateMachine.cancel()
        render()
    }

    private fun render() {
        val state = stateMachine.state()
        val hasSavedText = state.savedText.isNotBlank()
        val savedText = if (hasSavedText) {
            state.savedText
        } else {
            notesSavedText.context.getString(R.string.archive_popup_notes_empty)
        }
        notesSavedText.text = savedText
        notesSavedText.alpha = if (hasSavedText) 1f else 0.78f
        inlineEditButton.text = notesSavedText.context.getString(R.string.archive_popup_edit)

        if (state.mode == ArchiveNotesMode.EDIT) {
            notesViewCard.visibility = View.GONE
            notesEditContainer.visibility = View.VISIBLE
            if (notesInput.text?.toString() != state.draftText) {
                notesInput.setText(state.draftText)
                notesInput.setSelection(state.draftText.length)
            }
            notesInput.requestFocus()
        } else {
            notesViewCard.visibility = View.VISIBLE
            notesEditContainer.visibility = View.GONE
        }
    }
}
