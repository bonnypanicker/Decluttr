package com.tool.decluttr.presentation.screens.dashboard

internal enum class ArchiveNotesMode {
    VIEW,
    EDIT
}

internal data class ArchiveNotesUiState(
    val mode: ArchiveNotesMode,
    val savedText: String,
    val draftText: String
)

internal class ArchiveNotesStateMachine(initialText: String?) {
    private var savedText: String = initialText?.trim().orEmpty()
    private var draftText: String = savedText
    private var mode: ArchiveNotesMode =
        if (savedText.isBlank()) ArchiveNotesMode.EDIT else ArchiveNotesMode.VIEW

    fun state(): ArchiveNotesUiState {
        return ArchiveNotesUiState(
            mode = mode,
            savedText = savedText,
            draftText = draftText
        )
    }

    fun startEdit() {
        draftText = savedText
        mode = ArchiveNotesMode.EDIT
    }

    fun updateDraft(value: String) {
        draftText = value
    }

    fun save(): String {
        savedText = draftText.trim()
        mode = ArchiveNotesMode.VIEW
        return savedText
    }

    fun cancel() {
        draftText = savedText
        mode = ArchiveNotesMode.VIEW
    }

    fun onEscape(): Boolean {
        if (mode == ArchiveNotesMode.EDIT) {
            cancel()
            return true
        }
        return false
    }

    fun onCtrlEnter(): String? {
        if (mode == ArchiveNotesMode.EDIT) {
            return save()
        }
        return null
    }
}
