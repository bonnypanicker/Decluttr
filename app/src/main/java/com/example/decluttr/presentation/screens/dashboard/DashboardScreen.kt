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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    val allInstalledApps by viewModel.allInstalledApps.collectAsState()
    val isLoadingDiscovery by viewModel.isLoadingDiscovery.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("My Archive", "Discover")

    Scaffold(
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
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    // Archive List
                    Column(modifier = Modifier.fillMaxSize()) {
                        StorageSavedEstimator(apps = archivedApps)
                        ArchivedAppsList(
                            apps = archivedApps,
                            onAppClick = onNavigateToAppDetails,
                            onDeleteClick = { viewModel.deleteArchivedApp(it) }
                        )
                    }
                }
                1 -> {
                    // Discovery List
                    DiscoveryScreen(
                        unusedApps = unusedApps,
                        allApps = allInstalledApps,
                        isLoading = isLoadingDiscovery,
                        onRefresh = { viewModel.loadDiscoveryData() },
                        onBatchUninstall = { packageIds ->
                            viewModel.archiveAndUninstallSelected(packageIds)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StorageSavedEstimator(apps: List<ArchivedApp>) {
    if (apps.isEmpty()) return
    
    // In a real implementation this would sum exact apk sizes saved from db
    // Since we don't save exact APK size to DB in this implementation
    // We will estimate ~45MB average per app
    val estimatedMBs = apps.size * 45
    
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Estimated Storage Saved", 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "~$estimatedMBs MB", 
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
