package com.tool.decluttr.presentation.screens.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.domain.repository.AppRepository
import com.tool.decluttr.domain.repository.BillingRepository
import com.tool.decluttr.domain.usecase.GetAppDetailsUseCase
import com.tool.decluttr.domain.usecase.GetInstalledAppsUseCase
import com.tool.decluttr.domain.usecase.GetUnusedAppsUseCase
import com.tool.decluttr.presentation.util.AppIconModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val getUnusedAppsUseCase: GetUnusedAppsUseCase,
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val archiveAndUninstallUseCase: com.tool.decluttr.domain.usecase.ArchiveAndUninstallUseCase,
    private val checkUsagePermissionUseCase: com.tool.decluttr.domain.usecase.CheckUsagePermissionUseCase,
    private val uninstallAppUseCase: com.tool.decluttr.domain.usecase.UninstallAppUseCase,
    val billingRepository: BillingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "DecluttrDragDbgVm"
    }

    val archivedApps: StateFlow<List<ArchivedApp>> = appRepository.getAllArchivedApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val archivedAppCount: StateFlow<Int> = appRepository.getAllArchivedApps()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val isPremium: StateFlow<Boolean> = billingRepository.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _unusedApps = MutableStateFlow<List<GetInstalledAppsUseCase.InstalledAppInfo>>(emptyList())
    val unusedApps = _unusedApps.asStateFlow()

    private val _allInstalledApps = MutableStateFlow<List<GetInstalledAppsUseCase.InstalledAppInfo>>(emptyList())
    val allInstalledApps = _allInstalledApps.asStateFlow()

    private val _isLoadingDiscovery = MutableStateFlow(false)
    val isLoadingDiscovery = _isLoadingDiscovery.asStateFlow()

    private val _isPreparingAllApps = MutableStateFlow(false)
    val isPreparingAllApps = _isPreparingAllApps.asStateFlow()

    private val _isStartupReady = MutableStateFlow(false)
    val isStartupReady = _isStartupReady.asStateFlow()

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

    data class ReviewData(
        val archivedApps: List<ArchivedApp>,
        val celebration: CelebrationData
    )

    private val _celebrationEvent = kotlinx.coroutines.flow.MutableSharedFlow<CelebrationData>()
    val celebrationEvent = _celebrationEvent.asSharedFlow()

    private val _reviewEvent = kotlinx.coroutines.flow.MutableSharedFlow<ReviewData>()
    val reviewEvent = _reviewEvent.asSharedFlow()

    private val _undoDeleteEvent = kotlinx.coroutines.flow.MutableSharedFlow<ArchivedApp>()
    val undoDeleteEvent = _undoDeleteEvent.asSharedFlow()

    private val _paymentRequiredEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val paymentRequiredEvent = _paymentRequiredEvent.asSharedFlow()

    private var discoveryJob: kotlinx.coroutines.Job? = null
    private var lazyWarmIconsJob: kotlinx.coroutines.Job? = null
    private val preloadedIconPackages = ConcurrentHashMap.newKeySet<String>()
    private var lastRefreshTime = 0L
    private val REFRESH_COOLDOWN_MS = 30_000L  // 30 seconds
    private val INITIAL_ICON_PRELOAD_COUNT = 60
    private val STARTUP_BLOCKING_ICON_PRELOAD_COUNT = 48
    private val ICON_PREFETCH_CHUNK_SIZE = 12
    private val ICON_WARM_PARALLELISM = 6

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
            try {
                val isInitialLoad = !_isStartupReady.value
                val result = getUnusedAppsUseCase.fetchAll()
                _unusedApps.value = result.unusedApps
                _allInstalledApps.value = result.allApps
                primeIconCaches(
                    apps = result.allApps,
                    awaitInitial = isInitialLoad,
                    awaitCount = if (isInitialLoad) STARTUP_BLOCKING_ICON_PRELOAD_COUNT else 0
                )
                lastRefreshTime = System.currentTimeMillis()
                if (!_isStartupReady.value) {
                    _isStartupReady.value = true
                }
            } finally {
                _isLoadingDiscovery.value = false
            }
        }
    }

    fun prepareAllAppsForDisplay() {
        if (_isPreparingAllApps.value) return
        viewModelScope.launch {
            _isPreparingAllApps.value = true
            try {
                val result = if (allInstalledApps.value.isEmpty()) {
                    getUnusedAppsUseCase.fetchAll()
                } else {
                    GetUnusedAppsUseCase.UnusedAppsResult(
                        allApps = allInstalledApps.value,
                        unusedApps = unusedApps.value
                    )
                }
                _unusedApps.value = result.unusedApps
                _allInstalledApps.value = result.allApps
                primeIconCaches(result.allApps, awaitInitial = true)
            } finally {
                _isPreparingAllApps.value = false
            }
        }
    }

    fun prefetchIcons(packageIds: List<String>) {
        if (packageIds.isEmpty()) return
        viewModelScope.launch {
            warmPackageIds(packageIds, limit = ICON_PREFETCH_CHUNK_SIZE)
        }
    }

    private suspend fun primeIconCaches(
        apps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
        awaitInitial: Boolean,
        awaitCount: Int = INITIAL_ICON_PRELOAD_COUNT
    ) {
        val initialPackages = apps.take(INITIAL_ICON_PRELOAD_COUNT).map { it.packageId }
        if (awaitInitial) {
            val blockingCount = awaitCount.coerceIn(0, initialPackages.size)
            if (blockingCount > 0) {
                warmPackageIds(initialPackages.take(blockingCount))
            }
            if (blockingCount < initialPackages.size) {
                viewModelScope.launch {
                    warmPackageIds(initialPackages.drop(blockingCount))
                }
            }
        } else {
            viewModelScope.launch {
                warmPackageIds(initialPackages)
            }
        }
        lazyWarmIconsJob?.cancel()
        lazyWarmIconsJob = viewModelScope.launch {
            val remainingPackages = apps.drop(INITIAL_ICON_PRELOAD_COUNT).map { it.packageId }
            remainingPackages.chunked(ICON_PREFETCH_CHUNK_SIZE).forEach { chunk ->
                if (!isActive) return@launch
                warmPackageIds(chunk)
                yield()
            }
        }
    }

    private suspend fun warmPackageIds(
        packageIds: List<String>,
        limit: Int = packageIds.size
    ) {
        withContext(Dispatchers.IO) {
            val imageLoader = context.imageLoader
            val toWarm = packageIds
                .filter { preloadedIconPackages.add(it) }
                .take(limit)
            toWarm
                .chunked(ICON_WARM_PARALLELISM)
                .forEach { batch ->
                    coroutineScope {
                        batch
                            .map { packageId ->
                                async {
                                    val request = ImageRequest.Builder(context)
                                        .data(AppIconModel(packageId))
                                        .memoryCacheKey(packageId)
                                        .crossfade(false)
                                        .build()
                                    imageLoader.execute(request)
                                }
                            }
                            .awaitAll()
                    }
                }
        }
    }

    fun loadDiscoveryDataIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_COOLDOWN_MS) return
        loadDiscoveryData()
    }
    
    data class UninstallProgress(val current: Int, val total: Int, val isUninstalling: Boolean)
    private val _uninstallProgress = MutableStateFlow(UninstallProgress(0, 0, false))
    val uninstallProgress = _uninstallProgress.asStateFlow()

    fun deleteArchivedApp(app: ArchivedApp) {
        viewModelScope.launch {
            appRepository.deleteApp(app)
            _undoDeleteEvent.emit(app)
        }
    }

    fun restoreArchivedApp(app: ArchivedApp) {
        viewModelScope.launch {
            appRepository.insertApp(app)
        }
    }

    fun updateArchivedApp(app: ArchivedApp) {
        viewModelScope.launch {
            android.util.Log.d(
                TAG,
                "updateArchivedApp pkg=${app.packageId} folder=${app.folderName} notesLen=${app.notes?.length ?: 0}"
            )
            appRepository.updateApp(app)
        }
    }

    fun archiveAndUninstallSelected(
        packageIds: Set<String>,
        archiveEligiblePackageIds: Set<String> = packageIds
    ) {
        if (packageIds.isEmpty()) return
        
        val appsToUninstall = allInstalledApps.value.filter { it.packageId in packageIds }
        val appInfoMap = appsToUninstall.associate {
            it.packageId to com.tool.decluttr.domain.usecase.ArchiveAndUninstallUseCase.ArchiveSourceInfo(
                isPlayStoreInstalled = it.isPlayStoreInstalled,
                lastTimeUsed = it.lastTimeUsed,
                archivedSizeBytes = it.apkSizeBytes.takeIf { size -> size > 0L },
                name = it.name
            )
        }
        
        viewModelScope.launch {
            _uninstallProgress.value = UninstallProgress(0, packageIds.size, true)
            var uninstalledCount = 0
            val successfullyArchivedIds = mutableListOf<String>()

            for (packageId in packageIds) {
                val sourceInfo = run {
                    val baseInfo = appInfoMap[packageId]
                    val details = runCatching { getAppDetailsUseCase(packageId, fetchIcon = true) }.getOrNull()
                    com.tool.decluttr.domain.usecase.ArchiveAndUninstallUseCase.ArchiveSourceInfo(
                        isPlayStoreInstalled = baseInfo?.isPlayStoreInstalled ?: true,
                        lastTimeUsed = baseInfo?.lastTimeUsed ?: 0L,
                        archivedSizeBytes = details?.archivedSizeBytes ?: baseInfo?.archivedSizeBytes,
                        name = details?.name ?: baseInfo?.name,
                        category = details?.category ?: baseInfo?.category,
                        iconBytes = details?.iconBytes ?: baseInfo?.iconBytes
                    )
                }
                _uninstallProgress.value = UninstallProgress(uninstalledCount + 1, packageIds.size, true)
                val success = awaitUninstall(packageId) {
                    uninstallAppUseCase(packageId)
                }
                if (success) {
                    uninstalledCount++
                    if (packageId in archiveEligiblePackageIds) {
                        try {
                            archiveAndUninstallUseCase(
                                packageIds = listOf(packageId),
                                appInfoMap = mapOf(packageId to sourceInfo),
                                performUninstall = false
                            )
                            successfullyArchivedIds += packageId
                        } catch (e: com.tool.decluttr.domain.usecase.ArchiveLimitExceededException) {
                            // Hit limit, trigger paywall event
                            _paymentRequiredEvent.emit(Unit)
                            // Stop further processing for this batch
                            break
                        }
                    }
                }
            }

            _uninstallProgress.value = UninstallProgress(0, 0, false)
            loadDiscoveryData()
            
            if (successfullyArchivedIds.isNotEmpty()) {
                val archivedIds = successfullyArchivedIds.toSet()
                val savedBytes = appsToUninstall
                    .filter { it.packageId in archivedIds }
                    .sumOf { it.apkSizeBytes }
                val celebration = CelebrationData(archivedIds.size, savedBytes)
                // Wait for Room to have all newly archived apps before showing review
                val reviewApps = archivedApps.first { apps ->
                    archivedIds.all { id -> apps.any { it.packageId == id } }
                }.filter { it.packageId in archivedIds }
                _reviewEvent.emit(ReviewData(reviewApps, celebration))
            }
        }
    }

    fun saveReviewNotes(notesMap: Map<String, String>, celebration: CelebrationData) {
        viewModelScope.launch {
            for ((packageId, note) in notesMap) {
                val app = archivedApps.value.find { it.packageId == packageId }
                if (app != null) {
                    appRepository.updateApp(app.copy(notes = note))
                }
            }
            _celebrationEvent.emit(celebration)
        }
    }

    fun uninstallSelectedOnly(packageIds: Set<String>) {
        if (packageIds.isEmpty()) return
        
        val appsToUninstall = allInstalledApps.value.filter { it.packageId in packageIds }
        val savedBytes = appsToUninstall.sumOf { it.apkSizeBytes }
        
        viewModelScope.launch {
            _uninstallProgress.value = UninstallProgress(0, packageIds.size, true)
            var uninstalledCount = 0

            for (packageId in packageIds) {
                _uninstallProgress.value = UninstallProgress(uninstalledCount + 1, packageIds.size, true)
                val success = awaitUninstall(packageId) {
                    uninstallAppUseCase(packageId)
                }
                if (success) {
                    uninstalledCount++
                }
            }
            
            _uninstallProgress.value = UninstallProgress(0, 0, false)
            loadDiscoveryData()
            
            if (uninstalledCount > 0) {
                _celebrationEvent.emit(CelebrationData(uninstalledCount, appsToUninstall.take(uninstalledCount).sumOf { it.apkSizeBytes }))
            }
        }
    }

    private suspend fun awaitUninstall(packageId: String, triggerUninstall: () -> Unit): Boolean {
        return kotlinx.coroutines.withTimeoutOrNull(60_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val removedPkg = intent.data?.schemeSpecificPart
                        if (removedPkg == packageId) {
                            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                            if (!isReplacing) {
                                try {
                                    ctx.unregisterReceiver(this)
                                } catch (e: Exception) {
                                    FirebaseCrashlytics.getInstance().recordException(e)
                                }
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                }
                            }
                        }
                    }
                }
                val filter = android.content.IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
                    addDataScheme("package")
                }
                
                // MUST use RECEIVER_EXPORTED to receive system broadcasts like ACTION_PACKAGE_REMOVED
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }

                continuation.invokeOnCancellation {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }

                triggerUninstall()
            }
        } ?: false // Timeout = user cancelled or took too long
    }
}
