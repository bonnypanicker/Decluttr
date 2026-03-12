package com.example.decluttr.presentation.screens.dashboard

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.decluttr.domain.model.ArchivedApp
import com.example.decluttr.domain.repository.AppRepository
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.example.decluttr.domain.usecase.GetUnusedAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _hasUsagePermission = MutableStateFlow(checkUsagePermissionUseCase())
    val hasUsagePermission = _hasUsagePermission.asStateFlow()

    // 100MB in bytes
    private val LARGE_APP_THRESHOLD = 100L * 1024 * 1024

    val largeApps = kotlinx.coroutines.flow.combine(allInstalledApps, unusedApps) { all, unused ->
        // Exclude already-rarely-used apps so we don't double-count in cards
        val unusedIds = unused.map { it.packageId }.toSet()
        all.filter { it.apkSizeBytes > LARGE_APP_THRESHOLD && it.packageId !in unusedIds }
           .sortedByDescending { it.apkSizeBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class CelebrationData(
        val count: Int,
        val savedBytes: Long
    )

    private val _celebrationEvent = kotlinx.coroutines.flow.MutableSharedFlow<CelebrationData>()
    val celebrationEvent = _celebrationEvent.asSharedFlow()

    private var discoveryJob: kotlinx.coroutines.Job? = null
    private var lastRefreshTime = 0L
    private val REFRESH_COOLDOWN_MS = 30_000L  // 30 seconds

    init {
        loadDiscoveryData()
    }

    fun checkUsagePermission() {
        _hasUsagePermission.value = checkUsagePermissionUseCase()
    }

    fun loadDiscoveryData() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            _isLoadingDiscovery.value = true
            
            // Single pass: fetch all installed apps + unused in one go
            val result = getUnusedAppsUseCase.fetchAll()
            _unusedApps.value = result.unusedApps
            _allInstalledApps.value = result.allApps
            
            _isLoadingDiscovery.value = false
            lastRefreshTime = System.currentTimeMillis()
        }
    }

    fun loadDiscoveryDataIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_COOLDOWN_MS) return
        loadDiscoveryData()
    }
    
    fun deleteArchivedApp(app: ArchivedApp) {
        viewModelScope.launch {
            appRepository.deleteApp(app)
        }
    }

    fun archiveAndUninstallSelected(packageIds: Set<String>) {
        if (packageIds.isEmpty()) return
        
        // Calculate size before removing
        val appsToUninstall = allInstalledApps.value.filter { it.packageId in packageIds }
        val savedBytes = appsToUninstall.sumOf { it.apkSizeBytes }
        
        // Build metadata map for archive entries
        val appInfoMap = appsToUninstall.associate { 
            it.packageId to Pair(it.isPlayStoreInstalled, it.lastTimeUsed)
        }
        
        viewModelScope.launch {
            archiveAndUninstallUseCase(packageIds.toList(), appInfoMap)
            loadDiscoveryData() // Refresh list after uninstall queue is fired
            _celebrationEvent.emit(CelebrationData(packageIds.size, savedBytes))
        }
    }

    fun uninstallSelectedOnly(packageIds: Set<String>) {
        if (packageIds.isEmpty()) return
        
        // Calculate size before removing
        val appsToUninstall = allInstalledApps.value.filter { it.packageId in packageIds }
        val savedBytes = appsToUninstall.sumOf { it.apkSizeBytes }
        
        viewModelScope.launch {
            packageIds.forEach { pkg ->
                uninstallAppUseCase(pkg)
            }
            loadDiscoveryData()
            _celebrationEvent.emit(CelebrationData(packageIds.size, savedBytes))
        }
    }
}
