package com.tool.decluttr.presentation.screens.dashboard

import com.tool.decluttr.domain.model.AppListItem

data class DiscoveryViewState(
    val title: String = "Dashboard",
    val apps: List<AppListItem> = emptyList(),
    val isAppListMode: Boolean = false,
    val isLoading: Boolean = false,
    val listModeFilter: String = "All",
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NAME_ASC
)
