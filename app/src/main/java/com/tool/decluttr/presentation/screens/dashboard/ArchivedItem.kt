package com.tool.decluttr.presentation.screens.dashboard

import com.tool.decluttr.domain.model.ArchivedApp

sealed class ArchivedItem {
    data class App(val app: ArchivedApp) : ArchivedItem()
    data class Folder(val name: String, val apps: List<ArchivedApp>) : ArchivedItem()
}
