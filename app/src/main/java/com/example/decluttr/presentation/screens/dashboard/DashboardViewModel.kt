package com.example.decluttr.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.domain.repository.AppRepository
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.example.decluttr.domain.usecase.GetUnusedAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val getUnusedAppsUseCase: GetUnusedAppsUseCase,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val archiveAndUninstallUseCase: com.example.decluttr.domain.usecase.ArchiveAndUninstallUseCase,
    private val checkUsagePermissionUseCase: com.example.decluttr.domain.usecase.CheckUsagePermissionUseCase,
    private val uninstallAppUseCase: com.example.decluttr.domain.usecase.UninstallAppUseCase
) : ViewModel() {

    val archivedApps: StateFlow<List<ArchivedApp>> = appRepository.getAllArchivedApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _unusedApps = MutableStateFlow<List<GetInstalledAppsUseCase.InstalledAppInfo>>(emptyList())
    val unusedApps = _unusedApps.asStateFlow()

    private val _allInstalledApps = MutableStateFlow<List<GetInstalledAppsUseCase.InstalledAppInfo>>(emptyList())
    val allInstalledApps = _allInstalledApps.asStateFlow()

    private val _isLoadingDiscovery = MutableStateFlow(false)
    val isLoadingDiscovery = _isLoadingDiscovery.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(true)
    val hasUsagePermission = _hasUsagePermission.asStateFlow()

    init {
        checkUsagePermission()
        loadDiscoveryData()
    }

    fun checkUsagePermission() {
        _hasUsagePermission.value = checkUsagePermissionUseCase()
    }

    fun loadDiscoveryData() {
        viewModelScope.launch {
            _isLoadingDiscovery.value = true
            
            // In a real app we might load these in parallel, but sequential is fine for now
            val unused = getUnusedAppsUseCase()
            _unusedApps.value = unused
            
            val all = getInstalledAppsUseCase()
            _allInstalledApps.value = all
            
            _isLoadingDiscovery.value = false
        }
    }
    
    fun deleteArchivedApp(app: ArchivedApp) {
        viewModelScope.launch {
            appRepository.deleteApp(app)
        }
    }

    fun archiveAndUninstallSelected(packageIds: Set<String>) {
        if (packageIds.isEmpty()) return
        viewModelScope.launch {
            archiveAndUninstallUseCase(packageIds.toList())
            loadDiscoveryData() // Refresh list after uninstall queue is fired
        }
    }

    fun uninstallSelectedOnly(packageIds: Set<String>) {
        if (packageIds.isEmpty()) return
        viewModelScope.launch {
            packageIds.forEach { pkg ->
                uninstallAppUseCase(pkg)
            }
        }
    }
}
