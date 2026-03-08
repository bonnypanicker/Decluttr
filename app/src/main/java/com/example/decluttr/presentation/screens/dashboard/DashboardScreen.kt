package com.example.decluttr.presentation.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.example.decluttr.domain.model.ArchivedApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAppDetails: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val archivedApps by viewModel.archivedApps.collectAsState()
    val unusedApps by viewModel.unusedApps.collectAsState()
    val largeApps by viewModel.largeApps.collectAsState()
    val allInstalledApps by viewModel.allInstalledApps.collectAsState()
    val isLoadingDiscovery by viewModel.isLoadingDiscovery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Declutter", "My Archive")
    val tabIcons = listOf(Icons.Default.Search, Icons.Default.List)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Decluttr", 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                actions = {
                    if (archivedApps.isNotEmpty()) {
                        val estimatedMBs = archivedApps.size * 45
                        androidx.compose.material3.Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(100),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "~${estimatedMBs}MB saved",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = { Icon(tabIcons[index], contentDescription = title) },
                        label = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }
        },
        floatingActionButton = {
            val hasUsagePermission = viewModel.hasUsagePermission.collectAsState().value
            if (selectedTabIndex == 1 && hasUsagePermission && unusedApps.isNotEmpty()) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Declutter") },
                    text = { Text("Declutter ${unusedApps.size} Apps", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    // Declutter Dashboard
                    DiscoveryScreen(
                        unusedApps = unusedApps,
                        largeApps = largeApps,
                        allApps = allInstalledApps,
                        isLoading = isLoadingDiscovery,
                        onRefresh = { viewModel.loadDiscoveryData() },
                        onBatchUninstall = { packageIds ->
                            viewModel.archiveAndUninstallSelected(packageIds)
                        },
                        onBatchUninstallOnly = { packageIds ->
                            viewModel.uninstallSelectedOnly(packageIds)
                        },
                        hasUsagePermission = viewModel.hasUsagePermission.collectAsState().value,
                        onRequestPermission = { viewModel.checkUsagePermission() }
                    )
                }
                1 -> {
                    // Archive List
                    ArchivedAppsList(
                        apps = archivedApps,
                        onAppClick = onNavigateToAppDetails,
                        onDeleteClick = { viewModel.deleteArchivedApp(it) },
                        onNavigateToDiscover = { selectedTabIndex = 0 }
                    )
                }
            }
        }
    }
}
